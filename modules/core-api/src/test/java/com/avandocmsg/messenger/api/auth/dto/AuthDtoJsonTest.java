package com.avandocmsg.messenger.api.auth.dto;

import com.avandocmsg.messenger.api.admin.dto.AdminSessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loginResponse_serializesOAuthFieldNames() throws Exception {
        var json = mapper.writeValueAsString(new LoginResponse("at", "rt", 60));
        assertTrue(json.contains("\"access_token\""));
        assertTrue(json.contains("\"refresh_token\""));
        assertTrue(json.contains("\"expires_in\""));
    }

    @Test
    void registerResponse_serializesSnakeCase() throws Exception {
        var json = mapper.writeValueAsString(new RegisterResponse("u1", "bob", "Bob"));
        assertTrue(json.contains("\"user_id\""));
        assertTrue(json.contains("\"display_name\""));
    }

    @Test
    void adminSession_serializesSnakeCase() throws Exception {
        var json = mapper.writeValueAsString(
            new AdminSessionResponse("id", "x", List.of("admin"), "1.0"));
        assertTrue(json.contains("\"realm_roles\""));
        assertTrue(json.contains("\"api_version\""));
    }

    @Test
    void registerRequest_deserializesSnakeCaseOnly() throws Exception {
        var json = "{\"username\":\"a\",\"password\":\"b\",\"display_name\":\"N\"}";
        var r = mapper.readValue(json, RegisterRequest.class);
        assertEquals("N", r.displayName());
    }

    @Test
    void healthReady_serializesDatabaseOk() throws Exception {
        var om = new ObjectMapper();
        var json = om.writeValueAsString(
            new com.avandocmsg.messenger.common.dto.HealthReadyResponse("ready", "1.0", true, true, true));
        assertTrue(json.contains("\"database_ok\""));
        assertTrue(json.contains("\"redis_ok\""));
        assertTrue(json.contains("\"nats_ok\""));
    }
}
