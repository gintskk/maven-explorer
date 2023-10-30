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
package dev.c0ps.mx.runner.impl.kafka;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

import dev.c0ps.franz.Lane;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;

public class FileBasedLaneManagement implements LaneManagement {

    private File baseDir;

    public FileBasedLaneManagement(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public boolean shouldProcess(Artifact a, Lane l) {
        if (l == Lane.PRIORITY) {
            return true;
        }

        if (l == Lane.ERROR) {
            throw new IllegalArgumentException();
        }

        var f = f(a);

        return read(f) == Lane.NORMAL;

    }

    @Override
    public void reportProgress(Artifact a, Lane lNew) {
        if (lNew == Lane.ERROR) {
            throw new IllegalArgumentException();
        }
        var f = f(a);

        var lOld = read(f);
        if (lOld == Lane.PRIORITY || lOld == lNew) {
            return;
        }

        write(lNew, f);
    }

    private File f(Artifact a) {
        return MavenRepositoryUtils.getMavenFilePath(baseDir, a, "prio");
    }

    private static Lane read(File f) {
        try {
            if (!f.exists()) {
                return Lane.NORMAL;
            }
            var s = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
            return Lane.valueOf(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void write(Lane l, File f) {
        try {
            f.getParentFile().mkdirs();
            FileUtils.writeStringToFile(f, l.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}