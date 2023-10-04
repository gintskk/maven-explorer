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
package eu.f4sten.mavendownloader.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import dev.c0ps.maven.data.Pom;
import dev.c0ps.maveneasyindex.Artifact;

public class IngestionDataTest {

    private static final Artifact A1 = mock(Artifact.class);
    private static final Artifact A2 = mock(Artifact.class);

    private static final Pom P1 = mock(Pom.class);
    private static final Pom P2 = mock(Pom.class);

    @Test
    public void defaults() {
        var a = new IngestionData();

        var b = new IngestionData();
        b.artifact = null;
        b.numCrashes = 0;
        b.pom = null;
        b.status = null;
        b.createdWith = null;

        assertEquals(b, a);
        assertEquals(b.hashCode(), a.hashCode());
    }

    @Test
    public void equalDefaults() {
        var a = new IngestionData();
        var b = new IngestionData();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalNonDefaults() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffArtifact() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A2, 2, P1, IngestionStatus.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffNumCrashes() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A1, 3, P1, IngestionStatus.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffPom() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P2, IngestionStatus.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffStatus() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, IngestionStatus.DONE, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffVersion() {
        var a = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, IngestionStatus.FOUND, "1.2.4");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = new IngestionData().toString();
        assertTrue(actual.startsWith(IngestionData.class.getName() + "@"));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("numCrashes="));
    }

    private static IngestionData someIngestionState(Artifact a, int numCrashes, Pom p, IngestionStatus s, String v) {
        var i = new IngestionData();
        i.artifact = a;
        i.numCrashes = numCrashes;
        i.pom = p;
        i.status = s;
        i.createdWith = v;
        return i;
    }
}