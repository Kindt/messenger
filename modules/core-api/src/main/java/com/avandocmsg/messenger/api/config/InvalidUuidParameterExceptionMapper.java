package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.api.metrics.ApiValidationMetrics;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidUuidParameterExceptionMapper implements ExceptionMapper<InvalidUuidParameterException> {

    private final UserMessageSource messages;

    @Inject
    public InvalidUuidParameterExceptionMapper(UserMessageSource messages) {
        this.messages = messages;
    }

    @Override
    public Response toResponse(InvalidUuidParameterException e) {
        ApiValidationMetrics.invalidUuidParameter();
        var label = messages.get("param." + e.paramKey());
        var msg = e.missing()
            ? messages.format("uuid.param.missing", label)
            : messages.format("uuid.param.invalid", label);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, msg))
            .build();
    }
}
