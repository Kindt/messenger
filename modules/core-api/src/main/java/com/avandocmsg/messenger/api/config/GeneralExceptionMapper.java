package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class GeneralExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger log = LoggerFactory.getLogger(GeneralExceptionMapper.class);

    private final UserMessageSource messages;

    @Inject
    public GeneralExceptionMapper(UserMessageSource messages) {
        this.messages = messages;
    }

    @Override
    public Response toResponse(Throwable e) {
        log.error("Unhandled exception", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ApiError(500, messages.get("error.internal")))
            .build();
    }
}
