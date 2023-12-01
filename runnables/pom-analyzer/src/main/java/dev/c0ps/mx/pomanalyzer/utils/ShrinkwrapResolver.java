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
package dev.c0ps.mx.pomanalyzer.utils;

import static dev.c0ps.commons.Asserts.assertTrue;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.COMPILE;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.PROVIDED;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.RUNTIME;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.SYSTEM;
import static org.jboss.shrinkwrap.resolver.api.maven.ScopeType.TEST;

import java.io.File;
import java.lang.module.ResolutionException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.NoResolvedResultException;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenResolvedArtifactImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.pomanalyzer.exceptions.MissingPomFileException;
import dev.c0ps.mx.pomanalyzer.exceptions.NoArtifactRepositoryException;
import com.google.inject.Inject;

public class ShrinkwrapResolver {

    // Attention: Be aware that the test suite for this class is disabled by default
    // to avoid unnecessary downloads on every build. Make sure to re-enable the
    // tests and run them locally for every change in this class.

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

            LOG.error("Could not identify the repository for {}. Deleting pom to enforce re-download ...", dep);
            utils.getLocalPomFile(dep).delete();
            throw new NoArtifactRepositoryException(dep.toString());
        });

        return deps;
    }

    private Set<Artifact> resolve(Artifact a) {
        var localPomFile = getLocalPomFile(a);
        return resolveScopesRecursively(a, localPomFile, COMPILE, RUNTIME, TEST, PROVIDED, SYSTEM);
    }

    private Set<Artifact> resolveScopesRecursively(Artifact a, File localPomFile, ScopeType... scopes) {
        assertTrue(scopes.length > 0, "Resolution requires at least one scope");
        try {
            LOG.info("Resolving {} in scopes {} ...", a, Arrays.toString(scopes));
            return resolveScopes(localPomFile, scopes);
        } catch (NoResolvedResultException e) {
            LOG.error("Resolving {} failed for scopes {}", a, Arrays.toString(scopes));
            if (scopes.length > 1) {
                var scopes2 = Arrays.copyOfRange(scopes, 0, scopes.length - 1);
                return resolveScopesRecursively(a, localPomFile, scopes2);
            }
            LOG.error("No scopes left, giving up.");
            throw new ResolutionException(e);
        }
    }

    private Set<Artifact> resolveScopes(File localPomFile, ScopeType... scopes) {
        var deps = new HashSet<Artifact>();
        MavenResolvedArtifactImpl.deps = deps;
        try {
            Maven.configureResolver() //
                    .withClassPathResolution(false) //
                    .withMavenCentralRepo(true) //
                    .loadPomFromFile(localPomFile) //
                    .importDependencies(scopes) //
                    .resolve() //
                    .withTransitivity() //
                    .asResolvedArtifact();
        } catch (IllegalArgumentException e) {
            // no dependencies are declared in pom
            return new HashSet<>();
        } finally {
            MavenResolvedArtifactImpl.deps = null;
        }
        return deps;
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
}