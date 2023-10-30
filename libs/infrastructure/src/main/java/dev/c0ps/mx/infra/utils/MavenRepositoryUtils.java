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
package dev.c0ps.mx.infra.utils;

import static jakarta.ws.rs.core.Response.Status.FOUND;
import static jakarta.ws.rs.core.Response.Status.MOVED_PERMANENTLY;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PERMANENT_REDIRECT;
import static jakarta.ws.rs.core.Response.Status.SEE_OTHER;
import static jakarta.ws.rs.core.Response.Status.TEMPORARY_REDIRECT;
import static java.util.Locale.ENGLISH;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.commons.Asserts;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maveneasyindex.Artifact;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class MavenRepositoryUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryUtils.class);
    private static final String[] ALLOWED_CLASSIFIERS = new String[] { null, "sources" };

    private File dirM2;

    @Inject
    public MavenRepositoryUtils(@Named("dir.m2") File dirM2) {
        this.dirM2 = dirM2;
    }

    public File getLocalPomFile(Artifact a) {
        var pom = a.clone();
        pom.packaging = "pom";
        return getMavenFilePath(new File(dirM2, "repository"), pom);
    }

    public static String getUrl(Pom r, String classifier) {
        return getUrl(r.artifactRepository, r.groupId, r.artifactId, r.version, classifier, r.packagingType);
    }

    public static UrlCheck checkGetRequest(Artifact a, String classifier) {
        var url = getUrl(a.repository, a.groupId, a.artifactId, a.version, classifier, a.packaging);
        return checkGetRequest(url);
    }

    // TODO get rid of static alternative
    public UrlCheck checkGetRequestNonStatic(Artifact a) {
        return checkGetRequest(a);
    }

    @Deprecated
    public static UrlCheck checkGetRequest(Artifact a) {
        return checkGetRequest(a, null);
    }

    private static String getUrl(String repo, String gid, String aid, String version, String classifier, String pkg) {
        if (isNullEmptyOrUnset(repo) || isNullEmptyOrUnset(gid) || isNullEmptyOrUnset(aid) || isNullEmptyOrUnset(pkg) || isNullEmptyOrUnset(version)) {
            throw new IllegalArgumentException("cannot build sources URL with missing package information");
        }
        Asserts.assertContains(ALLOWED_CLASSIFIERS, classifier);
        var classifierStr = classifier != null ? "-" + classifier : "";
        var ar = repo;
        if (!ar.endsWith("/")) {
            ar += "/";
        }
        var url = ar + gid.replace('.', '/') + "/" + aid + "/" + version + "/" + aid + "-" + version + classifierStr + "." + pkg;
        return url;
    }

    public static UrlCheck checkGetRequest(String url) {
        try {
            var httpClient = HttpClient.newBuilder() //
                    .version(HttpClient.Version.HTTP_2) //
                    .connectTimeout(Duration.ofSeconds(10)) //
                    .followRedirects(Redirect.NEVER) // do it manually instead
                    .build();
            var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            var statusCode = response.statusCode();
            var lastModified = getDateOrNull(response, "last-modified", "Last-Modified");

            if (statusCode == OK.getStatusCode()) {
                return new UrlCheck(url, lastModified);
            }

            for (var scMoved : Set.of(MOVED_PERMANENTLY, FOUND, SEE_OTHER, TEMPORARY_REDIRECT, PERMANENT_REDIRECT)) {
                if (statusCode == scMoved.getStatusCode()) {
                    var newLocation = getField(response, "Location", "location");
                    if (newLocation.isPresent()) {
                        return checkGetRequest(newLocation.get());
                    }
                }
            }

            return new UrlCheck(null, null);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> getField(HttpResponse<Void> response, String... keys) {
        for (String key : keys) {
            var val = response.headers().firstValue(key);
            if (val.isPresent()) {
                return val;
            }
        }
        return Optional.empty();
    }

    private static Date getDateOrNull(HttpResponse<Void> response, String... keys) {
        var headers = response.headers();
        for (var key : keys) {
            var val = headers.firstValue(key);
            if (val.isPresent()) {
                try {
                    var pattern = "E, d MMM yyyy HH:mm:ss Z";
                    var lastModified = new SimpleDateFormat(pattern, ENGLISH).parse(val.get());
                    return lastModified;
                } catch (ParseException e) {
                    LOG.warn("Could not parse release date: {}\n", val.get());
                }
            }
        }
        return null;
    }

    private static boolean isNullEmptyOrUnset(String s) {
        return s == null || s.isEmpty() || "?".equals(s);
    }

    public static File getMavenFilePath(File baseDir, Artifact a) {
        return getMavenFilePath(baseDir, a, a.packaging);
    }

    public File getMavenFilePathNonStatic(File baseDir, Artifact a, String extension) {
        var f = baseDir;
        for (var part : a.groupId.split("\\.")) {
            f = new File(f, part);
        }
        f = new File(f, a.artifactId);
        f = new File(f, a.version);
        f = new File(f, a.artifactId + "-" + a.version + "." + extension);
        return f;
    }

    public File getMavenFilePathNonStatic(File baseDir, Artifact a) {
        return getMavenFilePathNonStatic(baseDir, a);
    }

    @Deprecated
    public static File getMavenFilePath(File baseDir, Artifact a, String extension) {
        return new MavenRepositoryUtils(null).getMavenFilePathNonStatic(baseDir, a, extension);
    }

    @Deprecated
    public static String getMavenRelativeUrlPath(Artifact a) {
        return getMavenUrlPath(a, a.packaging);
    }

    public static String getMavenUrlPath(Artifact a, String extension) {

        var parts = new ArrayList<String>();
        for (var part : a.groupId.split("\\.")) {
            parts.add(part);
        }
        parts.add(a.artifactId);
        parts.add(a.version);
        parts.add(a.artifactId + "-" + a.version + "." + extension);

        return StringUtils.join(parts, "/");
    }

    public boolean doesExist(Pom r) {
        var url = getUrl(r, null);
        return checkGetRequest(url).url != null;
    }

    public static Artifact toArtifact(Pom pom) {
        return new Artifact(pom.groupId, pom.artifactId, pom.version, pom.packagingType) //
                .setReleaseDate(pom.releaseDate) //
                .setRepository(pom.artifactRepository);
    }

    public static class UrlCheck {
        public final String url;
        public final Date lastModified;

        public UrlCheck(String url, Date lastModified) {
            this.url = url;
            this.lastModified = lastModified;
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return EqualsBuilder.reflectionEquals(this, obj);
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
        }
    }
}