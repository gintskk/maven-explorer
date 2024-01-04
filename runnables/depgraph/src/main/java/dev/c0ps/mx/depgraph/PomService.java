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

import static dev.c0ps.franz.Lane.PRIORITY;

import dev.c0ps.franz.Kafka;
import dev.c0ps.maven.data.GA;
import dev.c0ps.maven.data.GAV;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maven.resolution.MavenResolverData;
import dev.c0ps.maveneasyindex.Artifact;
import dev.c0ps.mx.downloader.utils.ResultsDatabase;
import dev.c0ps.mx.infra.kafka.DefaultTopics;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/pom")
public class PomService {

    private static final Status SC_REQUESTED = Status.CREATED;
    private static final Status SC_NOT_FOUND = Status.NOT_FOUND;
    private static final Status SC_FOUND = Status.ACCEPTED;
    private static final Status SC_IN_PROGRESS = Status.PARTIAL_CONTENT;
    private static final Status SC_CRASHED = Status.EXPECTATION_FAILED;

    private final ResultsDatabase db;
    private final Kafka kafka;
    private final MavenResolverData data;

    @Inject
    public PomService(ResultsDatabase db, Kafka kafka, MavenResolverData data) {
        this.db = db;
        this.kafka = kafka;
        this.data = data;
    }

    @GET
    @Path("/{groupId}/{artifactId}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPom( //
            @PathParam("groupId") String groupId, //
            @PathParam("artifactId") String artifactId, //
            @PathParam("version") String version) {

        var gav = new GAV(groupId, artifactId, version);
        var pom = data.findPom(gav, Long.MAX_VALUE);
        if (pom != null) {
            return ok(pom);
        }

        var a = new Artifact(groupId, artifactId, version, "jar");
        // TODO revise status/result logic of pipeline
        var r = db.get(a);
        publishRequest(a);

        if (r == null) {
            return status(SC_REQUESTED);
        }

        switch (r.status) {

        case DONE:
            return ok(r.pom);

        case REQUESTED:
            return status(SC_REQUESTED);

        case FOUND:
            return status(SC_FOUND);

        case RESOLVED:
        case DEPS_MISSING:
            return status(SC_IN_PROGRESS);

        case NOT_FOUND:
            return status(SC_NOT_FOUND);
        case CRASHED:
            return status(SC_CRASHED);

        default:
            throw new RuntimeException();
        }
    }

    @GET
    @Path("/{groupId}/{artifactId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersions( //
            @PathParam("groupId") String groupId, //
            @PathParam("artifactId") String artifactId) {

        var ga = new GA(groupId, artifactId);
        var versions = data.findVersions(ga, Long.MAX_VALUE);
        return Response.ok(versions).build();
    }

    private Response ok(Pom pom) {
        return Response.ok(pom).build();
    }

    private static Response status(Status s) {
        return Response.status(s).build();
    }

    private void publishRequest(Artifact gav) {
        kafka.publish(gav, DefaultTopics.REQUESTED, PRIORITY);
        try {
            db.markRequested(gav);
        } catch (IllegalStateException e) {
            // ignore
        }
    }
}