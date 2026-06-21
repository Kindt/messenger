package com.avandocmsg.messenger.api.search;

public record SearchBackendStatus(
    String profileId,
    String state,
    String reason
) {}
