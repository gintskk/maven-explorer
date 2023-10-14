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
package eu.f4sten.mavendownloader;

import static dev.c0ps.diapper.AssertArgs.assertFor;

import java.io.File;

import com.google.inject.Provides;

import dev.c0ps.diapper.InjectorConfig;
import dev.c0ps.diapper.InjectorConfigBase;
import dev.c0ps.io.IoUtils;
import eu.f4sten.infra.utils.Version;
import eu.f4sten.mavendownloader.utils.FileBasedIngestionDatabase;
import eu.f4sten.mavendownloader.utils.IngestionDatabase;
import jakarta.inject.Named;

@InjectorConfig
public class MavenDownloaderInjectorConfig extends InjectorConfigBase {

    private MavenDownloaderArgs args;

    public MavenDownloaderInjectorConfig(MavenDownloaderArgs args) {
        this.args = args;
    }

    @Provides
    public MavenDownloaderArgs provideMavenCrawlerArgs() {
        return args;
    }

    @Provides
    public IngestionDatabase provideIngestionDatabase(Version v, IoUtils io) {
        assertFor(args) //
                .notNull(a -> a.dirIngestionDb, "ingestion db folder") //
                .that(a -> a.dirIngestionDb.exists(), "ingestion db folder does not exist");
        return new FileBasedIngestionDatabase(v, io, args.dirIngestionDb);
    }

    @Provides
    @Named("MavenDownloader.mavenHome")
    public File provideMavenHome() {
        assertFor(args) //
                .notNull(a -> a.mavenHome, "mavenHome") //
                .that(a -> a.mavenHome.exists(), "mavenHome does not exist");
        return args.mavenHome;
    }

    @Provides
    @Named("MavenDownloader.workingDir")
    public File provideWorkingDir() {
        assertFor(args) //
                .notNull(a -> a.workingDir, "workingDir") //
                .that(a -> a.workingDir.exists(), "workingDir does not exist");
        return args.workingDir;
    }
}