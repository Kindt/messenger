package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StartCaptionsRequest(
    String language,
    @JsonProperty("sample_text") String sampleText
) {}
