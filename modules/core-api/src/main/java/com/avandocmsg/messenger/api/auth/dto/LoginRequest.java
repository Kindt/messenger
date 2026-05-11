package com.avandocmsg.messenger.api.auth.dto;

public record LoginRequest(
    String username,
    String password
) {}
