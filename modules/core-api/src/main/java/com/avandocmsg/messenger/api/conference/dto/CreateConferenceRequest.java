package com.avandocmsg.messenger.api.conference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateConferenceRequest(
    String title
) {}
