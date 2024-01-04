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

import static dev.c0ps.test.TestLoggerUtils.assertLogsContain;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.c0ps.test.TestLoggerUtils;

public class RepositoryUrlFixerTest {

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String JAVA_NET = "https://maven.java.net/content/groups/public/";
    private static final String NETBEANS = "https://netbeans.apidesign.org/maven2/";

    @Test
    public void asd() {
        nofix(CENTRAL);

        fix("https://repo.maven.apache.org/maven2", CENTRAL);
        fix("http://repo.maven.apache.org/maven2/", CENTRAL);
        fix("http://repo.maven.apache.org/maven2", CENTRAL);

        fix("https://repo1.maven.org/maven2/", CENTRAL);
        fix("http://repo1.maven.org/maven2/", CENTRAL);
        fix("https://repo1.maven.org/maven2", CENTRAL);
        fix("http://repo1.maven.org/maven2", CENTRAL);

        fix("https://repo1.maven.org/eclipse/", CENTRAL);
        fix("http://repo1.maven.org/eclipse/", CENTRAL);
        fix("https://repo1.maven.org/eclipse", CENTRAL);
        fix("http://repo1.maven.org/eclipse", CENTRAL);

        fix("https://download.java.net/maven/2/", JAVA_NET);
        fix("http://download.java.net/maven/2/", JAVA_NET);
        fix("https://download.java.net/maven/2", JAVA_NET);
        fix("http://download.java.net/maven/2", JAVA_NET);

        fix("https://bits.netbeans.org/maven2/", NETBEANS);
        fix("http://bits.netbeans.org/maven2/", NETBEANS);
        fix("https://bits.netbeans.org/maven2", NETBEANS);
        fix("http://bits.netbeans.org/maven2", NETBEANS);

        var abc = "https://a.b/c/";
        nofix(abc);
        fix("http://a.b/c/", abc);
        fix("https://a.b/c", abc);
        fix("http://a.b/c", abc);
    }

    private static void nofix(String expected) {
        TestLoggerUtils.clearLog();
        var actual = RepositoryUrlFixer.fixRepoUrl(expected);
        assertEquals(expected, actual);
        assertLogSize(0);
    }

    private static void fix(String url, String expected) {
        TestLoggerUtils.clearLog();
        var actual = RepositoryUrlFixer.fixRepoUrl(url);
        assertEquals(expected, actual);

        assertLogSize(1);
        assertLogContains("INFO Updating repository url: %s -> %s", url, expected);
    }

    private static void assertLogSize(int expected) {
        var log = TestLoggerUtils.getFormattedLogs(RepositoryUrlFixer.class);
        assertEquals(expected, log.size());
    }

    private static void assertLogContains(String msg, Object... args) {
        assertLogsContain(RepositoryUrlFixer.class, msg, args);
    }
}