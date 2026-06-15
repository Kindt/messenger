package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to send a message")
public record SendMessageRequest(
    @Schema(description = "Message type", example = "text") String type,
    @Schema(description = "Message content") String content,
    @Schema(description = "ID of message being replied to") @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @Schema(description = "Client-side deduplication ID") @JsonProperty("client_msg_id") String clientMsgId,
    @Schema(description = "Срок видимости сообщения в секундах (скрытие из ленты после истечения); null = без TTL", example = "3600")
    @JsonProperty("visibility_ttl_seconds") Integer visibilityTtlSeconds,
    @Schema(description = "Срок переноса тела в deep-archive в секундах; null = без archive TTL", example = "86400")
    @JsonProperty("archive_ttl_seconds") Integer archiveTtlSeconds,
    @Schema(description = "E2EE scheme: mls (RFC 9420 wire) or legacy (client-side X25519)", example = "mls")
    @JsonProperty("e2ee_scheme") String e2eeScheme
) {}
