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
package dev.c0ps.mx.runner;

import java.io.File;

import org.apache.commons.lang3.SystemUtils;

import com.beust.jcommander.Parameter;

import dev.c0ps.mx.infra.kafka.DefaultTopics;

public class InfraArgs {

    // kafka

    @Parameter(names = "--kafka.url", arity = 1, description = "address for the Kafka Server")
    public String kafkaUrl;

    @Parameter(names = "--kafka.autoCommit", arity = 1, description = "should Kafka auto-commit after each poll")
    public boolean kafkaShouldAutoCommit = true;

    @Parameter(names = "--kafka.groupId", arity = 1, description = "optional id for Kafka consumer group")
    public String kafkaGroupId = null;

    @Parameter(names = "--instanceId", arity = 1, description = "uniquely identifies this application instance across re-starts")
    public String instanceId = null;

    // kafka.topics

    @Parameter(names = "--kafka.topic.requested", arity = 1)
    public String kafkaTopicRequested = DefaultTopics.REQUESTED;

    @Parameter(names = "--kafka.topic.downloaded", arity = 1)
    public String kafkaTopicDownloaded = DefaultTopics.DOWNLOADED;

    @Parameter(names = "--kafka.topic.analyzed", arity = 1)
    public String kafkaTopicAnalyzed = DefaultTopics.ANALYZED;

    // libhttpd

    @Parameter(names = "--http.port", arity = 1, description = "port used for http server")
    public int httpPort = 8080;

    @Parameter(names = "--http.baseUrl", arity = 1, description = "base url of http servlets")
    public String httpBaseUrl = "/";

    // timed executor

    @Parameter(names = "--exec.timeoutMS", arity = 1, description = "timeout for a timed execution")
    public int execTimeoutMS = 1000 * 60 * 5; // 5min

    @Parameter(names = "--exec.delayMS", arity = 1, description = "execution delay in a timed execution")
    public int execDelayMS = 0;

    // directories

    @Parameter(names = "--dir.base", arity = 1, description = "Working dir, base directory for all file-based operations")
    public File dirBase;

    @Parameter(names = "--dir.m2", arity = 1, description = "Folder for the local .m2 folder (usually '«home»/.m2/')")
    public File dirM2 = new File(SystemUtils.getUserHome(), ".m2");

    @Parameter(names = "--dir.lanes", arity = 1, description = "Folder for all marker files of lane-management")
    public File dirLanes;

    @Parameter(names = "--dir.completion", arity = 1, description = "Folder for all marker files of the completion progress")
    public File dirCompletion;

    @Parameter(names = "--dir.results", arity = 1, description = "Folder for all (partial) results")
    public File dirResults;

    @Parameter(names = "--dir.mavenHome", arity = 1)
    public File dirMavenHome = new File("/usr/share/maven/");
}