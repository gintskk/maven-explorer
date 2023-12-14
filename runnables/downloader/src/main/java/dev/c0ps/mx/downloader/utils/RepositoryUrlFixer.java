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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryUrlFixer {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryUrlFixer.class);

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private static final String HTTP = "http:";
    private static final String HTTPS = "https:";

    private static final Map<String, String> REPLS = new HashMap<>();

    static {
        add(CENTRAL, CENTRAL);
        add("https://repo1.maven.org/maven2/", CENTRAL);
        add("https://repo1.maven.org/eclipse/", CENTRAL);

        add("https://download.java.net/maven/2/", "https://maven.java.net/content/groups/public/");
        add("https://bits.netbeans.org/maven2/", "https://netbeans.apidesign.org/maven2/");
    }

    private RepositoryUrlFixer() {
        // no instantiation
    }

    private static void add(String from, String to) {
        for (var from2 : trailingSlash(from)) {
            for (var from3 : https(from2)) {
                REPLS.put(from3, to);
            }
        }
    }

    private static Set<String> https(String url) {
        return Set.of(url, url.replace("https:", "http:"));
    }

    private static Set<String> trailingSlash(String url) {
        return Set.of(url, url.substring(0, url.length() - 1));
    }

    public static String fixRepoUrl(String orig) {
        if (REPLS.containsKey(orig)) {
            var newUrl = REPLS.get(orig);
            if (!orig.equals(newUrl)) {
                LOG.info("Updating repository url: {} -> {}", orig, newUrl);
            }
            return newUrl;
        }

        var url = orig;

        if (!url.endsWith("/")) {
            url += "/";
        }
        if (url.startsWith(HTTP)) {
            url = HTTPS + url.substring(HTTP.length());
        }
        if (!orig.equals(url)) {
            LOG.info("Updating repository url: {} -> {}", orig, url);
        }
        return url;
    }

}