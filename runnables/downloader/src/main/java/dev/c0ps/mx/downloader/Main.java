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
import dev.c0ps.mx.downloader.data.IngestionData;
import dev.c0ps.mx.downloader.data.IngestionStatus;
import dev.c0ps.mx.downloader.utils.ArtifactFinder;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.downloader.utils.MavenDownloader;
import dev.c0ps.mx.infra.exceptions.UnrecoverableError;
import dev.c0ps.mx.infra.kafka.DefaultTopics;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.kafka.SimpleErrorMessage;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.TimedExecutor;
import jakarta.inject.Inject;

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

    @Inject
    public Main(TimedExecutor exec, Kafka kafka, ResultsDatabase db, LaneManagement lm, ArtifactFinder artifactFinder, MavenRepositoryUtils utils, MavenDownloader downloader) {
        this.exec = exec;
        this.kafka = kafka;
        this.db = db;
        this.lm = lm;
        this.artifactFinder = artifactFinder;
        this.utils = utils;
        this.downloader = downloader;
    }

    @Override
    public void run() {
        kafka.subscribe(DefaultTopics.REQUESTED, Artifact.class, this::download);
        while (true) {
            LOG.debug("Polling ...");
            kafka.poll();
        }
    }

    private void download(Artifact a, Lane lane) {
        exec.run(a, () -> {
            LOG.info("######################################## Consuming next {} record {} ...", lane, a);
            var state = db.get(a);
            if (state == null || state.status == IngestionStatus.REQUESTED) {
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
            // path(a) == path(b), fixes only affect the file contents
            var state = db.markFound(b);
            process(state, lane);
        }
    }

    private void process(IngestionData state, Lane lane) {
        var a = state.artifact;

        if (!lm.shouldProcess(a, lane)) {
            LOG.info("Lane management stopped further processing of artifact {} on lane {}", a, lane);
            return;
        }
        lm.reportProgress(a, lane);

        try {
            switch (state.status) {
            case RESOLVED:
            case DONE:
                continueResolvedOrDone(lane, a);
                break;
            case NOT_FOUND:
                // skip
                LOG.info("Artifact {} could not be found before. Not attempting again.", a);
                break;
            case CRASHED:
                continueCrashed(state, lane);
                break;
            case FOUND:
                continueFound(a, lane);
                break;
            default:
                break;
            }
        } catch (Exception e) {
            var s = db.markCrashed(a, e);
            if (s.numCrashes < MAX_TRIES_PER_COORD) {
                // crash (and restart) ...
                throw new UnrecoverableError("Caught an exception, throwing an exception to abort processing and retry ...", e);
            }
            LOG.info("Third crash, giving up on {} .. publishing error and moving on.", a);
            // ... or prevent endless crash loop
            publishError(s);
        }
    }

    private void continueResolvedOrDone(Lane lane, Artifact a) {
        var pom = utils.getLocalPomFile(a);
        if (pom.exists()) {
            // no processing required, just move on
            LOG.info("Reusing cached result for artifact {} ...", a);
            publish(a, lane);
        } else {
            LOG.error("Weird... {} was RESOLVED or DONE, but local pom is missing. Redownloading ...", a);
            db.markRequested(a);
            findAndProcess(a, lane);
        }
    }

    private void continueCrashed(IngestionData state, Lane lane) {
        if (state.numCrashes >= MAX_TRIES_PER_COORD) {
            // prevent endless crash loop...
            LOG.info("Reusing cached result for artifact {} ...", state.artifact);
            publishError(state);
            return;
        }
        // ... or repeat normal processing
        continueFound(state.artifact, lane);
    }

    private void continueFound(Artifact a, Lane lane) {
        LOG.info("Artifact {} was found, attempting to resolve it ...", a);
        try {
            downloader.download(a);
            db.markResolved(a);
            publish(a, lane);
        } catch (ResolutionException e) {
            // this error is common, take a shortcut and prevent re-attempt
            for (var i = 0; i < MAX_TRIES_PER_COORD - 1; i++) {
                db.markCrashed(a, e);
            }
            var s = db.markCrashed(a, e);
            publishError(s);
        }
    }

    private void publish(Artifact a, Lane lane) {
        LOG.info("Publishing result for artifact {} ... ({})", a, lane);
        var gav = MavenRepositoryUtils.toGAV(a);
        // use GAV as key to eliminate parallel/duplicate processing in multiple workers
        kafka.publish(gav, a, DefaultTopics.DOWNLOADED, lane);
    }

    private void publishError(IngestionData s) {
        switch (s.status) {
        case CRASHED:
            LOG.error("Artifact {} has already crashed {} times. Not attempting a download again.", s.artifact, s.numCrashes);
            break;
        case NOT_FOUND:
            LOG.error("Artifact {} could not be found. Not attempting again.", s.artifact);
            break;
        default:
            break;
        }
        var msg = new SimpleErrorMessage<Artifact>(s.artifact, s.stacktrace);
        kafka.publish(msg, DefaultTopics.DOWNLOADED, Lane.ERROR);
    }
}