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
package dev.c0ps.mx.pomanalyzer.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.module.ResolutionException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.c0ps.commons.ResourceUtils;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.pomanalyzer.data.ResolutionResult;
import dev.c0ps.mx.pomanalyzer.exceptions.MissingPomFileException;
import dev.c0ps.test.TestLoggerUtils;

// The artifact source resolution breaks caching mechanisms by deleting packages from the
// local .m2 folder. This exact functionality is tested here, so the test suite will download
// dependencies over-and-over again on every build. Enable this test only for local tests.
@Disabled
public class ShrinkwrapResolverTest {

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    @Test
    public void reminderToReenableDisabledAnnotationOnClass() {
        fail("Suite is expensive and should only be run locally. Re-enable @Disabled annotation.");
    }

    private MavenRepositoryUtils mru;
    private ShrinkwrapResolver sut;

    @BeforeEach
    public void setup() {
        mru = mock(MavenRepositoryUtils.class);
        sut = new ShrinkwrapResolver(mru);
        TestLoggerUtils.clearLog();
    }

    // TODO add test for org.javassist:javassist:3.24.0-GA:jar:0@CENTRAL which fails for SYSTEM scope

    @Test
    public void failNoLocalPom() {
        var a = new Artifact("g", "a", "v");
        when(mru.getLocalPomFile(a)).thenReturn(new File("/non/existing"));
        var e = assertThrows(MissingPomFileException.class, () -> {
            sut.resolveDependencies(a);
        });
        assertEquals("Local .m2 folder does not contain a pom file for g:a:v:null:0@null", e.getMessage());
    }

    @Test
    public void failNonExistingDepRepo() {
        assertThrows(ResolutionException.class, () -> {
            resolveTestPom("non-existing-dep-repo.pom");
        });
    }

    @Test
    public void failNonExistingDep() {
        assertThrows(ResolutionException.class, () -> {
            resolveTestPom("non-existing-dep.pom");
        });
    }

    @Test
    public void resolveDirectDependencies() {
        var actual = resolveTestPom("basic.pom");
        var expected = Set.of(JSR305, COMMONS_LANG3, REMLA);
        assertEquals(expected, actual);
    }

    @Test
    public void resolveDirectDependenciesWithTraiilingSlash() {
        var actual = resolveTestPom("basic-with-trailing-repo-slashes.pom");
        var expected = Set.of(JSR305, COMMONS_LANG3, REMLA);
        assertEquals(expected, actual);
    }

    @Test
    public void resolveTransitiveDependencies() {
        var actual = resolveTestPom("transitive.pom");
        var expected = Set.of(COMMONS_TEXT, COMMONS_LANG3);
        assertEquals(expected, actual);
    }

    @Test
    public void ensureAllScopesAreIncluded() {
        var actual = resolveTestPom("scopes.pom");
        assertTrue(actual.contains(JSR305), "JSR305"); // default (none)
        assertTrue(actual.contains(SLF4J), "SLF4J"); // compile
        assertTrue(actual.contains(COMMONS_TEXT), "COMMONS_TEXT"); // runtime
        assertTrue(actual.contains(COMMONS_LANG3), "COMMONS_LANG3"); // provided
        assertTrue(actual.contains(OKIO), "OKIO"); // system
        assertTrue(actual.contains(OPENTEST), "JUNIT"); // test

        // direct deps + one (broken) system dep (not found via Maven)
        assertEquals(6 + 1, actual.size());
    }

    @Test
    public void noDependencies() {
        var actual = resolveTestPom("no-dependencies.pom");
        var expected = new HashSet<ResolutionResult>();
        assertEquals(expected, actual);
    }

    @Test
    public void unresolvableDependencies() {
        assertThrows(ResolutionException.class, () -> {
            resolveTestPom("unresolvable.pom");
        });
    }

    private static final Artifact JSR305 = central("com.google.code.findbugs:jsr305:3.0.2:jar");
    private static final Artifact SLF4J = central("org.slf4j:slf4j-api:1.7.32:jar");
    private static final Artifact COMMONS_LANG3 = central("org.apache.commons:commons-lang3:3.9:jar");
    private static final Artifact COMMONS_TEXT = central("org.apache.commons:commons-text:1.8:jar");
    private static final Artifact OKIO = central("com.squareup.okio:okio:3.0.0:jar");
    private static final Artifact OPENTEST = central("org.opentest4j:opentest4j:1.2.0:jar");
    private static final Artifact REMLA = central("remla:mylib:0.0.5:jar") //
            .setRepository("https://gitlab.com/api/v4/projects/26117144/packages/maven/");

    private static Artifact central(String coord) {
        var parts = coord.split(":");
        return new Artifact(parts[0], parts[1], parts[2], parts[3]).setRepository(CENTRAL);
    }

    private Set<Artifact> resolveTestPom(String pathToPom) {
        var fullPath = Path.of(ShrinkwrapResolverTest.class.getSimpleName(), pathToPom);
        var pom = ResourceUtils.getTestResource(fullPath.toString());
        var someArtifact = new Artifact("...", "...", "...", "...");
        when(mru.getLocalPomFile(someArtifact)).thenReturn(pom);
        return sut.resolveDependencies(someArtifact);
    }
}