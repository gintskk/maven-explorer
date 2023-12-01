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
package dev.c0ps.mx.downloader;

import java.lang.module.ResolutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.franz.Kafka;
import dev.c0ps.franz.Lane;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Result;
import dev.c0ps.mx.downloader.data.Status;
import dev.c0ps.mx.downloader.utils.ArtifactFinder;
import dev.c0ps.mx.downloader.utils.CompletionTracker;
import dev.c0ps.mx.downloader.utils.MavenDownloader;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.infra.exceptions.UnrecoverableError;
import dev.c0ps.mx.infra.kafka.DefaultTopics;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.kafka.SimpleErrorMessage;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.TimedExecutor;
import com.google.inject.Inject;

public class Main implements Runnable {

    private static final int MAX_TRIES_PER_COORD = 3;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private final TimedExecutor exec;
    private final Kafka kafka;
    private final ResultsDatabase db;
    private final LaneManagement lm;
    private final ArtifactFinder artifactFinder;
    private final MavenRepositoryUtils utils;
    private final MavenDownloader downloader;
    private final CompletionTracker tracker;

    @Inject
    public Main(TimedExecutor exec, Kafka kafka, ResultsDatabase db, LaneManagement lm, ArtifactFinder artifactFinder, MavenRepositoryUtils utils, MavenDownloader downloader,
            CompletionTracker tracker) {
        this.exec = exec;
        this.kafka = kafka;
        this.db = db;
        this.lm = lm;
        this.artifactFinder = artifactFinder;
        this.utils = utils;
        this.downloader = downloader;
        this.tracker = tracker;
    }

    @Override
    public void run() {

        kafka.subscribe(DefaultTopics.REQUESTED, Artifact.class, (a, lane) -> {
            LOG.info("######################################## Consuming next {} record {} ...", lane, a);

            if (!lm.shouldProcess(a, lane)) {
                LOG.info("Lane management stopped further processing of artifact {} on lane {}", a, lane);
                return;
            }
            lm.reportProgress(a, lane);

            if (tracker.shouldSkip(a)) {
                LOG.info("Skipping {} (has already been processed)", a);
                return;
            }
            // ok to "start" tracking (=in-memory), but "done" is set after pom-analyzer
            tracker.markStarted(a);

            download(a, lane);
        });

        while (true) {
            LOG.debug("Polling ...");
            kafka.poll();
        }
    }

    private void download(Artifact a, Lane lane) {
        exec.run(a, () -> {
            var state = db.get(a);
            if (state == null || state.status == Status.REQUESTED) {
                findAndProcess(a, lane);
            } else {
                process(state, lane);
            }
        });
    }

    private void findAndProcess(Artifact a, Lane lane) {
        var b = artifactFinder.findArtifact(a);

        if (b == null) {
            var state = db.markNotFound(a);
            publishError(state);
        } else {
            // gav(a) == gav(b), fix only affects file content
            var state = db.markFound(b);
            process(state, lane);
        }
    }

    private void process(Result state, Lane lane) {
        var a = state.artifact;

        try {
            switch (state.status) {
            case NOT_FOUND:
            case CRASHED:
                // skip
                publishError(state);
                break;
            case FOUND:
                continueFound(a, lane);
                break;
            case RESOLVED:
            case DEPS_MISSING:
            case DONE:
                reusePreviousResult(lane, a, state.status);
                break;
            default:
                LOG.error("Skipping {} with unexpected status {} ...", state.artifact, state.status);
                break;
            }
        } catch (Exception e) {
            var s = db.recordCrash(a, e);
            if (s.numCrashes < MAX_TRIES_PER_COORD) {
                // crash (and restart) ...
                throw new UnrecoverableError("Caught an exception, throwing an exception to abort processing and retry ...", e);
            }
            LOG.info("Artifact {} has now crashed {} times, giving up.", s.numCrashes, a);
            // ... or prevent endless crash loop
            var s2 = db.markCrashed(a);
            publishError(s2);
        }
    }

    private void continueFound(Artifact a, Lane lane) {
        LOG.info("Artifact {} was found, attempting to download/resolve it ...", a);
        try {
            downloader.download(a);
            db.markResolved(a);
            publish(a, lane);
        } catch (ResolutionException e) {
            // this error is common, take a shortcut and prevent re-attempt
            db.recordCrash(a, e);
            var s = db.markCrashed(a);
            publishError(s);
        }
    }

    private void reusePreviousResult(Lane lane, Artifact a, Status status) {
        var pom = utils.getLocalPomFile(a);
        if (pom.exists()) {
            // no processing required, just move on
            LOG.info("Reusing cached result for artifact {} ...", a);
            publish(a, lane);
        } else {
            LOG.error("Unexpected: Status of {} is {}, but local pom is missing. Redownloading ...", a, status);
            db.reset(a);
            db.markRequested(a);
            findAndProcess(a, lane);
        }
    }

    private void publish(Artifact a, Lane lane) {
        LOG.info("Publishing result for artifact {} ... ({})", a, lane);
        var gav = MavenRepositoryUtils.toGAV(a);
        // use GAV as key to eliminate parallel/duplicate processing in multiple workers
        kafka.publish(gav, a, DefaultTopics.DOWNLOADED, lane);
    }

    private void publishError(Result s) {
        switch (s.status) {
        case CRASHED:
            LOG.error("Artifact {} has crashed, not attempting again.", s.artifact, s.numCrashes);
            break;
        case NOT_FOUND:
            LOG.error("Artifact {} could not be found. Not attempting again.", s.artifact);
            break;
        default:
            var msg = String.format("Unexpected status {} for {} to publish an error", s.status, s.artifact);
            throw new IllegalStateException(msg);
        }
        var msg = new SimpleErrorMessage<Artifact>(s.artifact, s.stacktrace);
        kafka.publish(msg, DefaultTopics.DOWNLOADED, Lane.ERROR);
    }
}