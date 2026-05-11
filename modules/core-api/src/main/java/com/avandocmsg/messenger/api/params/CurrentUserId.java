package com.avandocmsg.messenger.api.params;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

/** Resolves the authenticated user id (JWT {@code sub}) as {@link UUID} with the same validation as path/query UUIDs. */
public final class CurrentUserId {

    private CurrentUserId() {
    }

    public static UUID uuid(SecurityContext securityContext) {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        return UuidParams.required(securityContext.getUserPrincipal().getName(), "user_id");
    }
}
