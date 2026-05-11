package com.avandocmsg.messenger.common.dto;

public record ApiError(
    int code,
    String message
) {}
