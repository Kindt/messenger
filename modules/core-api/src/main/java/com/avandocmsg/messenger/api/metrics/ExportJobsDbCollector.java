package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.core.port.AdminMetricsQueryPort;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;

import java.util.List;

/** Gauges derived from {@code export_jobs} (refreshed on each Prometheus scrape). */
public final class ExportJobsDbCollector extends Collector {

    private final AdminMetricsQueryPort adminMetricsQueryPort;
    private final int processingStaleMinutes;

    public ExportJobsDbCollector(AdminMetricsQueryPort adminMetricsQueryPort, int processingStaleMinutes) {
        this.adminMetricsQueryPort = adminMetricsQueryPort;
        this.processingStaleMinutes = processingStaleMinutes;
    }

    public static void registerDefault(AdminMetricsQueryPort adminMetricsQueryPort, int processingStaleMinutes) {
        CollectorRegistry.defaultRegistry.register(
            new ExportJobsDbCollector(adminMetricsQueryPort, processingStaleMinutes));
    }

    @Override
    public List<MetricFamilySamples> collect() {
        long stale;
        try {
            stale = adminMetricsQueryPort.countProcessingStaleExportJobs(processingStaleMinutes);
        } catch (Exception e) {
            stale = 0;
        }
        return List.of(new GaugeMetricFamily(
            "export_jobs_processing_stale",
            "export_jobs in processing with updated_at older than "
                + processingStaleMinutes
                + " minutes (EXPORT_PROCESSING_STALE_MINUTES)",
            stale));
    }
}
