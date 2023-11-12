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

import static dev.c0ps.mx.infra.utils.MavenRepositoryUtils.toArtifact;
import static java.lang.String.format;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.maven.data.Pom;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.data.Status;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import jakarta.inject.Inject;

public class ArtifactFinder {

    // Attention: Be aware that the test suite for this class is disabled by default
    // to avoid unnecessary downloads on every build. Make sure to re-enable the
    // tests and run them locally for every change in this class.

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactFinder.class);

    private static final Pattern REGEX_PACKAGING = Pattern.compile(".*<\\s*packaging>\\s*([a-zA-Z0-9]+)\\s*<\\s*\\/packaging\\s*>.*", Pattern.DOTALL);

    private static final int ONE_HOUR_MS = 1000 * 60 * 60;
    private static final int ONE_DAY_MS = ONE_HOUR_MS * 24;

    private static final String HTTPS_CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String HTTP_CENTRAL = "http://repo.maven.apache.org/maven2/";
    private static final String HTTPS_CENTRAL_OLD = "https://repo1.maven.org/maven2/";
    private static final String HTTP_CENTRAL_OLD = "http://repo1.maven.org/maven2/";

    private static final String[] PACKAGING_TYPES = new String[] { "jar", "war", "ear", "aar", "ejb" };

    private final ResultsDatabase db;
    private final MavenRepositoryUtils utils;

    @Inject
    public ArtifactFinder(ResultsDatabase db, MavenRepositoryUtils utils) {
        this.db = db;
        this.utils = utils;

    }

    public Artifact findArtifact(Pom p) {
        return findArtifact(toArtifact(p));
    }

    public Artifact findArtifact(Artifact orig) {

        var oldA = wasFixedBefore(orig);
        if (oldA != null) {
            return oldA;
        }

        var a = findArtifactWithStrategies(orig);
        if (a == null) {
            LOG.error("Package cannot be found: {}", a);
            db.markNotFound(orig);
            return null;
        }

        if ("pom".equals(a.packaging)) {
            var nonPom = parsePackagingFromUrl(a);
            if (a.equals(nonPom)) {
                LOG.info("Confirmed packaging of {}", a);
            } else {
                LOG.info("Updating packaging for {} to {}", a, nonPom.packaging);
                var b = findArtifactWithStrategies(nonPom);
                if (b == null) {
                    LOG.error("Updated package cannot be found: {}", nonPom);
                } else {
                    a = b;
                }
            }
        }

        if (a != null) {
            LOG.error("Marking package as found: {}", a);
            db.markFound(a);
        }

        return a;
    }

    private Artifact wasFixedBefore(Artifact a) {
        var s = db.get(a);
        if (s != null && s.status != Status.REQUESTED) {
            return s.artifact;
        }
        return null;
    }

    private Artifact findArtifactWithStrategies(Artifact orig) {

        var a = cloneWithBasicFixes(orig);
        Artifact fix;

        if ((fix = exists(a)) != null) {
            LOG.info("Found: {}", fix);

            if ("pom".equals(fix.packaging)) {

            }

            return fix;
        }

        fix = tryFix(a, "changing packaging to lower case", tmp -> {
            tmp.packaging = tmp.packaging.toLowerCase();
        });
        if (fix != null) {
            return fix;
        }

        for (var pt : PACKAGING_TYPES) {
            if (pt.equals(a.packaging)) {
                continue;
            }
            var msg = format("fixing packaging from %s to %s", a, pt);
            fix = tryFix(a, msg, tmp -> {
                tmp.packaging = pt;
            });
            if (fix != null) {
                return fix;
            }
        }

        fix = tryProtocolReplace(a, "http://", "https://");
        if (fix != null) {
            return fix;
        }

        fix = tryProtocolReplace(a, "https://", "http://");
        if (fix != null) {
            return fix;
        }

        // not found
        return null;
    }

    private Artifact tryProtocolReplace(Artifact a, String from, String to) {
        if (a.repository.startsWith(from)) {
            return tryFix(a, "changing protocol to " + to, tmp -> {
                tmp.repository = to + tmp.repository.substring(from.length());
            });
        }
        return null;
    }

    private static Artifact cloneWithBasicFixes(Artifact orig) {
        var a = orig.clone();

        // basic fixes
        ensureRepositoryEndWithSlash(a);
        replaceOldReferencesToMavenCentral(a);
        if (orig.equals(a)) {
            a = orig;
        }
        return a;
    }

    private static void ensureRepositoryEndWithSlash(Artifact a) {
        if (!a.repository.endsWith("/")) {
            a.repository += "/";
        }
    }

    private static void replaceOldReferencesToMavenCentral(Artifact a) {
        if (HTTP_CENTRAL.equals(a.repository) //
                || HTTP_CENTRAL_OLD.equals(a.repository) //
                || HTTPS_CENTRAL_OLD.equals(a.repository)) {
            LOG.info("Updating old Maven Central reference: {}", a.repository);
            a.repository = HTTPS_CENTRAL;
        }
    }

    private Artifact tryFix(Artifact a, String msg, Consumer<Artifact> fixer) {
        var tmp = a.clone();
        fixer.accept(tmp);
        Artifact fix;
        if ((fix = exists(tmp)) != null) {
            LOG.info("Found artifact after: {}", msg);
            return fix;
        }
        return null;
    }

    private Artifact exists(Artifact a) {
        var res = utils.checkGetRequestNonStatic(a);
        if (res.url == null) {
            return null;
        }

        // check if base url has changed (after following forwards)
        var part = a.groupId.replace('.', '/') + '/' + a.artifactId + '/' + a.version + '/';
        var idx = res.url.indexOf(part);
        var newUrl = res.url.substring(0, idx);
        if (!newUrl.equals(a.repository)) {
            LOG.info("Fixed repository URL: {} --> {}", a.repository, newUrl);
            // clone before changing
            a = a.clone();
            a.repository = newUrl;
        }

        // check if releaseDate needs updating
        if (res.lastModified != null) {
            var lastModified = res.lastModified.getTime();

            if (lastModified != a.releaseDate) {
                var diff = Math.abs(lastModified - a.releaseDate);
                if (!a.hasReleaseDate()) {
                    LOG.info("Recovered release time for {}, last modified is {}", a, lastModified);
                    // clone before changing
                    a = a.clone();
                    a.releaseDate = lastModified;
                } else if (diff < ONE_DAY_MS) {
                    var diffStr = new DecimalFormat("#.##").format(diff / (double) ONE_HOUR_MS);
                    LOG.info("Replacing deviating release time for {}, last modified is {} ({}h)", a, lastModified, diffStr);
                    // clone before changing
                    a = a.clone();
                    a.releaseDate = lastModified;
                } else {
                    var diffStr = new DecimalFormat("#.##").format(diff / (double) ONE_DAY_MS);
                    LOG.info("Ignoring large release time deviation for {}, last modified is {} ({}d)", a, lastModified, diffStr);
                }
            }
        }
        return a;
    }

    private Artifact parsePackagingFromUrl(Artifact a) {

        var url = utils.getUrl(a);
        var packaging = parsePackagingFromUrl(url);

        if (packaging.equals(a.packaging)) {
            return a;
        } else {
            var clone = a.clone();
            clone.packaging = packaging;
            return clone;
        }
    }

    public static String parsePackagingFromUrl(URL url) {
        try {
            var data = IOUtils.toString(url, StandardCharsets.UTF_8);
            var matcher = REGEX_PACKAGING.matcher(data);
            return matcher.matches() //
                    ? matcher.group(1)
                    : "jar";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}