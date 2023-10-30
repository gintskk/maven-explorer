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

import static dev.c0ps.mx.pomanalyzer.utils.CompletionTracker.EXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;

public class CompletionTrackerTest {

    private static final Artifact A = new Artifact("g", "a", "1.2.3");

    @TempDir
    private File tmpDir;
    private MavenRepositoryUtils utils;

    private CompletionTracker sut;

    @BeforeEach
    public void setup() {
        utils = mock(MavenRepositoryUtils.class);
        when(utils.getMavenFilePathNonStatic(eq(tmpDir), eq(A), eq(EXT))).thenReturn(f());
        init();
    }

    private File f() {
        return new File(tmpDir, "x");
    }

    private void init() {
        sut = new CompletionTracker(utils, tmpDir);
    }

    @Test
    public void extensionIsDone() {
        assertEquals("done", EXT);
    }

    @Test
    public void unknownArtifactsShouldBeProcessed() {
        assertFalse(sut.shouldSkip(A));
    }

    @Test
    public void unknownArtifactsAreAlsoCheckedOnDisk() {
        var sut = new TestCompletionTracker();
        sut.shouldSkip(A);
        assertTrue(sut.hasCalledExists);
    }

    @Test
    public void skipStartedArtifacts() {
        sut.markStarted(A);
        assertTrue(sut.shouldSkip(A));
    }

    @Test
    public void startingDoesNotPersistOverRestarts() {
        sut.markStarted(A);
        init();
        assertFalse(sut.shouldSkip(A));
    }

    @Test
    public void skipFinalizedArtifacts() {
        sut.markCompleted(A);
        assertTrue(sut.shouldSkip(A));
    }

    @Test
    public void finalizationPersistsOverRestarts() {
        sut.markCompleted(A);
        init();
        assertTrue(sut.shouldSkip(A));
    }

    @Test
    public void finalizationCreatesTheExpectedFile() {
        sut.markCompleted(A);
        verify(utils).getMavenFilePathNonStatic(eq(tmpDir), eq(A), eq(EXT));
        assertTrue(f().exists());
    }

    @Test
    public void noFileSystemIfStoredInMemory() {
        var sut = new TestCompletionTracker();
        sut.markStarted(A);
        sut.shouldSkip(A);
        assertFalse(sut.hasCalledExists);
    }

    @Test
    public void clearMemory() {
        var sut = new TestCompletionTracker();
        sut.markStarted(A);
        sut.markCompleted(A);
        sut.shouldSkip(A);
        assertFalse(sut.hasCalledExists);

        sut.clearMemory();

        sut.shouldSkip(A);
        assertTrue(sut.hasCalledExists);
    }

    @Test
    public void completionIsCached() {
        var sut = new TestCompletionTracker();
        sut.markCompleted(A);
        sut.shouldSkip(A);
        assertFalse(sut.hasCalledExists);
    }

    @Test
    public void shouldSkipIsCached() {
        var sut = new TestCompletionTracker();
        sut.markCompleted(A);

        sut = new TestCompletionTracker();
        sut.shouldSkip(A);
        assertTrue(sut.hasCalledExists);

        sut.hasCalledExists = false;

        sut.shouldSkip(A);
        assertFalse(sut.hasCalledExists);
    }

    @Test
    public void canAbortMemory() {
        sut.markStarted(A);
        sut.markAborted(A);
        assertFalse(sut.shouldSkip(A));
    }

    @Test
    public void cannotAbortFinalization() {
        sut.markCompleted(A);
        init();
        var e = assertThrows(IllegalStateException.class, () -> {
            sut.markAborted(A);
        });
        assertEquals("Cannot abort a completed artifact (g:a:1.2.3:null:0@null)", e.getMessage());
    }

    @Test
    public void cannotAbortFinalizationEvenWhenStartedInMemory() {
        sut.markCompleted(A);
        sut.markStarted(A);
        init();
        var e = assertThrows(IllegalStateException.class, () -> {
            sut.markAborted(A);
        });
        assertEquals("Cannot abort a completed artifact (g:a:1.2.3:null:0@null)", e.getMessage());
    }

    @Test
    public void smokeTestWhenUsedInPractice() {
        sut = new CompletionTracker(new MavenRepositoryUtils(null), tmpDir);
        assertFalse(sut.shouldSkip(A));
        sut.markStarted(A);
        sut.markAborted(A);
        sut.markCompleted(A);

        sut = new CompletionTracker(new MavenRepositoryUtils(null), tmpDir);
        assertTrue(sut.shouldSkip(A));
    }

    private class TestCompletionTracker extends CompletionTracker {

        private boolean hasCalledExists;

        TestCompletionTracker() {
            super(utils, tmpDir);
        }

        @Override
        protected boolean exists(Artifact a) {
            hasCalledExists = true;
            return super.exists(a);
        }
    }
}