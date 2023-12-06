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
package dev.c0ps.mx.examples;

import dev.c0ps.mx.infra.utils.Version;
import jakarta.inject.Inject;

public class PrintVersion implements Runnable {

    private Version version;

    @Inject
    public PrintVersion(Version version) {
        this.version = version;
    }

    @Override
    public void run() {
        System.out.printf("Server version: \n", version.get());
    }
}