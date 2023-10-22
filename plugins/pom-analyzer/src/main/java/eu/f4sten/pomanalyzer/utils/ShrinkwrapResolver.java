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
package eu.f4sten.pomanalyzer.utils;

import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.COMPILE;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.PROVIDED;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.RUNTIME;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.SYSTEM;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.TEST;

import java.io.File;
import java.lang.module.ResolutionException;
import java.util.HashSet;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.NoResolvedResultException;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenChecksumPolicy;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenUpdatePolicy;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenResolvedArtifactImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.maveneasyindex.Artifact;
import eu.f4sten.infra.utils.MavenRepositoryUtils;
import eu.f4sten.pomanalyzer.exceptions.MissingPomFileException;
import eu.f4sten.pomanalyzer.exceptions.NoArtifactRepositoryException;
import jakarta.inject.Inject;

public class ShrinkwrapResolver {

    // Attention: Be aware that the test suite for this class is disabled by default
    // to avoid unnecessary downloads on every build. Make sure to re-enable the
    // tests and run them locally for every change in this class.

    private static final String REPO_CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String REPO_CENTRAL_OLD = "https://repo1.maven.org/maven2/";
    private static final String REPO_CENTRAL_NON_HTTPS = "http://repo.maven.apache.org/maven2/";
    private static final String REPO_CENTRAL_OLD_NON_HTTPS = "http://repo1.maven.org/maven2/";

    // old http://download.java.net/maven/2/ is deprecated, see https://stackoverflow.com/a/22656393/3617482
    // private static final MavenRemoteRepository REPO_JAVA_NET = getRepo("java-net", "https://maven.java.net/content/groups/public/");

    // old http://bits.netbeans.org/maven2/ is deprecated, see https://netbeans.apache.org/about/oracle-transition.html
    // private static final MavenRemoteRepository REPO_NETBEANS = getRepo("netbeans", "https://netbeans.apidesign.org/maven2/");

    private static final Logger LOG = LoggerFactory.getLogger(ShrinkwrapResolver.class);

    private final MavenRepositoryUtils utils;

    @Inject
    public ShrinkwrapResolver(MavenRepositoryUtils utils) {
        this.utils = utils;
    }

    public Set<Artifact> resolveDependencies(Artifact a) {
        var deps = new HashSet<Artifact>();

        resolve(a).forEach(dep -> {
            // remember when repository could be identified
            if (dep.repository.startsWith("http")) {
                deps.add(dep);
                return;
            }

            LOG.error("Could not identify the artifact repository for dependency {}", dep);
            throw new NoArtifactRepositoryException(dep.toString());
        });

        return deps;
    }

    private Set<Artifact> resolve(Artifact a) {
        var localPomFile = getLocalPomFile(a);
        var deps = new HashSet<Artifact>();
        try {
            MavenResolvedArtifactImpl.deps = deps;

            var r = Maven.configureResolver() //
                    .withClassPathResolution(false) //
                    .withMavenCentralRepo(true);

            // TODO it should not be necessary to add additional repositories when starting from a pom
            // only add repo if it is different
//             r = addRepoIfNotMatching(r, getRepo(a.repository), REPO_CENTRAL, REPO_CENTRAL_OLD, REPO_CENTRAL_NON_HTTPS, REPO_CENTRAL_OLD_NON_HTTPS);

            // add replacements for popular repositories that were migrated
            // r = addRepoIfNotMatching(r, REPO_JAVA_NET, a.repository);
            // r = addRepoIfNotMatching(r, REPO_NETBEANS, a.repository);

            r //
                    .loadPomFromFile(localPomFile) //
                    .importDependencies(COMPILE, RUNTIME, PROVIDED, SYSTEM, TEST) //
                    .resolve() //
                    .withTransitivity() //
                    .asResolvedArtifact();
            MavenResolvedArtifactImpl.deps = null;
            return deps;
        } catch (IllegalArgumentException e) {
            // no dependencies are declared in pom
            return new HashSet<>();
        } catch (NoResolvedResultException e) {
            throw new ResolutionException(e);
        }
    }

    private File getLocalPomFile(Artifact a) {
        var localPomFile = utils.getLocalPomFile(a);
        if (!localPomFile.exists()) {
            var msg = String.format("Local .m2 folder does not contain a pom file for %s", a);
            LOG.error(msg);
            throw new MissingPomFileException(msg);
        }
        return localPomFile;
    }

    private static ConfigurableMavenResolverSystem addRepoIfNotMatching(ConfigurableMavenResolverSystem r, MavenRemoteRepository newRepo, String... repoUrls) {
        for (var repoUrl : repoUrls) {
            // handle all cases with or without final slash
            var repoUrlSlash = repoUrl + "/";
            var repoUrlNoSlash = repoUrl.substring(0, repoUrl.length() - 1);
            if (newRepo.getUrl().equals(repoUrl) || newRepo.getUrl().equals(repoUrlNoSlash) || newRepo.getUrl().equals(repoUrlSlash)) {
                return r;
            }
        }
        return r.withRemoteRepo(newRepo);
    }

    private static MavenRemoteRepository getRepo(String url) {
        var name = getRepoName(url);
        return getRepo(name, url);
    }

    private static MavenRemoteRepository getRepo(String name, String url) {
        return MavenRemoteRepositories //
                .createRemoteRepository(name, url, "default") //
                .setChecksumPolicy(MavenChecksumPolicy.CHECKSUM_POLICY_WARN) //
                .setUpdatePolicy(MavenUpdatePolicy.UPDATE_POLICY_NEVER);
    }

    private static String getRepoName(String url) {
        if (REPO_CENTRAL.equals(url)) {
            return "central";
        }
        return url.replaceAll("[^a-zA-Z0-9-]+", "");
    }
}