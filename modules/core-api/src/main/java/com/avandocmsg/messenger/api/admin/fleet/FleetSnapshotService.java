package com.avandocmsg.messenger.api.admin.fleet;

import com.avandocmsg.messenger.api.admin.fleet.dto.FleetSnapshotResponse;
import com.avandocmsg.messenger.api.admin.ui.AdminStatsPort;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.metrics.FleetSnapshotMetrics;
import com.avandocmsg.messenger.common.hotplug.HotPlugRegistry;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FleetSnapshotService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FleetTargetRegistry registry;
    private final AdminStatsPort localStats;
    private final AppConfig appConfig;
    private final HotPlugRegistry hotPlugRegistry;
    private final long hotplugTtlMs;
    private final FleetHttpProbe probe;
    private final ExecutorService executor;

    public FleetSnapshotService(
        FleetTargetRegistry registry,
        AdminStatsPort localStats,
        AppConfig appConfig,
        HotPlugRegistry hotPlugRegistry,
        long hotplugTtlMs
    ) {
        this.registry = registry;
        this.localStats = localStats;
        this.appConfig = appConfig;
        this.hotPlugRegistry = hotPlugRegistry;
        this.hotplugTtlMs = hotplugTtlMs;
        int timeoutMs = appConfig.fleetProbeTimeoutMs();
        this.probe = new FleetHttpProbe(Duration.ofMillis(timeoutMs));
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public FleetSnapshotResponse snapshot() {
        var now = Instant.now();
        var nowMs = System.currentTimeMillis();
        var components = new ArrayList<FleetSnapshotResponse.FleetComponentSnapshot>();

        components.add(localComponent());

        var futures = registry.targets().stream()
            .map(t -> CompletableFuture.supplyAsync(() -> probeTarget(t), executor))
            .toList();
        for (var f : futures) {
            components.add(f.join());
        }

        if (hotPlugRegistry != null) {
            hotPlugRegistry.evictStale(nowMs);
            for (var entry : hotPlugRegistry.snapshot().entrySet()) {
                var p = entry.getValue();
                var age = nowMs - p.lastHeartbeatEpochMs();
                var present = age <= hotplugTtlMs;
                components.add(new FleetSnapshotResponse.FleetComponentSnapshot(
                    "hotplug-" + p.serviceId(),
                    "hotplug-worker",
                    "nats-heartbeat",
                    null,
                    NatsSubjects.SVC_HEARTBEAT_PREFIX + p.serviceId(),
                    true,
                    present,
                    null,
                    null,
                    present ? null : "stale (" + age + "ms)",
                    p.state(),
                    null,
                    p.lastHeartbeatEpochMs(),
                    null,
                    null,
                    null
                ));
            }
        }

        components.sort(Comparator
            .comparing(FleetSnapshotResponse.FleetComponentSnapshot::role)
            .thenComparing(FleetSnapshotResponse.FleetComponentSnapshot::id));

        var stats = localStats.snapshot();
        var counts = stats.counts();
        var shared = new FleetSnapshotResponse.SharedData(
            stats.apiVersion(),
            new FleetSnapshotResponse.DatabaseCounts(
                counts.users(),
                counts.chats(),
                counts.messages(),
                counts.countsAvailable()
            )
        );

        var response = new FleetSnapshotResponse(
            now,
            appConfig.fleetAggregatorNode(),
            appConfig.fleetProbeTimeoutMs(),
            List.copyOf(components),
            shared
        );
        FleetSnapshotMetrics.record(response);
        return response;
    }

    private FleetSnapshotResponse.FleetComponentSnapshot localComponent() {
        var stats = localStats.snapshot();
        var deps = stats.dependencies();
        return new FleetSnapshotResponse.FleetComponentSnapshot(
            appConfig.fleetAggregatorNode(),
            "core-api",
            "local-jvm",
            null,
            null,
            true,
            deps.databaseOk() && deps.redisOk() && deps.natsOk(),
            200,
            0L,
            null,
            "ACTIVE",
            stats.jvm().uptimeMs(),
            null,
            stats.jvm().uptimeMs(),
            stats.jvm().heapUsedBytes(),
            new FleetSnapshotResponse.Dependencies(deps.databaseOk(), deps.redisOk(), deps.natsOk())
        );
    }

    private FleetSnapshotResponse.FleetComponentSnapshot probeTarget(FleetTarget target) {
        var result = probe.probe(target.probeUrl());
        return new FleetSnapshotResponse.FleetComponentSnapshot(
            target.id(),
            target.role(),
            "http-probe",
            target.baseUrl(),
            target.probeUrl(),
            result.reachable(),
            result.ready(),
            result.httpStatus() > 0 ? result.httpStatus() : null,
            result.latencyMs(),
            result.error(),
            null,
            result.jvmUptimeMs(),
            null,
            result.jvmUptimeMs(),
            result.heapUsedBytes(),
            result.dependencies()
        );
    }

    /** Internal HTTP probe helper. */
    static final class FleetHttpProbe {
        private final HttpClient client;
        private final Duration timeout;

        FleetHttpProbe(Duration timeout) {
            this.timeout = timeout;
            this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }

        record ProbeResult(
            boolean reachable,
            boolean ready,
            int httpStatus,
            long latencyMs,
            String error,
            Long jvmUptimeMs,
            Long heapUsedBytes,
            FleetSnapshotResponse.Dependencies dependencies
        ) {
            static ProbeResult unreachable(String error) {
                return new ProbeResult(false, false, 0, 0L, error, null, null, null);
            }
        }

        ProbeResult probe(String url) {
            var start = System.currentTimeMillis();
            try {
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .GET()
                    .header("Accept", "application/json, text/plain, */*")
                    .build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                var latency = System.currentTimeMillis() - start;
                var body = resp.body();
                var ready = resp.statusCode() >= 200 && resp.statusCode() < 300;
                Long uptime = null;
                Long heap = null;
                FleetSnapshotResponse.Dependencies deps = null;
                if (body != null && !body.isBlank() && body.trim().startsWith("{")) {
                    try {
                        JsonNode node = MAPPER.readTree(body);
                        if (node.has("status")) {
                            var st = node.get("status").asText("");
                            ready = ready && !"not ready".equalsIgnoreCase(st) && !"down".equalsIgnoreCase(st);
                        }
                        if (node.has("jvm") && node.get("jvm").isObject()) {
                            var jvm = node.get("jvm");
                            if (jvm.has("uptime_ms")) {
                                uptime = jvm.get("uptime_ms").asLong();
                            }
                            if (jvm.has("heap_used_bytes")) {
                                heap = jvm.get("heap_used_bytes").asLong();
                            }
                        }
                        if (node.has("dependencies") && node.get("dependencies").isObject()) {
                            var d = node.get("dependencies");
                            deps = new FleetSnapshotResponse.Dependencies(
                                boolOrNull(d, "database_ok"),
                                boolOrNull(d, "redis_ok"),
                                boolOrNull(d, "nats_ok")
                            );
                        } else {
                            deps = new FleetSnapshotResponse.Dependencies(
                                boolOrNull(node, "database_ok"),
                                boolOrNull(node, "redis_ok"),
                                boolOrNull(node, "nats_ok")
                            );
                        }
                    } catch (Exception ignored) {
                        // plain text health
                    }
                }
                return new ProbeResult(true, ready, resp.statusCode(), latency, null, uptime, heap, deps);
            } catch (Exception e) {
                return ProbeResult.unreachable(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }

        private static Boolean boolOrNull(JsonNode node, String field) {
            if (node == null || !node.has(field) || node.get(field).isNull()) {
                return null;
            }
            return node.get(field).asBoolean();
        }
    }

}
