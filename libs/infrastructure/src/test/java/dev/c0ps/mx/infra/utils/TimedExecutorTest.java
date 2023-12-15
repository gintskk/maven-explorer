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
package dev.c0ps.mx.infra.utils;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import dev.c0ps.mx.infra.exceptions.ExecutionTimeoutException;
import dev.c0ps.mx.infra.exceptions.UnrecoverableError;

public class TimedExecutorTest {

    @Test
    public void waitingDoesNotBlock() {
        var sut = new TimedExecutor(10, 0);

        var max = IS_OS_WINDOWS ? 1000 : 50;
        assertMaxDuration(max, () -> {
            sut.run("...", takes(1));
        });
    }

    @Test
    public void errorAfterTimeoutHits() {
        var sut = new TimedExecutor(10, 0);

        var max = IS_OS_WINDOWS ? 1000 : 50;
        assertMaxDuration(max, () -> {
            var e = assertThrows(ExecutionTimeoutException.class, () -> {
                sut.run("XYZ", takes(100));
            });
            assertEquals("Execution timeout after 10ms: XYZ", e.getMessage());
        });
    }

    @Test
    public void delayIsAdded() {
        var sut = new TimedExecutor(1000, 100);

        var max = IS_OS_WINDOWS ? 1000 : 200;
        assertMinMaxDuration(100, max, () -> {
            sut.run("...", takes(1));
        });
    }

    @Test
    public void delayCanBeAddedManually() {
        if (SystemUtils.IS_OS_WINDOWS) {
            // unreliable execution
            return;
        }
        var sut = new TimedExecutor(1000, 100);

        var max = IS_OS_WINDOWS ? 1000 : 200;
        assertMinMaxDuration(100, max, () -> {
            sut.delayExecution();
        });
    }

    @Test
    public void errorsArePropagated_Error() {
        var sut = new TimedExecutor(1000, 100);

        assertThrows(UnrecoverableError.class, () -> {
            sut.run("...", () -> {
                throw new UnrecoverableError();
            });
        });
    }

    @Test
    public void errorsArePropagated_RuntimeException() {
        var sut = new TimedExecutor(1000, 100);

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            sut.run("...", () -> {
                throw new ArrayIndexOutOfBoundsException();
            });
        });
    }

    @Test
    public void interruptsDoNotLeadToCrash() {
        var sut = new TimedExecutor(1000, 100);
        sut.run("...", () -> {
            Thread.currentThread().interrupt();
        });
    }

    private static void assertMaxDuration(int limitMS, Runnable r) {
        assertMinMaxDuration(0, limitMS, r);
    }

    private static void assertMinMaxDuration(int minLimitMS, int maxLimitMS, Runnable r) {
        var start = System.currentTimeMillis();
        r.run();
        var end = System.currentTimeMillis();
        var durationMS = end - start;
        assertTrue(durationMS >= minLimitMS, String.format("Execution was too fast. Min limit was %dms, but took %dms.", minLimitMS, durationMS));
        assertTrue(durationMS <= maxLimitMS, String.format("Execution took too long. Max limit was %dms, but took %dms.", maxLimitMS, durationMS));
    }

    private static Runnable takes(int i) {
        return () -> {
            try {
                Thread.sleep(i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}