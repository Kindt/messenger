package com.avandocmsg.messenger.ws.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsTokenValidatorTest {

    @Test
    void validate_garbageTokenReturnsNull() {
        var v = new WsTokenValidator("http://issuer.example", "http://localhost:65530/jwks");
        assertNull(v.validate("not-a-valid-jwt"));
    }

    @Test
    void validate_emptyReturnsNull() {
        var v = new WsTokenValidator("http://issuer.example", "http://localhost:65530/jwks");
        assertNull(v.validate(""));
    }
}
