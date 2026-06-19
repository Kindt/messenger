package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.RegisterRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserLookupAdapter;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private final StubUserRepository userRepo = new StubUserRepository();
    private final StubAppConfig appConfig = new StubAppConfig();
    private final StubUserRepositoryPort userPort = new StubUserRepositoryPort();
    private final StubSavedChatPort savedChatPort = new StubSavedChatPort();
    private final UUID keycloakUserId = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void register_createsUserInLocalDbWithKeycloakId() {
        var userPort = new StubUserRepositoryPort();
        var service = new TestingAuthService(keycloakUserId, userPort);
        var request = new RegisterRequest("newuser", "password123", "New User");
        var outcome = service.register(request);

        assertInstanceOf(RegisterOutcome.Success.class, outcome);
        var response = ((RegisterOutcome.Success) outcome).response();
        assertEquals("newuser", response.username());
        assertEquals(keycloakUserId, userPort.created.get("newuser"));
        assertEquals(keycloakUserId.toString(), response.userId());
    }

    @Test
    void register_failsWhenKeycloakUnavailable() {
        var userPort = new StubUserRepositoryPort();
        var service = new TestingAuthService(null, userPort);
        var request = new RegisterRequest("newuser", "password123", "New User");
        var outcome = service.register(request);

        assertInstanceOf(RegisterOutcome.Failure.class, outcome);
        assertEquals(
            RegisterOutcome.Status.KEYCLOAK_UNAVAILABLE,
            ((RegisterOutcome.Failure) outcome).status());
        assertTrue(userPort.created.isEmpty());
    }

    @Test
    void register_failsWhenCreateReturnsFalse() {
        var userPort = new StubUserRepositoryPort();
        userPort.failOnCreate = true;
        var service = new TestingAuthService(keycloakUserId, userPort);
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

        TestingAuthService(UUID provisionedId, StubUserRepositoryPort userPort) {
            super(new StubAppConfig(), new JdbcUserLookupAdapter(new StubUserRepository()), userPort, new StubSavedChatPort());
            this.provisionedId = provisionedId;
            this.usernameExists = false;
        }

        TestingAuthService(UUID provisionedId, boolean usernameExists) {
            super(new StubAppConfig(), new JdbcUserLookupAdapter(new StubUserRepository()), new StubUserRepositoryPort(), new StubSavedChatPort());
            this.provisionedId = provisionedId;
            this.usernameExists = usernameExists;
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

    static class StubUserRepositoryPort implements UserRepositoryPort {
        final Map<String, UUID> created = new HashMap<>();
        boolean failOnCreate = false;

        @Override
        public Optional<com.avandocmsg.messenger.core.domain.UserProfile> findById(UserId id) {
            return Optional.empty();
        }

        @Override
        public boolean updateProfile(UserId id, String displayName, String phone) {
            return false;
        }

        @Override
        public boolean updatePresence(UserId id, String presenceStatus) {
            return false;
        }

        @Override
        public boolean updateUserStatus(UserId id, String presenceStatus, String customStatusText,
                                        java.time.Instant dndUntil, boolean clearDndUntil) {
            return false;
        }

        @Override
        public boolean updatePrivacy(UserId id, boolean disableReadReceipts) {
            return false;
        }

        @Override
        public boolean updateUiLocale(UserId id, String uiLocale) {
            return false;
        }

        @Override
        public boolean touchHeartbeat(UserId id) {
            return false;
        }

        @Override
        public void upsertFromKeycloak(UserId id, String username, String displayName) {
        }

        @Override
        public boolean createLocalUser(UserId id, String username, String displayName) {
            if (failOnCreate) {
                return false;
            }
            created.put(username, id.value());
            return true;
        }
    }

    static class StubSavedChatPort implements SavedChatPort {
        @Override
        public Optional<ChatId> getSavedChatId(UserId userId) {
            return Optional.empty();
        }

        @Override
        public Optional<ChatId> ensureSavedVaultChat(UserId userId) {
            return Optional.of(ChatId.of(UUID.randomUUID()));
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
