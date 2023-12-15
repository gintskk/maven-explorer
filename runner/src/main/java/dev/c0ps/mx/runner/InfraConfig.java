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

import static dev.c0ps.diapper.AssertArgs.assertFor;
import static dev.c0ps.diapper.AssertArgs.notNullAndNotEmpty;

import java.io.File;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

import dev.c0ps.diapper.AssertArgs;
import dev.c0ps.diapper.IInjectorConfig;
import dev.c0ps.diapper.InjectorConfig;
import dev.c0ps.diapper.RunnerArgs;
import dev.c0ps.franz.Kafka;
import dev.c0ps.franz.KafkaConnector;
import dev.c0ps.franz.KafkaGracefulShutdownThread;
import dev.c0ps.franz.KafkaImpl;
import dev.c0ps.io.IoUtils;
import dev.c0ps.io.IoUtilsImpl;
import dev.c0ps.io.JsonUtils;
import dev.c0ps.io.JsonUtilsImpl;
import dev.c0ps.io.ObjectMapperBuilder;
import dev.c0ps.libhttpd.HttpServer;
import dev.c0ps.libhttpd.HttpServerGracefulShutdownThread;
import dev.c0ps.libhttpd.HttpServerImpl;
import dev.c0ps.maven.json.CommonsMavenDataModule;
import dev.c0ps.maveneasyindex.ArtifactModule;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.utils.HostName;
import dev.c0ps.mx.infra.utils.Version;
import dev.c0ps.mx.runner.impl.kafka.FileBasedLaneManagement;
import dev.c0ps.mx.runner.impl.utils.HostNameImpl;
import dev.c0ps.mx.runner.impl.utils.VersionImpl;
import jakarta.inject.Named;

@InjectorConfig
public class InfraConfig implements IInjectorConfig {

    private static final Logger LOG = LoggerFactory.getLogger(InfraConfig.class);

    private final InfraArgs args;

    public InfraConfig(InfraArgs args) {
        this.args = args;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(InfraArgs.class).toInstance(args);
        binder.bind(HostName.class).to(HostNameImpl.class);
        binder.bind(Version.class).to(VersionImpl.class);
    }

    @Provides
    public HttpServer bindHttpServer(Injector injector) {
        var server = new HttpServerImpl(injector, args.httpPort, args.httpBaseUrl);
        Runtime.getRuntime().addShutdownHook(new HttpServerGracefulShutdownThread(server));
        return server;
    }

    @Provides
    public IoUtils bindIoUtils(JsonUtils jsonUtils, ObjectMapper om) {
        assertFor(args) //
                .notNull(args -> args.dirBase, "base dir") //
                .that(args -> args.dirBase.exists(), "base dir does not exist");
        return new IoUtilsImpl(args.dirBase, jsonUtils, om);
    }

    @Provides
    @Singleton
    public KafkaConnector bindKafkaConnector(RunnerArgs runnerArgs) {
        assertFor(args) //
                .notNull(a -> a.kafkaUrl, "kafka url") //
                .that(a -> a.instanceId == null || !a.instanceId.isEmpty(), "instance id must be null or non-empty") //
                .that(a -> a.kafkaGroupId == null || !a.kafkaGroupId.isEmpty(), "group id must be null or non-empty");

        var pluginId = runnerArgs.run.replace("dev.c0ps.mx.", "");
        pluginId = pluginId.endsWith(".Main") //
                ? pluginId.replace(".Main", "") //
                : pluginId;

        var serverUrl = args.kafkaUrl;
        var groupId = args.kafkaGroupId != null ? args.kafkaGroupId : pluginId;
        var instanceId = args.instanceId;
        return new KafkaConnector(serverUrl, groupId, instanceId);
    }

    @Provides
    @Singleton
    public Kafka bindKafka(JsonUtils jsonUtils, KafkaConnector connector) {
        var kafka = new KafkaImpl(jsonUtils, connector, args.kafkaShouldAutoCommit);
        Runtime.getRuntime().addShutdownHook(new KafkaGracefulShutdownThread(kafka));
        return kafka;
    }

    @Provides
    @Singleton
    public JsonUtils bindJsonUtils(ObjectMapper om) {
        return new JsonUtilsImpl(om);
    }

    @ProvidesIntoSet
    public Module provideCommonsMavenDataModule() {
        return new CommonsMavenDataModule();
    }

    @ProvidesIntoSet
    public Module provideArtifactModule() {
        return new ArtifactModule();
    }

    @Provides
    @Singleton
    public ObjectMapper bindObjectMapper(Set<Module> modules) {
        LOG.info("Instantiating ObjectMapper from {} modules: {}", modules.size(), modules);

        return new ObjectMapperBuilder().build() //
                .registerModules(modules);
    }

    @Provides
    @Named("TimedExecutor.timeoutMS")
    public int bindExecTimeout() {
        return args.execTimeoutMS;
    }

    @Provides
    @Named("TimedExecutor.delayMS")
    public int bindExecDelay() {
        return args.execDelayMS;
    }

    @Provides
    @Named("kafka.url")
    public String provideKafkaUrl() {
        return args.kafkaUrl;
    }

    @Provides
    @Named("kafka.topic.requested")
    public String provideKafkaTopicRequested() {
        notNullAndNotEmpty(args, a -> a.kafkaTopicRequested, "kafka.topic.requested");
        return args.kafkaTopicRequested;
    }

    @Provides
    @Named("kafka.topic.downloaded")
    public String provideKafkaTopicDownloaded() {
        notNullAndNotEmpty(args, a -> a.kafkaTopicDownloaded, "kafka.topic.downloaded");
        return args.kafkaTopicDownloaded;
    }

    @Provides
    @Named("kafka.topic.analyzed")
    public String provideKafkaTopicAnalyzed() {
        notNullAndNotEmpty(args, a -> a.kafkaTopicAnalyzed, "kafka.topic.analyzed");
        return args.kafkaTopicAnalyzed;
    }

    @Provides
    public LaneManagement provideLaneManagement() {
        AssertArgs.directoryExists(args, a -> a.dirLanes, "lane folder");
        return new FileBasedLaneManagement(args.dirLanes);
    }

    @Provides
    @Named("dir.base")
    public File bindDirBase() {
        AssertArgs.directoryExists(args, a -> a.dirBase, "base folder (aka. working dir)");
        return args.dirBase;
    }

    @Provides
    @Named("dir.completion")
    public File bindDirCompletion() {
        AssertArgs.directoryExists(args, a -> a.dirCompletion, "completion folder");
        return args.dirCompletion;
    }

    @Provides
    @Named("dir.m2")
    public File bindDirM2() {
        AssertArgs.directoryExists(args, a -> a.dirM2, ".m2 folder");
        return args.dirM2;
    }

    @Provides
    @Named("dir.mavenHome")
    public File provideDirMavenHome() {
        AssertArgs.directoryExists(args, a -> a.dirMavenHome, "mavenHome folder");
        return args.dirMavenHome;
    }

    @Provides
    @Named("dir.lanes")
    public File provideDirLanes() {
        AssertArgs.directoryExists(args, a -> a.dirLanes, "lane folder");
        return args.dirLanes;
    }

    @Provides
    @Named("dir.results")
    public File provideDirResults() {
        AssertArgs.directoryExists(args, a -> a.dirResults, "results folder");
        return args.dirResults;
    }
}