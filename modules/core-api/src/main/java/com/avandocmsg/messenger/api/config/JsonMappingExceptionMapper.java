package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    private static final Logger log = LoggerFactory.getLogger(JsonMappingExceptionMapper.class);

    private final UserMessageSource messages;

    @Inject
    public JsonMappingExceptionMapper(UserMessageSource messages) {
        this.messages = messages;
    }

    @Override
    public Response toResponse(JsonProcessingException e) {
        log.warn("JSON parse error: {}", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, messages.format("error.json.invalid", e.getOriginalMessage())))
            .build();
    }
}
