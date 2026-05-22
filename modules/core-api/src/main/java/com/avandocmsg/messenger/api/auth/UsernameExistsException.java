package com.avandocmsg.messenger.api.auth;

/** Keycloak reported that the username is already taken. */
public final class UsernameExistsException extends RuntimeException {

    public UsernameExistsException(String username) {
        super(username);
    }
}
