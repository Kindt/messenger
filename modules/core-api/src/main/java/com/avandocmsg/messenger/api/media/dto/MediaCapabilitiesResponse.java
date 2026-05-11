package com.avandocmsg.messenger.api.media.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Лимиты загрузки, типы сообщений для вложений и ICE/STUN для клиентского WebRTC. */
public record MediaCapabilitiesResponse(
    @JsonProperty("max_upload_bytes") long maxUploadBytes,
    @JsonProperty("message_types_with_attachments") List<String> messageTypesWithAttachments,
    @JsonProperty("stun_uris") List<String> stunUris,
    @JsonProperty("conference_provider") String conferenceProvider,
    @JsonProperty("jitsi_base_url") String jitsiBaseUrl,
    @JsonProperty("note") String note
) {}
