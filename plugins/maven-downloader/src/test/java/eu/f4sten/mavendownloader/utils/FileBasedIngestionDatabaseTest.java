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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import dev.c0ps.io.IoUtils;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maven.data.PomBuilder;
import dev.c0ps.maveneasyindex.Artifact;
import eu.f4sten.infra.utils.Version;
import eu.f4sten.mavendownloader.data.IngestionData;
import eu.f4sten.mavendownloader.data.IngestionStatus;

public class FileBasedIngestionDatabaseTest {

    private static final String NL = System.lineSeparator();
    public static final Throwable SOME_THROWABLE = new IllegalArgumentException();
    public static final String SOME_STACKTRACK_START = "java.lang.IllegalArgumentException" + NL
            + "\tat eu.f4sten.mavendownloader.utils.FileBasedIngestionDatabaseTest.<clinit>(FileBasedIngestionDatabaseTest.java:";

    @TempDir
    private File tempDir;

    private IoUtils io;
    private FileBasedIngestionDatabase sut;

    private String toolVersion;

    @BeforeEach
    public void setup() {
        io = mock(IoUtils.class);
        when(io.getBaseFolder()).thenReturn(tempDir);

        var v = mock(Version.class);
        when(v.get()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return toolVersion;
            }
        });
        toolVersion = "1.2.3";
        sut = new FileBasedIngestionDatabase(v, io);
    }

    @Test
    public void unknownIsNull() {
        var actual = sut.getCurrentResult(a(1));
        assertNull(actual);
        verify(io).getBaseFolder();
        verifyNoMoreInteractions(io);
    }

    @Test
    public void readsExisting() {
        var d = new IngestionData();
        d.artifact = a(1);
        d.status = IngestionStatus.FOUND;
        mockRead(d);

        var actual = sut.getCurrentResult(a(1));
        assertSame(d, actual);
    }

    @Test
    public void requested() {
        sut.markRequested(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.REQUESTED;
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
        sut.markFound(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.FOUND;
        assertLastWrite(expected);
    }

    @Test
    public void foundReturnsLastWrite() {
        var a = sut.markFound(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void notFound() {
        sut.markNotFound(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.NOT_FOUND;
        assertLastWrite(expected);
    }

    @Test
    public void notFoundReturnsLastWrite() {
        var a = sut.markNotFound(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void resolved() {
        sut.markResolved(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.RESOLVED;
        assertLastWrite(expected);
    }

    @Test
    public void resolvedReturnsLastWrite() {
        var a = sut.markResolved(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void depsMissing() {
        sut.markDepsMissing(p(1));

        var expected = d(a(1));
        expected.pom = p(1);
        expected.status = IngestionStatus.DEPS_MISSING;
        assertLastWrite(expected);
    }

    @Test
    public void depsMissingReturnsLastWrite() {
        var a = sut.markDepsMissing(p(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void done() {
        sut.markDone(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.DONE;
        assertLastWrite(expected);
    }

    @Test
    public void doneReturnsLastWrite() {
        var a = sut.markDone(a(1));
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void crashWillUnwrapRuntimeExceptions() {
        sut.markCrashed(a(1), new RuntimeException(SOME_THROWABLE));

        var expected = d(a(1));
        expected.status = IngestionStatus.CRASHED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void crashWillUnwrapRuntimeExceptionsTwice() {
        sut.markCrashed(a(1), new RuntimeException(new RuntimeException(SOME_THROWABLE)));

        var expected = d(a(1));
        expected.status = IngestionStatus.CRASHED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void crashCanHandleNonExistence() {
        sut.markCrashed(a(1), SOME_THROWABLE);

        var expected = d(a(1));
        expected.status = IngestionStatus.CRASHED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void crashReturnsLastWrite() {
        var a = sut.markCrashed(a(1), SOME_THROWABLE);
        var b = getLastWrite(a(1));
        assertSame(a, b);
    }

    @Test
    public void crashFirst() throws IOException {
        var d = d(a(1));
        d.status = IngestionStatus.RESOLVED;
        mockRead(d);

        sut.markCrashed(a(1), SOME_THROWABLE);

        var expected = d(a(1));
        expected.status = IngestionStatus.CRASHED;
        expected.numCrashes = 1;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void crashSecond() throws IOException {
        var d = d(a(1));
        d.status = IngestionStatus.CRASHED;
        d.numCrashes = 1;
        mockRead(d);

        sut.markCrashed(a(1), SOME_THROWABLE);

        var expected = d(a(1));
        expected.status = IngestionStatus.CRASHED;
        expected.numCrashes = 2;
        expected.stacktrace = SOME_STACKTRACK_START;
        assertLastWrite(expected);
    }

    @Test
    public void createdWithGetsDowngraded() throws IOException {
        var d = d(a(1));
        d.status = IngestionStatus.FOUND;
        mockRead(d);

        toolVersion = "0.1.2";
        sut.markResolved(a(1));

        var expected = new IngestionData();
        expected.artifact = a(1);
        expected.status = IngestionStatus.RESOLVED;
        expected.createdWith = "0.1.2";
        assertLastWrite(expected);
    }

    @Test
    public void createdWithDoesNotGetUpgraded() throws IOException {
        var d = d(a(1));
        d.status = IngestionStatus.FOUND;
        d.createdWith = "0.1.2";
        mockRead(d);

        toolVersion = "1.2.3";
        sut.markResolved(a(1));

        var expected = d(a(1));
        expected.status = IngestionStatus.RESOLVED;
        expected.createdWith = "0.1.2";
        assertLastWrite(expected);
    }

    private IngestionData mockRead(IngestionData d) {
        var f = f(d.artifact);
        try {
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        when(io.readFromFile(eq(f), eq(IngestionData.class))).thenReturn(d);
        return d;
    }

    private void assertLastWrite(IngestionData expected) {
        var actual = getLastWrite(expected.artifact);

        if (actual.stacktrace != null && actual.stacktrace.startsWith(SOME_STACKTRACK_START)) {
            actual.stacktrace = SOME_STACKTRACK_START;
        }

        assertEquals(expected, actual);
    }

    private IngestionData getLastWrite(Artifact a) {
        var captor = ArgumentCaptor.forClass(IngestionData.class);
        verify(io).writeToFile(captor.capture(), eq(f(a)));
        var actual = captor.getValue();
        return actual;
    }

    private File f(Artifact a) {

        return Paths.get(tempDir.getAbsolutePath(), //
                "g", //
                "g2", //
                "a", //
                a.version, //
                a.artifactId + "-" + a.version + ".ingestion" //
        ).toFile();
    }

    private IngestionData d(Artifact a) {
        var d = new IngestionData();
        d.artifact = a;
        d.createdWith = toolVersion;
        return d;
    }

    private static Artifact a(int i) {
        var a = new Artifact();
        a.groupId = "g.g2";
        a.artifactId = "a";
        a.version = "1.2." + i;
        a.releaseDate = 1234;
        a.repository = "http://server";
        return a;
    }

    private static Pom p(int i) {
        return new PomBuilder() //
                .groupId("g.g2") //
                .artifactId("a") //
                .version("1.2." + i) //
                .releaseDate(1234) //
                .artifactRepository("http://server") //
                .pom();
    }
}