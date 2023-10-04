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
package eu.f4sten.mavendownloader.utils;

import static dev.c0ps.commons.Asserts.assertNotNull;

import java.io.File;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

import dev.c0ps.io.IoUtils;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maveneasyindex.Artifact;
import eu.f4sten.infra.utils.MavenRepositoryUtils;
import eu.f4sten.infra.utils.Version;
import eu.f4sten.mavendownloader.data.IngestionData;
import eu.f4sten.mavendownloader.data.IngestionStatus;
import jakarta.inject.Inject;

public class FileBasedIngestionDatabase implements IngestionDatabase {

    private final Version toolVersion;
    private final IoUtils io;
    private final File baseDir;

    @Inject
    public FileBasedIngestionDatabase(Version toolVersion, IoUtils io) {
        this.toolVersion = toolVersion;
        this.io = io;
        this.baseDir = io.getBaseFolder();
    }

    @Override
    public IngestionData getCurrentResult(Artifact a) {
        var f = f(a);
        if (!f.exists()) {
            return null;
        }
        return io.readFromFile(f, IngestionData.class);
    }

    @Override
    public IngestionData markRequested(Artifact a) {
        return update(a, d -> {
            d.status = IngestionStatus.REQUESTED;
        });
    }

    @Override
    public IngestionData markFound(Artifact a) {
        return update(a, d -> {
            d.status = IngestionStatus.FOUND;
        });
    }

    @Override
    public IngestionData markNotFound(Artifact a) {
        return update(a, d -> {
            d.status = IngestionStatus.NOT_FOUND;
        });
    }

    @Override
    public IngestionData markResolved(Artifact a) {
        return update(a, d -> {
            d.status = IngestionStatus.RESOLVED;
        });
    }

    @Override
    public IngestionData markCrashed(Artifact a, Throwable t) {
        assertNotNull(t);
        var cause = unwrapOriginalCause(t);
        return update(a, d -> {
            d.status = IngestionStatus.CRASHED;
            d.numCrashes += 1;
            if (t != null) {
                d.stacktrace = ExceptionUtils.getStackTrace(cause);
            }
        });
    }

    private static Throwable unwrapOriginalCause(Throwable t) {
        boolean isRuntimeExceptionAndNoSubtype = RuntimeException.class.equals(t.getClass());
        boolean isWrapped = isRuntimeExceptionAndNoSubtype && t.getCause() != null;
        return isWrapped ? unwrapOriginalCause(t.getCause()) : t;
    }

    @Override
    public IngestionData markDepsMissing(Pom p) {
        var a = MavenRepositoryUtils.toArtifact(p);
        return update(a, d -> {
            d.status = IngestionStatus.DEPS_MISSING;
            d.pom = p;
        });
    }

    @Override
    public IngestionData markDone(Artifact a) {
        return update(a, d -> {
            d.status = IngestionStatus.DONE;
        });
    }

    private IngestionData update(Artifact a, Consumer<IngestionData> c) {
        var f = f(a);

        var d = f.exists() //
                ? io.readFromFile(f, IngestionData.class)
                : new IngestionData();

        // the artifact might have been altered, e.g., fixed packaging
        d.artifact = a;
        d.createdWith = d.createdWith == null //
                ? toolVersion.get()
                : getSmaller(d.createdWith, toolVersion.get());

        c.accept(d);
        io.writeToFile(d, f);

        return d;
    }

    private File f(Artifact a) {
        return MavenRepositoryUtils.getMavenFilePath(baseDir, a, "ingestion");
    }

    private static String getSmaller(String v1, String v2) {
        var cv1 = new ComparableVersion(v1);
        var cv2 = new ComparableVersion(v2);
        return cv1.compareTo(cv2) < 1 //
                ? v1
                : v2;
    }
}