/*
 * Copyright 2022 Delft University of Technology
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
package dev.c0ps.mx.downloader.utils;

import static dev.c0ps.commons.Asserts.assertNotNull;
import static dev.c0ps.mx.downloader.data.Status.CRASHED;
import static dev.c0ps.mx.downloader.data.Status.DEPS_MISSING;
import static dev.c0ps.mx.downloader.data.Status.DONE;
import static dev.c0ps.mx.downloader.data.Status.FOUND;
import static dev.c0ps.mx.downloader.data.Status.NOT_FOUND;
import static dev.c0ps.mx.downloader.data.Status.REQUESTED;
import static dev.c0ps.mx.downloader.data.Status.RESOLVED;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

import dev.c0ps.commons.Asserts;
import dev.c0ps.io.IoUtils;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Result;
import dev.c0ps.mx.downloader.data.Status;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.Version;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class FileBasedResultsDatabase implements ResultsDatabase {

    private static final Set<String> VALID_TRANSITIONS = new HashSet<>();

    {
        // crawler -> downloader
        VALID_TRANSITIONS.add(transition(null, FOUND));
        // depgraph -> downloader
        VALID_TRANSITIONS.add(transition(null, REQUESTED));

        VALID_TRANSITIONS.add(transition(REQUESTED, FOUND));
        VALID_TRANSITIONS.add(transition(REQUESTED, NOT_FOUND));

        VALID_TRANSITIONS.add(transition(FOUND, FOUND));
        VALID_TRANSITIONS.add(transition(FOUND, CRASHED));
        VALID_TRANSITIONS.add(transition(FOUND, RESOLVED));

        VALID_TRANSITIONS.add(transition(RESOLVED, CRASHED));
        VALID_TRANSITIONS.add(transition(RESOLVED, DEPS_MISSING));

        VALID_TRANSITIONS.add(transition(DEPS_MISSING, CRASHED));
        VALID_TRANSITIONS.add(transition(DEPS_MISSING, DONE));

        VALID_TRANSITIONS.add(transition(CRASHED, CRASHED));
        VALID_TRANSITIONS.add(transition(CRASHED, RESOLVED));
        VALID_TRANSITIONS.add(transition(CRASHED, DEPS_MISSING));
    }

    private final Version toolVersion;
    private final IoUtils io;
    private final MavenRepositoryUtils mru;
    private final File baseDir;

    @Inject
    public FileBasedResultsDatabase(Version toolVersion, IoUtils io, MavenRepositoryUtils mru, //
            @Named("IngestionDatabase.baseDir") File baseDir) {
        this.toolVersion = toolVersion;
        this.io = io;
        this.mru = mru;
        this.baseDir = baseDir;
    }

    @Override
    public Result get(Artifact a) {
        var f = f(a);
        if (!f.exists()) {
            return null;
        }
        return io.readFromFile(f, Result.class);
    }

    @Override
    public Result markRequested(Artifact a) {
        return update(a, true, REQUESTED);
    }

    @Override
    public Result markFound(Artifact a) {
        return update(a, true, FOUND);
    }

    @Override
    public Result markNotFound(Artifact a) {
        return update(a, false, NOT_FOUND);
    }

    @Override
    public Result markResolved(Artifact a) {
        return update(a, false, RESOLVED);
    }

    @Override
    public Result recordCrash(Artifact a, Throwable t) {
        assertNotNull(t);
        var cause = unwrapOriginalCause(t);
        return update(a, false, r -> {
            r.numCrashes += 1;
            if (t != null) {
                r.stacktrace = ExceptionUtils.getStackTrace(cause);
            }
        });
    }

    @Override
    public Result markCrashed(Artifact a) {
        var old = get(a);
        if (old.stacktrace == null) {
            throw new IllegalStateException("Stacktrace is null in result");
        }
        if (old.numCrashes < 1) {
            throw new IllegalStateException("No crash has been recorded in result");
        }
        return update(a, false, r -> {
            r.status = Status.CRASHED;
            r.numCrashes = old.numCrashes;
            r.stacktrace = old.stacktrace;
        });
    }

    private static Throwable unwrapOriginalCause(Throwable t) {
        boolean isRuntimeExceptionAndNoSubtype = RuntimeException.class.equals(t.getClass());
        boolean isWrapped = isRuntimeExceptionAndNoSubtype && t.getCause() != null;
        return isWrapped ? unwrapOriginalCause(t.getCause()) : t;
    }

    @Override
    public Result markDepsMissing(Pom p) {
        var a = MavenRepositoryUtils.toArtifact(p);
        return update(a, false, d -> {
            d.status = DEPS_MISSING;
            d.pom = p;
            // successful transition resets crash info
            d.numCrashes = 0;
            d.stacktrace = null;
        });
    }

    @Override
    public Result markDone(Artifact a) {
        return update(a, false, DONE);
    }

    @Override
    public void reset(Artifact a) {
        var f = f(a);
        if (f.exists()) {
            if (!f.delete()) {
                var msg = String.format("Cannot delete completion marker of %s (%s)", a, f);
                throw new IllegalStateException(msg);
            }
        }
    }

    private Result update(Artifact a, boolean shouldUpdateArtifact, Status s) {
        return update(a, shouldUpdateArtifact, r -> {
            r.status = s;
            // successful transition resets crash info
            r.numCrashes = 0;
            r.stacktrace = null;
        });
    }

    private Result update(Artifact a, boolean shouldUpdateArtifact, Consumer<Result> c) {
        Asserts.assertNotNull(a);
        var f = f(a);
        var r = readResultOrCreateNew(f);

        var stateBefore = r.status;
        var numCrashesBefore = r.numCrashes;

        c.accept(r);

        var stateAfter = r.status;
        var numCrashesAfter = r.numCrashes;
        var wasCrash = (numCrashesAfter - numCrashesBefore > 0) && stateBefore == stateAfter;

        var hasCrashedWithoutStacktrace = stateAfter == CRASHED && r.stacktrace == null;
        if (hasCrashedWithoutStacktrace) {
            throw new IllegalStateException("Result has CRASHED without stacktrace");
        }

        var triesToMarkKnownPackageAsRequested = stateBefore != null && stateAfter == REQUESTED && !wasCrash;
        if (triesToMarkKnownPackageAsRequested) {
            var msg = String.format("Cannot mark package %s as REQUESTED: was known before", a);
            throw new IllegalStateException(msg);
        }

        if (shouldUpdateArtifact) {
            // the artifact might have been altered, e.g., fixed packaging
            r.artifact = a;
        }

        var t = transition(stateBefore, stateAfter);
        boolean isInvalidStateTransition = !VALID_TRANSITIONS.contains(t) && !wasCrash;
        if (isInvalidStateTransition) {
            var msg = String.format("Invalid state transition for package %s: %s", a, t);
            throw new IllegalStateException(msg);
        }

        // remember smallest version that was involved with creating this result
        setVersionIfRequired(r);

        io.writeToFile(r, f);

        return r;
    }

    private Result readResultOrCreateNew(File f) {
        var r = f.exists() //
                ? io.readFromFile(f, Result.class)
                : new Result();
        return r;
    }

    private void setVersionIfRequired(Result r) {
        r.createdWith = r.createdWith == null //
                ? toolVersion.get()
                : getSmaller(r.createdWith, toolVersion.get());
    }

    private File f(Artifact a) {
        return mru.getMavenFilePathNonStatic(baseDir, a, "result");
    }

    private static String getSmaller(String v1, String v2) {
        var cv1 = new ComparableVersion(v1);
        var cv2 = new ComparableVersion(v2);
        return cv1.compareTo(cv2) < 1 //
                ? v1
                : v2;
    }

    private static String transition(Status a, Status b) {
        return new StringBuilder().append(a).append("»").append(b).toString();
    }
}