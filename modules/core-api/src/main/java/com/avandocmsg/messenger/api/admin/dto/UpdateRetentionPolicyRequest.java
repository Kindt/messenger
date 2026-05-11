package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Тело {@code PATCH .../organizations/{orgId}/retention}: полная замена настраиваемых полей строки в БД
 * (числа {@code null} = хранить NULL и при GET подставлять дефолты платформы).
 */
@Schema(description = "Обновление политики ретенции организации (все три boolean обязательны)")
public record UpdateRetentionPolicyRequest(
    @Schema(description = "Макс. возраст тела в Hot (дни); null = не задано в БД (дефолт платформы)")
    @JsonProperty("hot_message_body_max_age_days") Integer hotMessageBodyMaxAgeDays,
    @Schema(description = "Мин. возраст метаданных в Hot (дни); null = не задано")
    @JsonProperty("hot_metadata_min_age_days") Integer hotMetadataMinAgeDays,
    @JsonProperty("archive_metadata_enabled") Boolean archiveMetadataEnabled,
    @JsonProperty("deep_archive_enabled") Boolean deepArchiveEnabled,
    @JsonProperty("legal_hold") Boolean legalHold
) {}
