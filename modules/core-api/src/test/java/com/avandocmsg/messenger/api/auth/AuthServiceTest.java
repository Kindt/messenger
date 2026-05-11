package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.RegisterRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private final StubUserRepository userRepo = new StubUserRepository();
    private final StubAppConfig appConfig = new StubAppConfig();
    private final StubChatRepository chatRepo = new StubChatRepository();
    private final AuthService authService = new AuthService(appConfig, userRepo, chatRepo, UuidGenerator.standard());

    @Test
    void register_createsUserInLocalDb() {
        var request = new RegisterRequest("newuser", "password123", "New User");
        var response = authService.register(request);

        assertNotNull(response);
        assertEquals("newuser", response.username());
        assertTrue(userRepo.created.containsKey("newuser"));
    }

    @Test
    void register_failsWhenCreateReturnsFalse() {
        userRepo.failOnCreate = true;

        var request = new RegisterRequest("failuser", "password123", "Fail User");
        var response = authService.register(request);

        assertNull(response);
    }

    @Test
    void register_assignsGeneratedUserId() {
        var request = new RegisterRequest("user123", "pass", "User 123");
        var response = authService.register(request);

        assertNotNull(response);
        assertNotNull(response.userId());
        assertFalse(response.userId().isBlank());
    }

    static class StubUserRepository extends com.avandocmsg.messenger.api.repository.UserRepository {
        final Map<String, UUID> created = new HashMap<>();
        boolean failOnCreate = false;

        StubUserRepository() { super(null); }

        @Override
        public boolean create(UUID id, String username, String displayName) {
            if (failOnCreate) return false;
            created.put(username, id);
            return true;
        }

        @Override
        public void upsertFromKeycloak(UUID id, String username, String displayName) {
            // no-op for tests without DB
        }
    }

    static class StubChatRepository extends com.avandocmsg.messenger.api.repository.ChatRepository {
        StubChatRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public java.util.UUID ensureSavedVaultChat(java.util.UUID userId) {
            return java.util.UUID.randomUUID();
        }
    }

    static class StubAppConfig extends AppConfig {
        StubAppConfig() {
            // Skip property loading
        }

        @Override
        public String keycloakIssuer() {
            return "http://localhost:8081/realms/avandocmsg";
        }

        @Override
        public String version() { return "0.1.0-SNAPSHOT"; }

        @Override
        public int port() { return 8080; }

        @Override
        public String dbJdbcUrl() { return ""; }

        @Override
        public String dbUser() { return ""; }

        @Override
        public String dbPassword() { return ""; }

        @Override
        public int dbPoolSize() { return 1; }

        @Override
        public String redisUri() { return ""; }

        @Override
        public String natsUrl() { return ""; }

        @Override
        public String keycloakJwksUrl() { return ""; }
    }
}
