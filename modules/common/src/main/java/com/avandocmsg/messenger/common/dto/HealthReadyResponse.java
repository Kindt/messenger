package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Readiness: версия API и зависимости (расширяемо для оркестраторов). */
public record HealthReadyResponse(
    String status,
    String version,
    @JsonProperty("database_ok") boolean databaseOk,
    @JsonProperty("redis_ok") Boolean redisOk,
    @JsonProperty("nats_ok") Boolean natsOk
) {}
