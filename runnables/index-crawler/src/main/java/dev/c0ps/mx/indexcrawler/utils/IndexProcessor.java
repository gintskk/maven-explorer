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
package dev.c0ps.mx.indexcrawler.utils;

import static dev.c0ps.franz.Lane.NORMAL;
import static dev.c0ps.mx.infra.utils.MavenRepositoryUtils.toGAV;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.franz.Kafka;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class IndexProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(IndexProcessor.class);

    private final LocalStore store;
    private final EasyIndexClient utils;
    private final Kafka kafka;
    private final String kafkaTopicOut;

    @Inject
    public IndexProcessor(LocalStore store, EasyIndexClient utils, Kafka kafka, //
            @Named("kafka.topic.requested") String kafkaTopicOut) {
        this.store = store;
        this.utils = utils;
        this.kafka = kafka;
        this.kafkaTopicOut = kafkaTopicOut;
    }

    public void tryProcessingNextIndices() {
        var nextIdx = store.getNextIndex();
        while (utils.exists(nextIdx)) {
            LOG.info("Processing index {} ...", nextIdx);
            process(nextIdx);
            store.finish(nextIdx);
            nextIdx++;
        }
        LOG.info("Index {} cannot be found", nextIdx);
    }

    private void process(int idx) {
        var artifacts = utils.get(idx);
        LOG.info("Publishing {} coordinates ...", artifacts.size());
        for (var ma : artifacts) {
            LOG.debug("Publishing: {}", ma);
            var gav = toGAV(ma);
            // use GAV as key to eliminate parallel/duplicate processing in multiple workers
            kafka.publish(gav, ma, kafkaTopicOut, NORMAL);
        }
    }
}