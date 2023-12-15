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
package dev.c0ps.mx.depgraph;

import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;

import dev.c0ps.franz.Lane;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/kafka-stats")
public class KafkaStatsService {

    private AdminClient ac;
    private StringBuilder html;

    @Inject
    public KafkaStatsService(@Named("kafka.url") String kafkaUrl) {
        var ps = new Properties();
        ps.setProperty(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        ac = AdminClient.create(ps);
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public String getKafkaStats() throws InterruptedException, ExecutionException {

        ac.listTopics().namesToListings().get().forEach((name, tl) -> {
            var td = getTopicDescription(name);
            var total = 0L;
            for (var p : td.partitions()) {
                var pid = p.partition();
                var listOffsetsResultInfo = getOffsets(name, pid);
                var size = listOffsetsResultInfo.offset();
                total += size;
            }
            register(name, total);
        });

        html = new StringBuilder();
        html.append("<html><head></head><style>* { font-family: Arial; font-size: 10pt; } h1 {font-size: 14pt} .h { font-weight: bold; }</style><body>");

        h1("Kafka Statistics");

        html.append("<table><tr class=\"h\">");

        th("date");
        th("timestampS");
        counts.forEach((topic, x) -> {
            th(topic + ".ok");
            th(topic + ".err");
        });
        html.append("</tr><tr>");

        var d = new Date();
        td(d);
        td(d.getTime() / 1000);
        counts.forEach((topic, byLane) -> {
            var ok = byLane.getOrDefault(Lane.NORMAL, 0L) + byLane.getOrDefault(Lane.PRIORITY, 0L);
            var err = byLane.getOrDefault(Lane.ERROR, 0L);

            td(ok);
            td(err);
        });
        html.append("</tr></table>");

        html.append("</body></html>");
        return html.toString();
    }

    private void register(String name, long total) {
        var start = "maven-explorer.";
        if (name.startsWith(start)) {
            name = name.substring(start.length());
        }

        var parts = name.split("-");

        register(parts[0], Lane.valueOf(parts[1]), total);
    }

    private final Map<String, Map<Lane, Long>> counts = new HashMap<>();

    private void register(String topic, Lane lane, long total) {
        var byLane = counts.getOrDefault(topic, new HashMap<>());
        byLane.put(lane, total);
        counts.put(topic, byLane);
    }

    private ListOffsetsResultInfo getOffsets(String name, int pid) {

        try {
            var tp = new TopicPartition(name, pid);
            ListOffsetsResultInfo listOffsetsResultInfo;
            listOffsetsResultInfo = ac.listOffsets(Collections.singletonMap(tp, null)).all().get().get(tp);
            return listOffsetsResultInfo;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private TopicDescription getTopicDescription(String name) {
        try {
            var d = ac.describeTopics(Set.of(name));
            return d.allTopicNames().get().get(name);

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void h1(Object o) {
        html.append("<h1>").append(o).append("</h1>");
    }

    private void h2(Object o) {
        html.append("<h2>").append(o).append("</h2>");
    }

    private void p(Object o) {
        html.append("<p>").append(o).append("</p>");
    }

    private void th(Object o) {
        tag("td", o);
    }

    private void td(Object o) {
        tag("td", o);
    }

    private void tag(String tag, Object o) {
        html.append("<").append(tag).append(">").append(o).append("</").append(tag).append(">");
    }
}
