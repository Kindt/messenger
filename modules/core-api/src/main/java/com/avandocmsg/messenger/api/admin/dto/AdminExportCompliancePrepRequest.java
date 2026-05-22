package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Подготовка чата для retention/export smokes (сообщения + политика ретенции)")
public record AdminExportCompliancePrepRequest(
    @Schema(description = "Существующий чат; если null — создать group (create_group)",
        example = "11111111-1111-1111-1111-111111111111")
    @JsonProperty("chat_id") String chatId,
    @Schema(description = "Создать group-чат, если chat_id не задан", example = "true")
    @JsonProperty("create_group") Boolean createGroup,
    @Schema(description = "Число тестовых сообщений (1–20)", example = "3")
    @JsonProperty("message_count") Integer messageCount,
    @Schema(description = "Загрузить тестовый файл и отправить сообщение type=file", example = "true")
    @JsonProperty("include_file") Boolean includeFile,
    @Schema(description = "Имя тестового файла (при include_file)", example = "compliance-smoke.txt")
    @JsonProperty("file_name") String fileName
) {}
