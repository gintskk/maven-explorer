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
package dev.c0ps.mx.downloader.data;

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
        var a = new Result();

        var b = new Result();
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
        var a = new Result();
        var b = new Result();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalNonDefaults() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffArtifact() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A2, 2, P1, Status.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffNumCrashes() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A1, 3, P1, Status.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffPom() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P2, Status.FOUND, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffStatus() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, Status.DONE, "1.2.3");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalDiffVersion() {
        var a = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.3");
        var b = someIngestionState(A1, 2, P1, Status.FOUND, "1.2.4");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = new Result().toString();
        assertTrue(actual.startsWith(Result.class.getName() + "@"));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("numCrashes="));
    }

    private static Result someIngestionState(Artifact a, int numCrashes, Pom p, Status s, String v) {
        var i = new Result();
        i.artifact = a;
        i.numCrashes = numCrashes;
        i.pom = p;
        i.status = s;
        i.createdWith = v;
        return i;
    }
}