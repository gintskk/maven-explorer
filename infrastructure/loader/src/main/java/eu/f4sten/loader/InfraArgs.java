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
package eu.f4sten.loader;

import java.io.File;

import org.apache.commons.lang3.SystemUtils;

import com.beust.jcommander.Parameter;

public class InfraArgs {

    @Parameter(names = "--baseDir", arity = 1, description = "Base folder for all file-based operations")
    public File baseDir;

    @Parameter(names = "--dir.lanes", arity = 1, description = "Folder for all marker files of lane-management")
    public File dirLanes;

    @Parameter(names = "--dir.m2", arity = 1, description = "Folder for the local .m2 folder (usually '«home»/.m2/')")
    public File dirM2 = new File(SystemUtils.getUserHome(), ".m2");

    @Parameter(names = "--kafka.url", arity = 1, description = "address for the Kafka Server")
    public String kafkaUrl;

    @Parameter(names = "--kafka.autoCommit", arity = 1, description = "should Kafka auto-commit after each poll")
    public boolean kafkaShouldAutoCommit = true;

    @Parameter(names = "--kafka.groupId", arity = 1, description = "optional id for Kafka consumer group")
    public String kafkaGroupId = null;

    @Parameter(names = "--instanceId", arity = 1, description = "uniquely identifies this application instance across re-starts")
    public String instanceId = null;

    @Parameter(names = "--http.port", arity = 1, description = "port used for http server")
    public int httpPort = 8080;

    @Parameter(names = "--http.baseUrl", arity = 1, description = "base url of http servlets")
    public String httpBaseUrl = "/";

    @Parameter(names = "--exec.timeoutMS", arity = 1, description = "timeout for a timed execution")
    public int execTimeoutMS = 1000 * 60 * 5; // 5min

    @Parameter(names = "--exec.delayMS", arity = 1, description = "execution delay in a timed execution")
    public int execDelayMS = 0;
}