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
    private final UUID keycloakUserId = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void register_createsUserInLocalDbWithKeycloakId() {
        var service = new TestingAuthService(keycloakUserId, userRepo);
        var request = new RegisterRequest("newuser", "password123", "New User");
        var outcome = service.register(request);

        assertInstanceOf(RegisterOutcome.Success.class, outcome);
        var response = ((RegisterOutcome.Success) outcome).response();
        assertEquals("newuser", response.username());
        assertEquals(keycloakUserId, userRepo.created.get("newuser"));
        assertEquals(keycloakUserId.toString(), response.userId());
    }

    @Test
    void register_failsWhenKeycloakUnavailable() {
        var service = new TestingAuthService(null);
        var request = new RegisterRequest("newuser", "password123", "New User");
        var outcome = service.register(request);

        assertInstanceOf(RegisterOutcome.Failure.class, outcome);
        assertEquals(
            RegisterOutcome.Status.KEYCLOAK_UNAVAILABLE,
            ((RegisterOutcome.Failure) outcome).status());
        assertTrue(userRepo.created.isEmpty());
    }

    @Test
    void register_failsWhenCreateReturnsFalse() {
        userRepo.failOnCreate = true;
        var service = new TestingAuthService(keycloakUserId, userRepo);
        var request = new RegisterRequest("failuser", "password123", "Fail User");
        var outcome = service.register(request);

        assertInstanceOf(RegisterOutcome.Failure.class, outcome);
        assertEquals(
            RegisterOutcome.Status.PERSISTENCE_FAILED,
            ((RegisterOutcome.Failure) outcome).status());
    }

    @Test
    void register_usernameExistsInKeycloak() {
        var service = new TestingAuthService(keycloakUserId, true);
        var outcome = service.register(new RegisterRequest("taken", "password123", "Taken"));

        assertInstanceOf(RegisterOutcome.Failure.class, outcome);
        assertEquals(
            RegisterOutcome.Status.USERNAME_EXISTS,
            ((RegisterOutcome.Failure) outcome).status());
    }

    static class TestingAuthService extends AuthService {
        private final UUID provisionedId;
        private final boolean usernameExists;

        TestingAuthService(UUID provisionedId) {
            this(provisionedId, false);
        }

        TestingAuthService(UUID provisionedId, boolean usernameExists) {
            super(new StubAppConfig(), new StubUserRepository(), new StubChatRepository(), UuidGenerator.standard());
            this.provisionedId = provisionedId;
            this.usernameExists = usernameExists;
        }

        TestingAuthService(UUID provisionedId, StubUserRepository userRepo) {
            super(new StubAppConfig(), userRepo, new StubChatRepository(), UuidGenerator.standard());
            this.provisionedId = provisionedId;
            this.usernameExists = false;
        }
        @Override
        protected UUID provisionKeycloakUser(RegisterRequest request) {
            if (usernameExists) {
                throw new UsernameExistsException(request.username());
            }
            return provisionedId;
        }
    }

    static class StubUserRepository extends com.avandocmsg.messenger.api.repository.UserRepository {
        final Map<String, UUID> created = new HashMap<>();
        boolean failOnCreate = false;

        StubUserRepository() {
            super(null);
        }

        @Override
        public boolean create(UUID id, String username, String displayName) {
            if (failOnCreate) {
                return false;
            }
            created.put(username, id);
            return true;
        }

        @Override
        public void upsertFromKeycloak(UUID id, String username, String displayName) {
            // no-op for tests without DB
        }

        @Override
        public Optional<com.avandocmsg.messenger.api.users.dto.UserProfile> findByUsername(String username) {
            return Optional.empty();
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
        public String version() {
            return "0.1.0-SNAPSHOT";
        }

        @Override
        public int port() {
            return 8080;
        }

        @Override
        public String dbJdbcUrl() {
            return "";
        }

        @Override
        public String dbUser() {
            return "";
        }

        @Override
        public String dbPassword() {
            return "";
        }

        @Override
        public int dbPoolSize() {
            return 1;
        }

        @Override
        public String redisUri() {
            return "";
        }

        @Override
        public String natsUrl() {
            return "";
        }

        @Override
        public String keycloakJwksUrl() {
            return "";
        }
    }
}
