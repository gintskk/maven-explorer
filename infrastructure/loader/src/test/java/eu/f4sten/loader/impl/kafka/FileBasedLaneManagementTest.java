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
package eu.f4sten.loader.impl.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.c0ps.franz.Lane;
import dev.c0ps.maveneasyindex.Artifact;

public class FileBasedLaneManagementTest {

    @TempDir
    private File tempDir;

    private FileBasedLaneManagement sut;

    private static final Artifact SOME_ARTIFACT = a(1);

    @BeforeEach
    public void setup() {
        sut = new FileBasedLaneManagement(tempDir);
    }

    @Test
    public void reportsAreNotPersistedN() {
        sut.reportProgress(a(1), Lane.NORMAL);
        assertFalse(f(1).exists());
    }

    @Test
    public void reportsArePersistedNP() {
        sut.reportProgress(a(1), Lane.NORMAL);
        sut.reportProgress(a(1), Lane.PRIORITY);
        assertContent(f(1), "PRIORITY");
    }

    @Test
    public void reportsArePersistedP() {
        sut.reportProgress(a(1), Lane.PRIORITY);
        assertContent(f(1), "PRIORITY");
    }

    @Test
    public void reportsArePersistedPN() {
        sut.reportProgress(a(1), Lane.PRIORITY);
        sut.reportProgress(a(1), Lane.NORMAL);
        assertContent(f(1), "PRIORITY");
    }

    @Test
    public void reportsArePersistedE() {
        assertThrows(IllegalArgumentException.class, () -> {
            sut.reportProgress(SOME_ARTIFACT, Lane.ERROR);
        });
    }

    @Test
    public void alwaysProcessUnknown_Normal() {
        sut.shouldProcess(SOME_ARTIFACT, Lane.NORMAL);
    }

    @Test
    public void alwaysProcessUnknown_Priority() {
        sut.shouldProcess(SOME_ARTIFACT, Lane.PRIORITY);
    }

    @Test
    public void alwaysProcessUnknown_Error() {
        assertThrows(IllegalArgumentException.class, () -> {
            sut.shouldProcess(SOME_ARTIFACT, Lane.ERROR);
        });
    }

    @Test
    public void shouldProcessNN() {
        sut.reportProgress(SOME_ARTIFACT, Lane.NORMAL);
        assertTrue(sut.shouldProcess(SOME_ARTIFACT, Lane.NORMAL));
    }

    @Test
    public void shouldProcessNP() {
        sut.reportProgress(SOME_ARTIFACT, Lane.NORMAL);
        assertTrue(sut.shouldProcess(SOME_ARTIFACT, Lane.PRIORITY));
    }

    @Test
    public void shouldNotProcessPN() {
        sut.reportProgress(SOME_ARTIFACT, Lane.PRIORITY);
        assertFalse(sut.shouldProcess(SOME_ARTIFACT, Lane.NORMAL));
    }

    @Test
    public void shouldProcessPP() {
        sut.reportProgress(SOME_ARTIFACT, Lane.PRIORITY);
        assertTrue(sut.shouldProcess(SOME_ARTIFACT, Lane.PRIORITY));
    }

    @Test
    public void dataIsReadFromDisk() throws IOException {
        File f = f(1);
        FileUtils.write(f, "PRIORITY", StandardCharsets.UTF_8);
        assertFalse(sut.shouldProcess(a(1), Lane.NORMAL));
    }

    private File f(int i) {
        return Paths.get(tempDir.getAbsolutePath(), "g", "g2", "a", "1.2." + i, "a-1.2." + i + ".prio").toFile();
    }

    private static Artifact a(int i) {
        var a = new Artifact();
        a.groupId = "g.g2";
        a.artifactId = "a";
        a.version = "1.2." + i;
        return a;
    }

    private static void assertContent(File f, String expected) {
        try {
            assertTrue(f.exists());
            var actual = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
            assertEquals(expected, actual);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}