/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.c0ps.mx.pomanalyzer;

import static dev.c0ps.commons.Asserts.assertTrue;
import static dev.c0ps.mx.infra.utils.MavenRepositoryUtils.toGAV;
import static java.lang.String.format;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.InvalidConfigurationFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.commons.AssertsException;
import dev.c0ps.franz.Kafka;
import dev.c0ps.franz.Lane;
import dev.c0ps.maven.PomExtractor;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Result;
import dev.c0ps.mx.downloader.data.Status;
import dev.c0ps.mx.downloader.utils.CompletionTracker;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.infra.exceptions.UnrecoverableError;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.kafka.SimpleErrorMessage;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.TimedExecutor;
import dev.c0ps.mx.pomanalyzer.utils.EffectiveModelBuilder;
import dev.c0ps.mx.pomanalyzer.utils.ShrinkwrapResolver;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Main implements Runnable {

    private static final int MAX_TRIES_PER_COORD = 3;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private final EffectiveModelBuilder modelBuilder;
    private final PomExtractor extractor;
    private final ShrinkwrapResolver resolver;
    private final Kafka kafka;
    private final TimedExecutor timedExec;
    private final ResultsDatabase db;
    private final LaneManagement lm;
    private final MavenRepositoryUtils m2utils;
    private final CompletionTracker tracker;

    private final String kafkaTopicIn;
    private final String kafkaTopicOut;
    private final String kafkaTopicRequested;

    private final Date startOfMainAt = new Date();
    private Date startOfOrigAt;
    private LinkedList<CurrentArtifact> queue = new LinkedList<>();
    private Set<Artifact> requeued = new HashSet<>();

    @Inject
    public Main(EffectiveModelBuilder modelBuilder, PomExtractor extractor, ShrinkwrapResolver resolver, Kafka kafka, TimedExecutor timedExec, ResultsDatabase db, LaneManagement lm,
            MavenRepositoryUtils m2utils, CompletionTracker tracker, //
            @Named("kafka.topic.downloaded") String kafkaTopicIn, //
            @Named("kafka.topic.analyzed") String kafkaTopicOut, //
            @Named("kafka.topic.requested") String kafkaTopicRequested) {
        this.modelBuilder = modelBuilder;
        this.extractor = extractor;
        this.resolver = resolver;
        this.kafka = kafka;
        this.timedExec = timedExec;
        this.db = db;
        this.lm = lm;
        this.m2utils = m2utils;
        this.tracker = tracker;
        this.kafkaTopicIn = kafkaTopicIn;
        this.kafkaTopicOut = kafkaTopicOut;
        this.kafkaTopicRequested = kafkaTopicRequested;
    }

    @Override
    public void run() {
        try {
            LOG.info("Subscribing to '{}', will publish in '{}' ...", kafkaTopicIn, kafkaTopicOut);
            kafka.subscribe(kafkaTopicIn, Artifact.class, (orig, lane) -> {
                LOG.info("######################################## Consuming next {} record {} ...", lane, orig);
                if (!lm.shouldProcess(orig, lane)) {
                    LOG.info("Lane management stopped further processing of artifact {} on lane {}", orig, lane);
                    return;
                }
                lm.reportProgress(orig, lane);
                processAll(orig, lane);
            });
            while (true) {
                LOG.debug("Polling ...");
                kafka.poll();
            }
        } finally {
            kafka.stop();
        }
    }

    public void processAll(Artifact orig, Lane lane) {

        startOfOrigAt = new Date();

        tracker.clearMemory();
        if (!queue.isEmpty()) {
            LOG.error("Tried to start {} with non-empty queue: {}", orig, queue);
            queue.clear();
        }
        queue.add(new CurrentArtifact(orig, null, orig, lane));

        while (!queue.isEmpty()) {

            // make sure the outer poll does not timeout
            kafka.sendHeartbeat();

            var cur = queue.poll();

            if (shouldSkipOrStart(cur.a)) {
                continue;
            }

            timedExec.run(cur.id(), () -> {
                try {
                    processOne(cur);
                } catch (InvalidConfigurationFileException e) {
                    LOG.error("Cannot process {}: pom.xml cannot be parsed", cur.a);
                    db.recordCrash(cur.a, e);
                    var s2 = db.markCrashed(cur.a);
                    publishError(cur, s2, "invalid pom.xml");
                } catch (Exception e) {
                    var s = db.recordCrash(cur.a, e);
                    if (s.numCrashes < MAX_TRIES_PER_COORD) {
                        // crash (and restart) ...
                        throw new UnrecoverableError(e);
                    }
                    // ... or prevent endless crash loop
                    var s2 = db.markCrashed(cur.a);
                    publishError(cur, s2, "caught exception");
                }
            });
        }
    }

    private boolean shouldSkipOrStart(Artifact a) {
        if (tracker.shouldSkip(a)) {
            LOG.info("Skipping {} (has already been processed)", a);
            return true;
        }
        tracker.markStarted(a);
        return false;
    }

    private void processOne(CurrentArtifact cur) {
        logRuntime(cur);

        var s = db.get(cur.a);

        // dependencies do not get marked automatically
        if (s == null) {
            s = db.markRequested(cur.a);
            // directly resolving transitive deps can also discover new dependencies
            publishRequest(cur);
            return;
        }
        // "fixed" information might exist for previous results
        if (shouldSkipAfterFixingInformation(cur, s)) {
            return;
        }

        switch (s.status) {
        case NOT_FOUND:
            LOG.info("Artifact {} not found, skipping.", cur.a);
            break;
        case REQUESTED:
        case FOUND:
            // skip
            LOG.info("Skipping {} dependency that has already been propagated back to downloading/resolution ...", s.status);
            break;
        case CRASHED:
            publishError(cur, s, "cached");
            break;
        case RESOLVED:
            continueResolved(cur);
            break;
        case DEPS_MISSING:
            continueDepsMissing(cur);
            break;
        case DONE:
            // handle like DEP_MISSING to ensure processing of all deps
            continueDepsMissing(cur);
            break;
        default:
            var msg = String.format("Unhandled status %s for %s", s.status, s.artifact);
            throw new IllegalStateException(msg);
        }
    }

    private boolean shouldSkipAfterFixingInformation(CurrentArtifact cur, Result r) {
        var resultContainsFix = !cur.a.equals(r.artifact);
        if (resultContainsFix) {
            LOG.info("Updating current artifact with cached data: {} -> {} ...", cur.a, r.artifact);
            tracker.markAborted(cur.a);
            if (requeued.remove(cur.a)) {
                requeued.add(r.artifact);
            }
            cur.a = r.artifact;
            if (shouldSkipOrStart(cur.a)) {
                return true;
            }
        }
        return false;
    }

    private void logRuntime(CurrentArtifact cur) {
        var now = new Date().toInstant();
        var mainDuration = Duration.between(startOfMainAt.toInstant(), now);
        var origDuration = Duration.between(startOfOrigAt.toInstant(), now);

        LOG.info("########## (Run started at: {} / {})", startOfMainAt, mainDuration);

        var msg = cur.isOriginalArtifact() //
                ? ">> Processing original {} ..."
                : "Processing dependency {} ... (original started at {} / {})";
        LOG.info(msg, cur.a, startOfOrigAt, origDuration);
    }

    private void continueResolved(CurrentArtifact cur) {

        logContinueState(cur, Status.RESOLVED);

        var pomFile = m2utils.getLocalPomFile(cur.a);
        if (!pomFile.exists()) {
            LOG.error("The pom file of RESOLVED artifact {} does not exist. Requesting again.", cur.a);
            publishRequest(cur);
            return;
        }

        var m = modelBuilder.buildEffectiveModel(pomFile);
        var pom = extractor.process(m);

        // update information with validated information from "fixed" artifact
        pom.artifactRepository = cur.a.repository;
        pom.packagingType = cur.a.packaging;
        pom.releaseDate = cur.a.releaseDate;

        LOG.info("Successful pom extraction for {} ...", cur.a);
        db.markDepsMissing(pom.pom());

        continueDepsMissing(cur);
    }

    private void continueDepsMissing(CurrentArtifact cur) {
        logContinueState(cur, Status.DEPS_MISSING);

        if (requeued.contains(cur.a)) {
            var shouldClose = !cur.isOriginalArtifact() || queue.isEmpty();
            if (shouldClose) {
                requeued.remove(cur.a);
                LOG.info("Finishing re-queued artifact {} ...", cur.a);
                publishResult(cur);
                tracker.markCompleted(cur.a);
            } else {
                LOG.info("Found original again ({}), but queue is not empty. Re-queueing again ...", cur.a);
                requeue(cur);
            }
        } else {
            LOG.info("Resolving dependencies of {} ...", cur.a);
            var deps = resolver.resolveDependencies(cur.a);
            LOG.info("Queueing the {} dependencies of {} ...", deps.size(), cur.a);
            var numSkips = new int[1];
            deps.forEach(dep -> {
                if (tracker.shouldSkip(dep)) {
                    numSkips[0]++;
                } else {
                    queue.add(cur.child(dep));
                }
            });
            if (numSkips[0] > 0) {
                LOG.info("Skipped {} dependencies (have already been processed)", numSkips[0]);
            }
            requeue(cur);
        }
    }

    private void requeue(CurrentArtifact cur) {
        if (!queue.contains(cur)) {
            queue.add(cur);
        }
        requeued.add(cur.a);
        tracker.markAborted(cur.a);
        LOG.info("Requeued {}.", cur.a);
    }

    private void logContinueState(CurrentArtifact cur, Status state) {
        var msg = cur.isOriginalArtifact() //
                ? "Continue {}: {} ... (original)"
                : "Continue {}: {} ... (dep of: {}, orig: {})";
        LOG.info(msg, state, cur.a, cur.parent, cur.origin);
    }

    private void publishResult(CurrentArtifact cur) {
        var msg = cur.isOriginalArtifact() //
                ? "Publishing result for artifact {} on {} ... (original)"
                : "Publishing result for artifact {} on {} ... (dep of: {}, orig: {})";
        LOG.info(msg, cur.a, cur.lane, cur.parent, cur.origin);
        db.markDone(cur.a);
        var gav = MavenRepositoryUtils.toGAV(cur.a);
        // use GAV as key to eliminate parallel/duplicate processing in multiple workers
        kafka.publish(gav, cur.a, kafkaTopicOut, cur.lane);
    }

    private void publishRequest(CurrentArtifact cur) {
        LOG.info("Dependency {} will be ingested separately ...", cur.a);
        var gav = MavenRepositoryUtils.toGAV(cur.a);
        // use GAV as key to eliminate parallel/duplicate processing in multiple workers
        kafka.publish(gav, cur.a, kafkaTopicRequested, Lane.PRIORITY);
    }

    private void publishError(CurrentArtifact c, Result s, String reason) {
        assertShallowEquals(c.a, s.artifact);
        assertTrue(s.status == Status.CRASHED);
        var msg = c.isOriginalArtifact() //
                ? "Pom extraction of {} has crashed for {} times ({}), not attempting again. (original)"
                : "Pom extraction of {} has crashed for {} times ({}), not attempting again. (dep of: {}, orig: {})";
        LOG.error(msg, s.artifact, s.numCrashes, reason, c.parent, c.origin);
        kafka.publish(new SimpleErrorMessage<Artifact>(s.artifact, s.stacktrace), kafkaTopicOut, Lane.ERROR);
    }

    private void assertShallowEquals(Artifact a, Artifact b) {
        if (!toGAV(a).equals(toGAV(b))) {
            var msg = String.format("Publishing error for non-matching artifact/result: %s and %s", a, b);
            LOG.warn(msg);
            throw new AssertsException(msg);
        }
    }

    private static class CurrentArtifact {

        public Artifact a;
        public final Artifact parent;
        public final Artifact origin;
        public final Lane lane;

        CurrentArtifact(Artifact a, Artifact parent, Artifact orig, Lane lane) {
            this.a = a;
            this.parent = parent;
            this.origin = orig;
            this.lane = lane;
        }

        public String id() {
            return isOriginalArtifact() //
                    ? String.format("%s (%s, original)", a, lane)
                    : String.format("%s (%s, dep of: %s, orig: %s)", a, lane, parent, origin);
        }

        public boolean isOriginalArtifact() {
            return a.equals(origin);
        }

        public CurrentArtifact child(Artifact child) {
            return new CurrentArtifact(child, a, origin, lane);
        }

        @Override
        public String toString() {
            var gav = toGAV(a);
            return isOriginalArtifact() ? format("«%s»", gav) : gav;
        }

        @Override
        public int hashCode() {
            return a.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CurrentArtifact) {
                var other = (CurrentArtifact) obj;
                return a.equals(other.a);
            }
            return false;
        }
    }
}