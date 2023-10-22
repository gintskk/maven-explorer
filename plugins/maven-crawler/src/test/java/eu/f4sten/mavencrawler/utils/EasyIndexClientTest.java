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
package eu.f4sten.mavencrawler.utils;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
public class EasyIndexClientTest {

    // Attention: This test suite exists to facilitate local smoke testing.
    // Run an instance of dev.c0ps.maven-easy-index and connect.

    private static final Logger LOG = LoggerFactory.getLogger(EasyIndexClientTest.class);
    private static final String LOCAL_URL = "http://localhost:1234";

    @Test
    public void runEasyIndexClient() {
        var client = new EasyIndexClient(LOCAL_URL);
        var deps = client.get(456);
        LOG.info("{}", deps);
    }

    @Test
    public void reminderToReactivateTheDisabledAnnotation() {
        fail();
    }
}