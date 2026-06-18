package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cached link preview for a message")
public record MessageLinkPreview(
    @JsonProperty("url") String url,
    @JsonProperty("title") String title
) {}
