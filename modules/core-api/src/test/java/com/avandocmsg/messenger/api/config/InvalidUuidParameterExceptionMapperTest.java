package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.common.dto.ApiError;
import io.prometheus.client.CollectorRegistry;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InvalidUuidParameterExceptionMapperTest {

    @Test
    void incrementsInvalidUuidMetric() {
        var mapper = new InvalidUuidParameterExceptionMapper(I18nTestFixtures.messagesEn());
        double before = sampleInvalidUuidTotal();
        mapper.toResponse(InvalidUuidParameterException.invalidFormat("chat_id"));
        assertEquals(before + 1.0, sampleInvalidUuidTotal(), 0.001);
    }

    private static double sampleInvalidUuidTotal() {
        return Optional.ofNullable(CollectorRegistry.defaultRegistry.getSampleValue(
            "api_invalid_uuid_parameter_total", new String[0], new String[0])).orElse(0.0);
    }

    @Test
    void mapsMissingTo400() {
        var r = new InvalidUuidParameterExceptionMapper(I18nTestFixtures.messagesEn())
            .toResponse(InvalidUuidParameterException.missing("chat_id"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        var err = assertInstanceOf(ApiError.class, r.getEntity());
        assertEquals(400, err.code());
        assertEquals("chat id is required", err.message());
    }

    @Test
    void mapsInvalidFormatTo400() {
        var r = new InvalidUuidParameterExceptionMapper(I18nTestFixtures.messagesEn())
            .toResponse(InvalidUuidParameterException.invalidFormat("file_id"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        var err = assertInstanceOf(ApiError.class, r.getEntity());
        assertEquals(400, err.code());
        assertEquals("Invalid file id", err.message());
    }
}
