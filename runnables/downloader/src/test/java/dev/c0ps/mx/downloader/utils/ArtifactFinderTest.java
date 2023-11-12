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

import static dev.c0ps.mx.downloader.data.Status.FOUND;
import static dev.c0ps.mx.downloader.data.Status.REQUESTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import dev.c0ps.maven.data.Pom;
import dev.c0ps.maven.data.PomBuilder;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Result;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils.UrlCheck;

// TODO mock http queries to avoid actual calls and remove this annotation
@Disabled("Running this suite is expensive, only run it locally")
public class ArtifactFinderTest {

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String CENTRAL_OLD = "https://repo1.maven.org/maven2/";
    private static final String JUNIT = "junit:junit:4.12:jar:1417709863000";

    private ResultsDatabase db;
    private MavenRepositoryUtils utils;

    private ArtifactFinder sut;

    @BeforeEach
    public void setup() {
        db = mock(ResultsDatabase.class);
        utils = mock(MavenRepositoryUtils.class);
        var actualUtils = new MavenRepositoryUtils(null);

        var captor = ArgumentCaptor.forClass(Artifact.class);
        when(utils.checkGetRequestNonStatic(captor.capture())).thenAnswer(new Answer<UrlCheck>() {
            @Override
            public UrlCheck answer(InvocationOnMock invocation) throws Throwable {
                return actualUtils.checkGetRequestNonStatic(captor.getValue());
            }
        });

        sut = new ArtifactFinder(db, utils);
    }

    @Test
    public void addDisableAnnotationAgain() {
        fail();
    }

    @Test
    public void happyPath() {
        var in = a(JUNIT, CENTRAL);
        var actual = sut.findArtifact(in);
        assertSame(in, actual);
    }

    @Test
    public void happyPathPoms() {
        var in = pom(JUNIT, CENTRAL);
        var actual = sut.findArtifact(in);
        var expected = a(JUNIT, CENTRAL);
        assertEquals(expected, actual);
    }

    @Test
    public void nonExistingArtifact() {
        var in = a("does.not:exist:1.2.3:pom:-1", CENTRAL);
        var actual = sut.findArtifact(in);
        assertNull(actual);
    }

    @Test
    public void addsTrailingSlash() {
        assertFix( //
                a(JUNIT, CENTRAL.substring(0, CENTRAL.length() - 1)), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void doUpdatesReleaseDateWithSmallDeviations() {
        var orig = 1417709863000L;
        var other = orig - 1000 * 60 * 60 * 24 + 1; // <1d
        assertFix( //
                a(JUNIT.replace(Long.toString(orig), Long.toString(other)), CENTRAL), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void doNotUpdatesReleaseDateWithLargeDeviation() {
        var orig = 1417709863000L;
        var other = orig - 1000 * 60 * 60 * 24 - 1; // >1d
        assertFix( //
                a(JUNIT.replace(Long.toString(orig), Long.toString(other)), CENTRAL), //
                a(JUNIT.replace(Long.toString(orig), Long.toString(other)), CENTRAL));
    }

    @Test
    public void rewritesHttpCentral() {
        assertFix( //
                a(JUNIT, CENTRAL.replace("https://", "http://")), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void rewritesHttpCentralOld() {
        assertFix( //
                a(JUNIT, CENTRAL_OLD.replace("https://", "http://")), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void rewritesHttpsCentralOld() {
        assertFix( //
                a(JUNIT, CENTRAL_OLD), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void fixesPackagingCapitalization() {
        assertFix( //
                a(JUNIT.replace("jar", "JAR"), CENTRAL), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void fixesPackaging() {
        assertFix( //
                a(JUNIT.replace(":jar:", ":foo:"), CENTRAL), //
                a(JUNIT, CENTRAL));
    }

    @Test
    public void returnsPreviousResultIfFound() {
        var s = new Result();
        s.artifact = mock(Artifact.class);
        s.status = FOUND;
        when(db.get(a(JUNIT, CENTRAL))).thenReturn(s);

        var actual = sut.findArtifact(a(JUNIT, CENTRAL));

        assertSame(s.artifact, actual);
    }

    @Test
    public void returnsNoPreviousResultIfRequested() {
        var s = new Result();
        s.artifact = mock(Artifact.class);
        s.status = REQUESTED;
        when(db.get(a(JUNIT, CENTRAL))).thenReturn(s);

        var actual = sut.findArtifact(a(JUNIT, CENTRAL));

        assertNotEquals(s.artifact, actual);
    }

    @Test
    public void checksWhenOnlyRequested() {
        var s = new Result();
        s.artifact = mock(Artifact.class);
        s.status = REQUESTED;
        when(db.get(a(JUNIT, CENTRAL))).thenReturn(s);

        var actual = sut.findArtifact(a(JUNIT, CENTRAL));
        assertNotNull(actual);
    }

    @Test
    public void storesResultsWhenResolved() {
        var a = sut.findArtifact(a(JUNIT, CENTRAL));
        verify(db).markFound(a);
    }

    @Test
    public void parseUrl() {
        // actual poms
        assertPackaging("https://repo.maven.apache.org/maven2/org/apache/nifi/nifi-easyrules-bundle/1.21.0/nifi-easyrules-bundle-1.21.0.pom", "pom");
        assertPackaging("https://repo.maven.apache.org/maven2/io/github/rdlopes/maven-root-pom/1.1.9/maven-root-pom-1.1.9.pom", "pom");
        assertPackaging("https://repo.maven.apache.org/maven2/org/ops4j/pax/web/pax-web-features/9.0.8/pax-web-features-9.0.8.pom", "pom");

        // defines jar packaging
        assertPackaging("https://repo.maven.apache.org/maven2/com/izivia/ocpp-operation-information/0.1.17/ocpp-operation-information-0.1.17.pom", "jar");
    }

    private void assertPackaging(String url, String expected) {
        try {
            var actual = ArtifactFinder.parsePackagingFromUrl(new URL(url));
            assertEquals(expected, actual);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

    }

    private void assertFix(Artifact in, Artifact expected) {
        var actual = sut.findArtifact(in);
        assertEquals(expected, actual);
        if (in.equals(expected)) {
            assertSame(in, actual);
        } else {
            assertNotSame(in, actual);
        }
    }

    private static Artifact a(String coord, String repo) {
        var parts = coord.split(":");
        var a = new Artifact();
        a.groupId = parts[0];
        a.artifactId = parts[1];
        a.version = parts[2];
        a.packaging = parts[3];
        a.releaseDate = Long.parseLong(parts[4]);
        a.repository = repo;
        return a;
    }

    private static Pom pom(String coord, String repo) {
        var a = a(coord, repo);
        var p = new PomBuilder();
        p.groupId = a.groupId;
        p.artifactId = a.artifactId;
        p.version = a.version;
        p.packagingType = a.packaging;
        p.artifactRepository = a.repository;
        return p.pom();
    }
}