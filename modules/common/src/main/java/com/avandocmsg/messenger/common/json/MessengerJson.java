package com.avandocmsg.messenger.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Shared {@link ObjectMapper} for workers and core-api (spec 025 FR-035/FR-036). */
public final class MessengerJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private MessengerJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
