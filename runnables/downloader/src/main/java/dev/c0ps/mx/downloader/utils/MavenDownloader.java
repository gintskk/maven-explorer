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

import static java.lang.String.join;

import java.io.File;
import java.lang.module.ResolutionException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.maveneasyindex.Artifact;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class MavenDownloader {

    // Attention: Be aware that the test suite for this class is disabled by default
    // to avoid unnecessary downloads on every build. Make sure to re-enable the
    // tests and run them locally for every change in this class.

    public static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private static final Logger LOG = LoggerFactory.getLogger(MavenDownloader.class);

    private final File baseDir;
    private final File dirM2;
    private final File mavenHome;

    @Inject
    public MavenDownloader( //
            @Named("dir.base") File baseDir, //
            @Named("dir.m2") File dirM2, //
            @Named("dir.mavenHome") File mavenHome) {

        this.baseDir = baseDir;
        this.dirM2 = dirM2;
        this.mavenHome = mavenHome;
    }

    public void download(Artifact a) {
        var coord = String.format("%s:%s:%s:%s", a.groupId, a.artifactId, a.version, a.packaging);
        download(coord, a.repository, true);

        for (var classifier : Set.of("sources", "javadoc")) {
            try {
                var sources = String.format("%s:%s:%s:jar:%s", a.groupId, a.artifactId, a.version, classifier);
                download(sources, a.repository, false);
            } catch (ResolutionException e) {
                // ignore exceptions for classifier jars
            }
        }
    }

    private void download(String coordinate, String repository, boolean includeTransitiveDeps) {
        var repositories = unique(CENTRAL, repository);
        LOG.info("Downloading coordinate '{}' from repositories [{}] ...", coordinate, join(", ", repositories));

        try {

            var props = new Properties();
            props.setProperty("artifact", coordinate);
            props.setProperty("remoteRepositories", join(",", repositories));
            props.setProperty("transitive", Boolean.toString(includeTransitiveDeps));

            var req = new DefaultInvocationRequest();
            req.setOutputHandler(new PrintStreamHandler(System.out, true));
            req.setGoals(List.of("dependency:get"));
            req.setProperties(props);
            req.setBatchMode(true);

            var invoker = new DefaultInvoker();
            invoker.setWorkingDirectory(baseDir);
            invoker.setMavenHome(mavenHome);
            invoker.setLocalRepositoryDirectory(new File(dirM2, "repository"));

            var res = invoker.execute(req);

            if (res.getExitCode() != 0) {
                throw new ResolutionException(res.getExecutionException());
            }

        } catch (MavenInvocationException e) {
            throw new ResolutionException(e);
        }
    }

    private static Set<String> unique(String... repos) {
        var uniqueRepos = new HashSet<String>();
        for (var repo : repos) {
            uniqueRepos.add(repo);
        }
        return uniqueRepos;
    }
}