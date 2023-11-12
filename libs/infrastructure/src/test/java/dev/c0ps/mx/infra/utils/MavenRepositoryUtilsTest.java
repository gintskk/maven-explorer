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
package dev.c0ps.mx.infra.utils;

import static dev.c0ps.test.TestLoggerUtils.assertLogsContain;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.c0ps.maven.data.PomBuilder;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils.UrlCheck;
import dev.c0ps.test.HttpTestServer;
import dev.c0ps.test.TestLoggerUtils;

public class MavenRepositoryUtilsTest {

    private static final int HTTP_PORT = 13579;
    private static final String ARTIFACT_REPO = "http://127.0.0.1:" + HTTP_PORT;
    private static final String CENTRAL_REPO = "https://repo.maven.apache.org/maven2/";
    private static final String SOME_CONTENT = "<some content>";

    private static HttpTestServer server;

    @TempDir
    private File dirM2;
    private MavenRepositoryUtils sut;

    @BeforeAll
    public static void setupAll() throws IOException {
        server = new HttpTestServer(HTTP_PORT);
        server.start();
    }

    @AfterAll
    public static void teardownAll() {
        server.stop();
    }

    @BeforeEach
    public void setup() {
        server.reset();
        sut = new MavenRepositoryUtils(dirM2);
        TestLoggerUtils.clearLog();
    }

    @Test
    public void localPomFile() {
        var a = new Artifact("g.g2", "a", "1.2.3", "jar");

        var actual = sut.getLocalPomFile(a);
        var expected = Paths.get(dirM2.getPath(), "repository", "g", "g2", "a", "1.2.3", "a-1.2.3.pom").toFile();
        assertEquals(expected, actual);
    }

    @Test
    public void getM2Path() {
        var a = new Artifact("g.g2", "a", "1.2.3", "jar");

        var actual = sut.getM2Path(a);
        var expected = Paths.get(dirM2.getPath(), "repository", "g", "g2", "a", "1.2.3", "a-1.2.3.jar").toFile();
        assertEquals(expected, actual);
    }

    @Test
    public void getM2PathWithClassifier() {
        var a = new Artifact("g.g2", "a", "1.2.3", "jar");

        var actual = sut.getM2PathWithClassifier(a, "x", "ext");
        var expected = Paths.get(dirM2.getPath(), "repository", "g", "g2", "a", "1.2.3", "a-1.2.3-x.ext").toFile();
        assertEquals(expected, actual);
    }

    @Test
    public void checkGetRequest_basicExistence() {
        var a = new Artifact("g", "a", "1.2.3", "jar");
        a.repository = ARTIFACT_REPO;

        var actual = sut.checkGetRequestNonStatic(a);
        var expected = new UrlCheck(ARTIFACT_REPO + "/g/a/1.2.3/a-1.2.3.jar", null);
        assertEquals(expected, actual);
    }

    @Test
    public void checkGetRequest_basicAbsence() {
        var a = new Artifact("g", "a", "1.2.3", "jar");
        a.repository = ARTIFACT_REPO;

        server.addResponse(404, TEXT_PLAIN, SOME_CONTENT);

        var actual = sut.checkGetRequestNonStatic(a);
        var expected = new UrlCheck(null, null);
        assertEquals(expected, actual);
    }

    @Test
    public void checkGetRequest_lastModified() {
        var a = new Artifact("g", "a", "1.2.3", "jar");
        a.repository = ARTIFACT_REPO;

        var expected = new Date(123456 * 1000);
        server.addResponse(200, TEXT_PLAIN, SOME_CONTENT).setLastModified(expected);

        var actual = sut.checkGetRequestNonStatic(a).lastModified;
        assertEquals(expected, actual);
    }

    @Test
    public void checkGetRequest_followsRedirects() {
        var a = new Artifact("g", "a", "1.2.3", "jar");
        a.repository = ARTIFACT_REPO;

        var relPath = "/g/a/1.2.3/a-1.2.3.jar";

        server.addResponse(301, TEXT_PLAIN, SOME_CONTENT).setLocation(ARTIFACT_REPO + "/a" + relPath);
        server.addResponse(302, TEXT_PLAIN, SOME_CONTENT).setLocation(ARTIFACT_REPO + "/b" + relPath);
        server.addResponse(303, TEXT_PLAIN, SOME_CONTENT).setLocation(ARTIFACT_REPO + "/c" + relPath);
        server.addResponse(307, TEXT_PLAIN, SOME_CONTENT).setLocation(ARTIFACT_REPO + "/d" + relPath);
        server.addResponse(308, TEXT_PLAIN, SOME_CONTENT).setLocation(ARTIFACT_REPO + "/e" + relPath);
        server.addResponse(200, TEXT_PLAIN, SOME_CONTENT);

        var actual = sut.checkGetRequestNonStatic(a);
        var expected = new UrlCheck(ARTIFACT_REPO + "/e" + relPath, null);
        assertEquals(expected, actual);
    }

    @Test
    public void doesNotExist() {
        var res = par("g1.g2:a:pt:1");
        server.addResponse(404, TEXT_PLAIN, SOME_CONTENT);
        assertFalse(sut.doesExist(res.pom()));
    }

    @Test
    public void doesExist() {
        var res = par("g1.g2:a:pt:1");
        server.addResponse(TEXT_PLAIN, SOME_CONTENT);
        assertTrue(sut.doesExist(res.pom()));
    }

    @Test
    public void getMavenFilePath() {
        var tmp = Paths.get("tmp").toFile();
        var a = new Artifact("g.g2", "a", "1.2.3", "ext");
        var actual = MavenRepositoryUtils.getMavenFilePath(tmp, a);
        var expected = Paths.get("tmp", "g", "g2", "a", "1.2.3", "a-1.2.3.ext").toFile();
        assertEquals(expected, actual);
    }

    @Test
    public void getUrl() {
        var a = new Artifact("g.g2", "a", "1.2.3", "ext");
        var actual = sut.getUrl(a);
        var expected = url(CENTRAL_REPO + "g/g2/a/1.2.3/a-1.2.3.ext");
        assertEquals(expected, actual);
    }

    @Test
    public void getUrl_missingSlash() {
        var centralNoSlash = CENTRAL_REPO.substring(0, CENTRAL_REPO.length() - 1);
        var a = new Artifact("g.g2", "a", "1.2.3", "ext").setRepository(centralNoSlash);
        var actual = sut.getUrl(a);
        var expected = url(CENTRAL_REPO + "g/g2/a/1.2.3/a-1.2.3.ext");
        assertEquals(expected, actual);
    }

    @Test
    public void getUrlWithClassifier() {
        var a = new Artifact("g.g2", "a", "1.2.3", "ext");
        var actual = sut.getUrlWithClassifier(a, "x", "foo");
        var expected = url(CENTRAL_REPO + "g/g2/a/1.2.3/a-1.2.3-x.foo");
        assertEquals(expected, actual);
    }

    @Test
    public void getUrlWithClassifier_missingSlash() {
        var centralNoSlash = CENTRAL_REPO.substring(0, CENTRAL_REPO.length() - 1);
        var a = new Artifact("g.g2", "a", "1.2.3", "ext").setRepository(centralNoSlash);
        var actual = sut.getUrlWithClassifier(a, "x", "foo");
        var expected = url(CENTRAL_REPO + "g/g2/a/1.2.3/a-1.2.3-x.foo");
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getMavenRelativeUrlPath() {
        var a = new Artifact("g.g2", "a", "1.2.3", "ext");
        var actual = MavenRepositoryUtils.getMavenRelativeUrlPath(a);
        var expected = "g/g2/a/1.2.3/a-1.2.3.ext";
        assertEquals(expected, actual);
    }

    @Test
    public void toArtifact() {
        var pb = new PomBuilder();
        pb.groupId = "g";
        pb.artifactId = "a";
        pb.version = "v";
        pb.packagingType = "pt";
        pb.artifactRepository = "http://somewhere";
        pb.releaseDate = 1234567890;

        var expected = new Artifact("g", "a", "v", "pt") //
                .setRepository("http://somewhere") //
                .setReleaseDate(1234567890);

        var actual = MavenRepositoryUtils.toArtifact(pb.pom());
        assertEquals(expected, actual);
    }

    @Test
    public void toGAV() {
        var a = new Artifact("g", "a", "v", "p").setReleaseDate(1234).setRepository("r");
        var actual = MavenRepositoryUtils.toGAV(a);
        var expected = "g:a:v";
        assertEquals(expected, actual);
    }

    @Test
    public void downloadToM2() throws IOException {
        var a = new Artifact("g.g2", "a", "1.2.3").setRepository(ARTIFACT_REPO);
        server.addResponse("text/plain", SOME_CONTENT);

        var f = sut.downloadToM2WithClassifier(a, "c", "ext");

        // server request
        assertEquals(1, server.requests.size());
        var req = server.requests.get(0);
        assertEquals("GET", req.method);
        assertEquals("/g/g2/a/1.2.3/a-1.2.3-c.ext", req.path);

        // file creation
        assertTrue(f.exists());
        var expectedPath = Paths.get(dirM2.getPath(), "repository", "g", "g2", "a", "1.2.3", "a-1.2.3-c.ext").toFile();
        assertEquals(expectedPath, f);

        // file contents
        var actualContent = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        assertEquals(SOME_CONTENT, actualContent);

        // log
        var logs = TestLoggerUtils.getFormattedLogs(MavenRepositoryUtils.class);
        assertEquals(3, logs.size());
        assertEquals("WARN Manual downloads do not update Maven metadata, use with caution!", logs.get(0));
        assertEquals("INFO Downloading g.g2:a:1.2.3:null:0@http://127.0.0.1:13579 (classifier: c, ext: ext) ...", logs.get(1));
        assertTrue(logs.get(2).startsWith("INFO Downloaded g.g2:a:1.2.3:null:0@http://127.0.0.1:13579 (classifier: c, ext: ext), took "));
        assertFalse(logs.get(2).contains("{}"));
        assertTrue(logs.get(2).endsWith("ms."));
    }

    @Test
    public void downloadToM2_existingFilesGetSkipped() throws IOException {
        var a = new Artifact("g.g2", "a", "1.2.3").setRepository(ARTIFACT_REPO);

        sut.downloadToM2WithClassifier(a, "c", "ext");
        server.requests.clear();
        TestLoggerUtils.clearLog();

        var f = sut.downloadToM2WithClassifier(a, "c", "ext");
        TestLoggerUtils.assertLogsContain(MavenRepositoryUtils.class, "INFO Download of %s (classifier: c, ext: ext) skipped: File exists.", a);

        assertTrue(f.exists());
        assertEquals(List.of(), server.requests);
    }

    @Test
    public void downloadToM2_unknownHost() throws IOException {
        var a = new Artifact("g.g2", "a", "1.2.3").setRepository("https://does.not.exist/");
        assertNull(sut.downloadToM2WithClassifier(a, "c", "ext"));
        assertLogsContain(MavenRepositoryUtils.class, "ERROR Unknown repository host: https://does.not.exist/");
    }

    @Test
    public void downloadToM2_notFound() throws IOException {
        var a = new Artifact("g.g2", "a", "1.2.3").setRepository(ARTIFACT_REPO);
        server.addResponse(404, "text/plain", SOME_CONTENT);
        assertNull(sut.downloadToM2WithClassifier(a, "c", "ext"));
        assertLogsContain(MavenRepositoryUtils.class, "ERROR Artifact not found in repository: %s", a);
    }

    private static PomBuilder par(String gapt) {
        String[] parts = gapt.split(":");
        var par = new PomBuilder();
        par.groupId = parts[0];
        par.artifactId = parts[1];
        par.packagingType = parts[2];
        par.version = parts[3];
        par.artifactRepository = ARTIFACT_REPO;
        return par;
    }

    private static URL url(String spec) {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}