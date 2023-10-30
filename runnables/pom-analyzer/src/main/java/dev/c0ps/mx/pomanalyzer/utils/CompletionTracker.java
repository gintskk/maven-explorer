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
package dev.c0ps.mx.pomanalyzer.utils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class CompletionTracker {

    public static final String EXT = "done";

    private final MavenRepositoryUtils utils;
    private final File baseDir;

    private final Set<Artifact> started = new HashSet<>();

    @Inject
    public CompletionTracker(MavenRepositoryUtils utils, @Named("dir.completion") File baseDir) {
        this.utils = utils;
        this.baseDir = baseDir;
    }

    public void clearMemory() {
        started.clear();
    }

    public boolean shouldSkip(Artifact a) {
        if (started.contains(a)) {
            return true;
        }
        if (exists(a)) {
            started.add(a);
            return true;
        }
        return false;
    }

    public void markStarted(Artifact a) {
        started.add(a);
    }

    public void markAborted(Artifact a) {
        if (f(a).exists()) {
            throw new IllegalStateException(String.format("Cannot abort a completed artifact (%s)", a));
        }
        started.remove(a);
    }

    public void markCompleted(Artifact a) {
        started.add(a);
        try {
            var f = f(a);
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // protected to make it testable
    protected boolean exists(Artifact a) {
        return f(a).exists();
    }

    private File f(Artifact a) {
        return utils.getMavenFilePathNonStatic(baseDir, a, EXT);
    }
}