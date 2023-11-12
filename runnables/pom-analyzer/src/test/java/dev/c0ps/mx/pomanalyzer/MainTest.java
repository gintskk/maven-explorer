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
package dev.c0ps.mx.pomanalyzer;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.c0ps.franz.Kafka;
import dev.c0ps.maven.PomExtractor;
import dev.c0ps.mx.downloader.utils.CompletionTracker;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.infra.kafka.LaneManagement;
import dev.c0ps.mx.infra.utils.MavenRepositoryUtils;
import dev.c0ps.mx.infra.utils.TimedExecutor;
import dev.c0ps.mx.pomanalyzer.utils.EffectiveModelBuilder;
import dev.c0ps.mx.pomanalyzer.utils.ShrinkwrapResolver;

public class MainTest {

    private EffectiveModelBuilder modelBuilder;
    private PomExtractor extractor;
    private ShrinkwrapResolver resolver;
    private Kafka kafka;
    private TimedExecutor exec;
    private ResultsDatabase db;
    private LaneManagement lm;
    private MavenRepositoryUtils mru;
    private CompletionTracker tracker;

    private Main sut;

    @BeforeEach
    public void setup() {
        modelBuilder = mock(EffectiveModelBuilder.class);
        extractor = mock(PomExtractor.class);
        resolver = mock(ShrinkwrapResolver.class);
        kafka = mock(Kafka.class);
        exec = mock(TimedExecutor.class);
        db = mock(ResultsDatabase.class);
        lm = mock(LaneManagement.class);
        mru = mock(MavenRepositoryUtils.class);
        tracker = mock(CompletionTracker.class);

        sut = new Main(modelBuilder, extractor, resolver, kafka, exec, db, lm, mru, tracker, "in", "out", "requests");

//        when(extractor.process(eq(null))).thenReturn(new Pom());
//        when(extractor.process(any(Model.class))).thenReturn(new Pom());
//        when(fixer.checkPackage(any(Pom.class))).thenReturn("jar");
    }

    @Test
    public void basicSmokeTest() {
        sut.hashCode();
        // sut.consume("{\"groupId\":\"log4j\",\"artifactId\":\"log4j\",\"version\":\"1.2.17\"}",
        // NORMAL);
    }

    // TODO extend test suite, right now this is only a stub for easy debugging
}