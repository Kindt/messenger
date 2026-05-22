package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.RegisterResponse;

/** Result of {@link AuthService#register(com.avandocmsg.messenger.api.auth.dto.RegisterRequest)}. */
public sealed interface RegisterOutcome {

    enum Status {
        SUCCESS,
        USERNAME_EXISTS,
        KEYCLOAK_UNAVAILABLE,
        PERSISTENCE_FAILED
    }

    record Success(RegisterResponse response) implements RegisterOutcome {}

    record Failure(Status status) implements RegisterOutcome {}

    static RegisterOutcome success(RegisterResponse response) {
        return new Success(response);
    }

    static RegisterOutcome failure(Status status) {
        return new Failure(status);
    }
}
