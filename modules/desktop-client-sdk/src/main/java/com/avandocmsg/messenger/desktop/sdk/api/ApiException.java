package com.avandocmsg.messenger.desktop.sdk.api;

public final class ApiException extends RuntimeException {

    private final int httpCode;

    public ApiException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    public int httpCode() {
        return httpCode;
    }
}
