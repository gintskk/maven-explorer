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

import static dev.c0ps.mx.downloader.data.Status.DEPS_MISSING;
import static dev.c0ps.mx.downloader.data.Status.FOUND;
import static dev.c0ps.mx.downloader.data.Status.RESOLVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import dev.c0ps.commons.AssertsException;
import dev.c0ps.io.IoUtils;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maven.data.PomBuilder;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Result;
import dev.c0ps.mx.downloader.data.Status;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.Version;

public class FileBasedResultsDatabaseTest {

    private static final String NL = System.lineSeparator();
    public static final Throwable SOME_THROWABLE = new IllegalArgumentException();
    public static final String SOME_STACKTRACK_START = "java.lang.IllegalArgumentException" + NL
            + "\tat dev.c0ps.mx.downloader.utils.FileBasedResultsDatabaseTest.<clinit>(FileBasedResultsDatabaseTest.java:";

    @TempDir
    private File tempDir;

    private IoUtils io;
    private FileBasedResultsDatabase sut;

    private String toolVersion;
    private MavenRepositoryUtils mru;

    @BeforeEach
    public void setup() {
        io = mock(IoUtils.class);
        var v = mock(Version.class);
        when(v.get()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return toolVersion;
            }
        });
        toolVersion = "1.2.3";

        mru = mock(MavenRepositoryUtils.class);

        when(mru.getMavenFilePathNonStatic(any(File.class), eq(a(1)), eq("result"))).thenReturn(f(a(1)));
        when(mru.getMavenFilePathNonStatic(any(File.class), eq(a(1, "x")), eq("result"))).thenReturn(f(a(1)));

        sut = new FileBasedResultsDatabase(v, io, mru, tempDir);
    }

    private void mockResult(Result res) {
        var f = f(res.artifact);
        f.getParentFile().mkdirs();
        try {
            f.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        when(io.readFromFile(f, Result.class)).thenReturn(res);
    }

    private void mockResult(Artifact a, Consumer<Result> res) {
        var out = new Result();
        out.artifact = a;
        res.accept(out);
        mockResult(out);
    }

    private void mockStatus(Artifact a, Status status) {
        mockResult(a, r -> {
            r.status = status;
        });
    }

    @Test
    public void testExpectationsAreFine() {
        assertEquals(a(1), MavenRepositoryUtils.toArtifact(p(1)));
    }

    @Test
    public void nullArtifactIsHandled() {
        var e = assertThrows(AssertsException.class, () -> {
            sut.markRequested(null);
        });
        assertEquals("Should not be null.", e.getMessage());
    }

    @Test
    public void successfulProgressionResetsCrashInfo_depsMissing() {
        mockResult(a(1), r -> {
            r.status = RESOLVED;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        sut.markDepsMissing(p(1));

        var b = getLastWrite(a(1));
        assertEquals(0, b.numCrashes);
        assertNull(b.stacktrace);
    }

    @Test
    public void successfulProgressionResetsCrashInfo_crash() {
        mockResult(a(1), r -> {
            r.status = RESOLVED;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        sut.markCrashed(a(1));

        var b = getLastWrite(a(1));
        assertEquals(1, b.numCrashes);
        assertEquals("...", b.stacktrace);
    }

    @Test
    public void successfulProgressionResetsCrashInfo_doneEtAl() {
        mockResult(a(1), r -> {
            r.status = DEPS_MISSING;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        // done is representative for all other cases that do not have a payload
        sut.markDone(a(1));

        var b = getLastWrite(a(1));
        assertEquals(0, b.numCrashes);
        assertNull(b.stacktrace);
    }

    @Test
    public void unknownIsNull() {
        var actual = sut.get(a(1));
        assertNull(actual);
        verifyNoMoreInteractions(io);
    }

    @Test
    public void usesMavenRepositoryUtils() {
        sut.get(a(1));
        verify(mru).getMavenFilePathNonStatic(eq(tempDir), eq(a(1)), eq("result"));
    }

    @Test
    public void readsExisting() {
        var expected = new Result();
        expected.artifact = a(1);
        expected.status = FOUND;
        mockResult(expected);

        var actual = sut.get(a(1));
        assertSame(expected, actual);
    }

    @Test
    public void requested() {
        sut.markRequested(a(1));

        var expected = d(a(1));
        expected.status = Status.REQUESTED;
        assertLastWrite(expected);
    }

    @Test
    public void requestedReturnsLastWrite() {
        var a = sut.markRequested(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void found() {
        mockStatus(a(1), Status.REQUESTED);
        sut.markFound(a(1));

        var expected = d(a(1));
        expected.status = Status.FOUND;
        assertLastWrite(expected);
    }

    @Test
    public void foundReturnsLastWrite() {
        mockStatus(a(1), Status.REQUESTED);
        var a = sut.markFound(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void notFound() {
        mockStatus(a(1), Status.REQUESTED);
        sut.markNotFound(a(1));

        var expected = d(a(1));
        expected.status = Status.NOT_FOUND;
        assertLastWrite(expected);
    }

    @Test
    public void notFoundReturnsLastWrite() {
        mockStatus(a(1), Status.REQUESTED);
        var a = sut.markNotFound(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void resolved() {
        mockStatus(a(1), Status.FOUND);
        sut.markResolved(a(1));

        var expected = d(a(1));
        expected.status = Status.RESOLVED;
        assertLastWrite(expected);
    }

    @Test
    public void resolvedReturnsLastWrite() {
        mockStatus(a(1), Status.FOUND);
        var a = sut.markResolved(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void depsMissing() {
        mockStatus(a(1), Status.RESOLVED);
        sut.markDepsMissing(p(1));

        var expected = d(a(1));
        expected.pom = p(1);
        expected.status = Status.DEPS_MISSING;
        assertLastWrite(expected);
    }

    @Test
    public void depsMissingReturnsLastWrite() {
        mockStatus(a(1), Status.RESOLVED);
        var a = sut.markDepsMissing(p(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void done() {
        mockStatus(a(1), Status.DEPS_MISSING);
        sut.markDone(a(1));

        var expected = d(a(1));
        expected.status = Status.DONE;
        assertLastWrite(expected);
    }

    @Test
    public void doneReturnsLastWrite() {
        mockStatus(a(1), Status.DEPS_MISSING);
        var a = sut.markDone(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void markCrashed() {
        mockResult(a(1), r -> {
            r.status = Status.DEPS_MISSING;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        sut.markCrashed(a(1));

        var expected = d(a(1));
        expected.status = Status.CRASHED;
        expected.numCrashes = 1;
        expected.stacktrace = "...";

        assertLastWrite(expected);
    }

    @Test
    public void markCrashedReturnsLastWrite() {
        mockResult(a(1), r -> {
            r.status = Status.DEPS_MISSING;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        var a = sut.markCrashed(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void markCrashedRequiresNumCrashes() {
        mockResult(a(1), r -> {
            r.status = Status.DEPS_MISSING;
            r.numCrashes = 0;
            r.stacktrace = "...";
        });
        var e = assertThrows(IllegalStateException.class, () -> {
            sut.markCrashed(a(1));
        });
        assertEquals("No crash has been recorded in result", e.getMessage());
    }

    @Test
    public void markCrashedRequiresStacktrace() {
        mockResult(a(1), r -> {
            r.status = Status.DEPS_MISSING;
            r.numCrashes = 1;
            r.stacktrace = null;
        });
        var e = assertThrows(IllegalStateException.class, () -> {
            sut.markCrashed(a(1));
        });
        assertEquals("Stacktrace is null in result", e.getMessage());
    }

    @Test
    public void addCrashWillUnwrapRuntimeExceptions() {
        mockStatus(a(1), Status.RESOLVED);
        sut.recordCrash(a(1), new RuntimeException(SOME_THROWABLE));

        var expected = d(a(1));
        expected.status = Status.RESOLVED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void addCrashWillUnwrapRuntimeExceptionsTwice() {
        mockStatus(a(1), Status.RESOLVED);
        sut.recordCrash(a(1), new RuntimeException(new RuntimeException(SOME_THROWABLE)));

        var expected = d(a(1));
        expected.status = RESOLVED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void addCrashReturnsLastWrite() {
        mockStatus(a(1), Status.RESOLVED);
        var a = sut.recordCrash(a(1), SOME_THROWABLE);
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void addCrashFirst() throws IOException {
        mockStatus(a(1), Status.RESOLVED);

        sut.recordCrash(a(1), SOME_THROWABLE);

        var expected = d(a(1));
        expected.status = Status.RESOLVED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void addCrashSecond() throws IOException {
        mockResult(a(1), d -> {
            d.status = Status.RESOLVED;
            d.numCrashes = 1;
        });

        sut.recordCrash(a(1), SOME_THROWABLE);

        var expected = d(a(1));
        expected.status = Status.RESOLVED;
        expected.numCrashes = 2;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void createdWithGetsDowngraded() throws IOException {
        mockResult(a(1), r -> {
            r.status = Status.FOUND;
        });

        toolVersion = "0.1.2";
        sut.markResolved(a(1));

        var expected = new Result();
        expected.artifact = a(1);
        expected.status = Status.RESOLVED;
        expected.createdWith = "0.1.2";
        assertLastWrite(expected);
    }

    @Test
    public void createdWithDoesNotGetUpgraded() throws IOException {
        mockResult(a(1), r -> {
            r.status = Status.FOUND;
            r.createdWith = "0.1.2";
        });

        toolVersion = "1.2.3";
        sut.markResolved(a(1));

        var expected = d(a(1));
        expected.status = Status.RESOLVED;
        expected.createdWith = "0.1.2";
        assertLastWrite(expected);
    }

    @Test
    public void markRequestedCrashesWhenExisting() throws IOException {
        mockStatus(a(1), Status.REQUESTED);
        var e = assertThrows(IllegalStateException.class, () -> {
            sut.markRequested(a(1, "x"));
        });
        var msg = String.format("Cannot mark package %s as REQUESTED: was known before", a(1, "x"));
        assertEquals(msg, e.getMessage());
    }

    @Test
    public void markRequestedDoesNotCrashesForReportingCrash() throws IOException {
        mockStatus(a(1), Status.REQUESTED);
        sut.recordCrash(a(1), new RuntimeException());
    }

    @Test
    public void markFoundCanUpdateArtifact() throws IOException {
        mockStatus(a(1), Status.REQUESTED);
        sut.markFound(a(1, "x"));
    }

    @Test
    public void cannotUpdateArtifact_notFound() throws IOException {
        mockStatus(a(1), Status.REQUESTED);
        var s = sut.markNotFound(a(1, "x"));
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void cannotUpdateArtifact_resolved() throws IOException {
        mockStatus(a(1), FOUND);
        var s = sut.markResolved(a(1, "x"));
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void cannotUpdateArtifact_addCrash() throws IOException {
        mockStatus(a(1), FOUND);
        var s = sut.recordCrash(a(1, "x"), new RuntimeException());
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void cannotUpdateArtifact_markCrashed() throws IOException {
        mockResult(a(1), r -> {
            r.status = FOUND;
            r.numCrashes = 1;
            r.stacktrace = "...";
        });
        var s = sut.markCrashed(a(1, "x"));
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void cannotUpdateArtifact_depsMissing() throws IOException {
        mockStatus(a(1), RESOLVED);
        var s = sut.markDepsMissing(p(1, "x"));
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void cannotUpdateArtifact_done() throws IOException {
        mockStatus(a(1), DEPS_MISSING);
        var s = sut.markDone(a(1, "x"));
        assertEquals(s.artifact, a(1));
    }

    @Test
    public void reset() throws IOException {
        mockStatus(a(1), DEPS_MISSING);
        var r = sut.get(a(1));
        assertNotNull(r);

        sut.reset(a(1));

        r = sut.get(a(1));
        assertNull(r);
    }

    private void assertLastWrite(Result expected) {
        var actual = getLastWrite(expected.artifact);

        if (actual.stacktrace != null && actual.stacktrace.startsWith(SOME_STACKTRACK_START)) {
            actual.stacktrace = SOME_STACKTRACK_START;
        }

        assertEquals(expected, actual);
    }

    private Result getLastWrite(Artifact a) {
        var captor = ArgumentCaptor.forClass(Result.class);
        verify(io).writeToFile(captor.capture(), eq(f(a)));
        var actual = captor.getValue();
        return actual;
    }

    private File f(Artifact a) {
        // simplified for testing
        var g = new File(tempDir, a.groupId);
        var ga = new File(g, a.artifactId);
        var gav = new File(ga, a.version + ".result");
        return gav;
    }

    private Result d(Artifact a) {
        var d = new Result();
        d.artifact = a;
        d.createdWith = toolVersion;
        return d;
    }

    private static Artifact a(int i) {
        return a(i, "jar");
    }

    private static Artifact a(int i, String packaging) {
        var a = new Artifact();
        a.groupId = "g.g2";
        a.artifactId = "a";
        a.version = "1.2." + i;
        a.packaging = packaging;
        a.releaseDate = 1234;
        a.repository = "http://server";
        return a;
    }

    private static Pom p(int i) {
        return p(i, "jar");
    }

    private static Pom p(int i, String packaging) {
        return new PomBuilder() //
                .groupId("g.g2") //
                .artifactId("a") //
                .version("1.2." + i) //
                .packagingType(packaging) //
                .releaseDate(1234) //
                .artifactRepository("http://server") //
                .pom();
    }
}