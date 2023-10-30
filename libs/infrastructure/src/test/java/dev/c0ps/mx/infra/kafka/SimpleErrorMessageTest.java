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
package dev.c0ps.mx.infra.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SimpleErrorMessageTest {

    private static final String NL = System.lineSeparator();

    @Test
    public void equalityDefault() {
        var a = new SimpleErrorMessage<Object>();
        var b = new SimpleErrorMessage<Object>();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityWithValues() {
        var a = someErrorMessage();
        var b = someErrorMessage();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffObj() {
        var a = someErrorMessage();
        var b = someErrorMessage();
        b.obj = "X";
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffStacktrace() {
        var a = someErrorMessage();
        var b = someErrorMessage();
        b.stacktrace = "X";
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void getterWorks() {
        var t = new RuntimeException();
        var m = SimpleErrorMessage.get("X", t);

        assertEquals("X", m.obj);
        assertTrue(m.stacktrace.startsWith("java.lang.RuntimeException" + NL + //
                "\tat dev.c0ps.mx.infra.kafka.SimpleErrorMessageTest.getterWorks(SimpleErrorMessageTest.java"), "Unexpected: " + m.stacktrace);
    }

    @Test
    public void getterWorksWithoutThrowable() {
        var m = SimpleErrorMessage.get("X");
        assertEquals("X", m.obj);
        assertNull(m.stacktrace);
    }

    @Test
    public void hasToString() {
        var m = new SimpleErrorMessage<String>();
        m.obj = "o";
        m.stacktrace = "s";
        var actual = m.toString();
        assertTrue(actual.startsWith(SimpleErrorMessage.class.getName() + "@"));
        assertTrue(actual.endsWith("[" + NL + "  obj=o" + NL + "  stacktrace=s" + NL + "]"));
    }

    private static SimpleErrorMessage<String> someErrorMessage() {
        return new SimpleErrorMessage<String>("some obj", "some stacktrace");
    }
}