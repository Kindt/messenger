package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * JVM + default process metrics (ТЗ п. 22, baseline observability).
 * Application counters such as {@link ApiDeniedMetrics} and {@link ApiValidationMetrics}
 * register with the same default registry and appear here.
 */
@Path("/v1/metrics")
public class PrometheusMetricsResource {

    static {
        DefaultExports.initialize();
    }

    @GET
    @Path("/prometheus")
    @Produces(MediaType.TEXT_PLAIN)
    public StreamingOutput prometheus() {
        return output -> {
            Writer w = new OutputStreamWriter(output);
            TextFormat.write004(w, CollectorRegistry.defaultRegistry.metricFamilySamples());
            w.flush();
        };
    }
}
