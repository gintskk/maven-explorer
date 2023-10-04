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
package eu.f4sten.mavendownloader.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.lang.module.ResolutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.c0ps.maveneasyindex.Artifact;

@Disabled
public class MavenDownloaderTest {

    // Attention: This test suite is expensive to run and the Maven Home directory
    // is system-specific. Use this test suite only for local debugging.

    public File dirMavenHome = new File("/opt/local/share/java/maven3/");

    @TempDir
    public File dirBase;

    // use a dedicated .m2 folder (instead of a dynamic one) to speed up local testing
    @TempDir
    public File dirM2;

    private MavenDownloader sut;

    @BeforeEach
    public void setup() {
        new File(dirM2, "repository").mkdirs();
        sut = new MavenDownloader(dirBase, dirM2, dirMavenHome);
    }

    @Test
    public void reminderToReenableDisabledAnnotationOnClass() {
        fail("Suite is expensive and should only be run locally. Re-enable @Disabled annotation.");
    }

    @Test
    public void nonExisting() {
        assertThrows(ResolutionException.class, () -> {
            sut.download(central("non.existing", "artifact", "1.2.3"));
        });
    }

    @Test
    public void existing() {
        sut.download(central("junit", "junit", "4.12"));
    }

    private static Artifact central(String g, String a, String v) {
        return new Artifact(g, a, v, "jar").setRepository(MavenDownloader.CENTRAL);
    }
}