package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Ответ 403 вместо общего 500 для {@link jakarta.annotation.security.RolesAllowed}. */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    private final UserMessageSource messages;

    @Inject
    public ForbiddenExceptionMapper(UserMessageSource messages) {
        this.messages = messages;
    }

    @Override
    public Response toResponse(ForbiddenException exception) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.insufficient_role")))
            .build();
    }
}
