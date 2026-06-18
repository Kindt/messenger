package com.avandocmsg.messenger.api.live.dto;

public record LiveSessionModerationRequest(
    String action,
    String reason
) {}
