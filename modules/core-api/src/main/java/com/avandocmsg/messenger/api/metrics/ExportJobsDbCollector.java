package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.export.ExportJobStaleCounts;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;

import javax.sql.DataSource;
import java.util.List;

/** Gauges derived from {@code export_jobs} (refreshed on each Prometheus scrape). */
public final class ExportJobsDbCollector extends Collector {

    private final DataSource dataSource;
    private final int processingStaleMinutes;

    public ExportJobsDbCollector(DataSource dataSource, int processingStaleMinutes) {
        this.dataSource = dataSource;
        this.processingStaleMinutes = processingStaleMinutes;
    }

    public static void registerDefault(DataSource dataSource, int processingStaleMinutes) {
        CollectorRegistry.defaultRegistry.register(
            new ExportJobsDbCollector(dataSource, processingStaleMinutes));
    }

    @Override
    public List<MetricFamilySamples> collect() {
        long stale;
        try {
            stale = ExportJobStaleCounts.countProcessingStale(dataSource, processingStaleMinutes);
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
