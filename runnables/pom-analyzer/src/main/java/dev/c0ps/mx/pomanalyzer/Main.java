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

import java.lang.module.ResolutionException;
import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.InvalidConfigurationFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.franz.Kafka;
import dev.c0ps.franz.Lane;
import dev.c0ps.maven.PomExtractor;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.IngestionData;
import dev.c0ps.mx.downloader.data.IngestionStatus;
import dev.c0ps.mx.downloader.utils.ArtifactFinder;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.infra.exceptions.UnrecoverableError;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.kafka.SimpleErrorMessage;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.TimedExecutor;
import dev.c0ps.mx.pomanalyzer.utils.CompletionTracker;
import dev.c0ps.mx.pomanalyzer.utils.EffectiveModelBuilder;
import dev.c0ps.mx.pomanalyzer.utils.ShrinkwrapResolver;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class Main implements Runnable {

    private static final int MAX_TRIES_PER_COORD = 3;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private final EffectiveModelBuilder modelBuilder;
    private final PomExtractor extractor;
    private final ShrinkwrapResolver resolver;
    private final Kafka kafka;
    private final ArtifactFinder finder;
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
    public Main(EffectiveModelBuilder modelBuilder, PomExtractor extractor, ShrinkwrapResolver resolver, Kafka kafka, ArtifactFinder finder, TimedExecutor timedExec, ResultsDatabase db,
            LaneManagement lm, MavenRepositoryUtils m2utils, CompletionTracker tracker, //
            @Named("kafka.topic.downloaded") String kafkaTopicIn, //
            @Named("kafka.topic.analyzed") String kafkaTopicOut, //
            @Named("kafka.topic.requested") String kafkaTopicRequested) {
        this.modelBuilder = modelBuilder;
        this.extractor = extractor;
        this.resolver = resolver;
        this.kafka = kafka;
        this.finder = finder;
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
        queue.add(new CurrentArtifact(orig, null, orig, lane));

        while (!queue.isEmpty()) {

            // make sure the outer poll does not timeout
            kafka.sendHeartbeat();

            var cur = queue.pop();
            if (tracker.shouldSkip(cur.a)) {
                LOG.info("Skipping {} (has already been processed)", cur.a);
                return;
            }
            tracker.markStarted(cur.a);

            timedExec.run(cur.id(), () -> {
                try {
                    processOne(cur);
                } catch (Exception e) {
                    var s = db.markCrashed(cur.a, e);
                    if (s.numCrashes < MAX_TRIES_PER_COORD) {
                        // crash (and restart) ...
                        throw new UnrecoverableError(e);
                    }
                    // ... or prevent endless crash loop
                    publishError(cur, s, "caught exception");
                }
            });
        }
    }

    private void processOne(CurrentArtifact cur) {
        logRuntime(cur);

        var s = db.get(cur.a);
        // dependencies do not get marked automatically
        if (s == null) {
            s = db.markResolved(cur.a);
        }

        // directly resolving transitive deps can discover new dependencies
        if (!hasAlreadyBeenIngested(cur, s)) {
            publishIngestion(cur);
            return;
        }

        try {
            switch (s.status) {
            case CRASHED:
                if (s.numCrashes >= MAX_TRIES_PER_COORD) {
                    // prevent endless crash loop...
                    publishError(cur, s, "cached");
                    return;
                }
                // ... or repeat normal processing
                continueResolved(cur);
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
                var msg = String.format("Status of %s was %s, but should be one of [CRASHED, RESOLVED, DEPS_MISSING, DONE]", s.artifact, s.status);
                throw new IllegalStateException(msg);
            }
        } catch (InvalidConfigurationFileException e) {
            LOG.error("Cannot resolve {}: pom.xml cannot be parsed", cur.a);
            for (var i = 0; i < MAX_TRIES_PER_COORD - 1; i++) {
                db.markCrashed(cur.a, e);
            }
            var s2 = db.markCrashed(cur.a, e);
            publishError(cur, s2, "invalid pom.xml");
        }
    }

    private void logRuntime(CurrentArtifact cur) {
        var now = new Date().toInstant();
        var mainDuration = Duration.between(startOfMainAt.toInstant(), now);
        var origDuration = Duration.between(startOfOrigAt.toInstant(), now);

        LOG.info("########## (Run started at: {} / {})", startOfMainAt, mainDuration);

        var msg = cur.isOriginalArtifact() //
                ? ">> Processing next original {} ..."
                : "Processing next artifact {} ... (origin started at {} / {})";
        LOG.info(msg, cur.a, startOfOrigAt, origDuration);

    }

    private boolean hasAlreadyBeenIngested(CurrentArtifact cur, IngestionData s) {
        if (cur.isOriginalArtifact()) {
            return true;
        }
        var pom = m2utils.getLocalPomFile(cur.a);
        return pom.exists();
    }

    private void continueResolved(CurrentArtifact cur) {

        logContinueState(cur, IngestionStatus.RESOLVED);

        cur = findOrFixArtifact(cur);

        var pomFile = m2utils.getLocalPomFile(cur.a);
        var m = modelBuilder.buildEffectiveModel(pomFile);
        var pom = extractor.process(m);

        var found = finder.findArtifact(pom.pom());
        if (found == null) {
            new ResolutionException("..?!");
        }

        pom.artifactRepository = found.repository;
        pom.packagingType = found.packaging;
        pom.releaseDate = found.releaseDate;

        LOG.info("Successful pom extraction for {} ...", cur.a);
        db.markDepsMissing(pom.pom());

        continueDepsMissing(cur);
    }

    private void requeue(CurrentArtifact cur) {
        queue.add(cur);
        requeued.add(cur.a);
        tracker.markAborted(cur.a);
    }

    private CurrentArtifact findOrFixArtifact(CurrentArtifact cur) {
        var b = finder.findArtifact(cur.a);
        if (cur.a.equals(b)) {
            return cur;
        }
        LOG.info("Fixed artifact {} --> {}", cur.a, b);
        return new CurrentArtifact(b, cur.parent, cur.origin, cur.lane);
    }

    private void continueDepsMissing(CurrentArtifact cur) {
        logContinueState(cur, IngestionStatus.DEPS_MISSING);

        if (requeued.contains(cur.a)) {
            requeued.remove(cur.a);
            LOG.info("Finishing re-queued artifact {} ...", cur.a);
            publishResult(cur);
            tracker.markCompleted(cur.a);
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

    private void logContinueState(CurrentArtifact cur, IngestionStatus state) {
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

    private void publishIngestion(CurrentArtifact cur) {
        LOG.info("Dependency {} will be ingested separately ...", cur.a);
        var gav = MavenRepositoryUtils.toGAV(cur.a);
        // use GAV as key to eliminate parallel/duplicate processing in multiple workers
        kafka.publish(gav, cur.a, kafkaTopicRequested, Lane.PRIORITY);
    }

    private void publishError(CurrentArtifact c, IngestionData s, String reason) {
        assertTrue(c.a.equals(s.artifact));
        assertTrue(s.status == IngestionStatus.CRASHED);
        var msg = c.isOriginalArtifact() //
                ? "Pom extraction of {} has crashed for {} times ({}), not attempting again. (original)"
                : "Pom extraction of {} has crashed for {} times ({}), not attempting again. (dep of: {}, orig: {})";
        LOG.error(msg, s.artifact, s.numCrashes, reason, c.parent, c.origin);
        kafka.publish(new SimpleErrorMessage<Artifact>(s.artifact, s.stacktrace), kafkaTopicOut, Lane.ERROR);
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
    }
}