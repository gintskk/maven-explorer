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

import static java.lang.String.format;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.c0ps.mx.infra.exceptions.ExecutionTimeoutError;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class TimedExecutor {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private final int timeoutMS;
    private final int delayMS;

    @Inject
    public TimedExecutor(@Named("TimedExecutor.timeoutMS") int timeoutMS, @Named("TimedExecutor.delayMS") int delayMS) {
        this.timeoutMS = timeoutMS;
        this.delayMS = delayMS;
    }

    public void run(Object execId, Runnable r) {

        var future = EXEC.submit(r);

        try {
            future.get(timeoutMS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            var msg = "Execution timeout after %dms: %s";
            throw new ExecutionTimeoutError(format(msg, timeoutMS, execId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException(cause);
            }
        }

        delayExecution();
    }

    public void delayExecution() {
        try {
            Thread.sleep(delayMS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}