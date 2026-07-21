package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.live.dto.JoinLiveSessionResponse;
import com.avandocmsg.messenger.api.live.dto.LiveSessionResponse;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.common.dto.ApiError;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LiveSessionResourceTest {

    @Test
    void join_returnsOkWhenServiceSucceeds() {
        var join = sampleJoin();
        var resource = new LiveSessionResource(new FakeLiveSessionService(true, Optional.of(join), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.join(UUID.randomUUID().toString(), userSecurityContext());
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof JoinLiveSessionResponse);
    }

    @Test
    void join_returnsServiceUnavailableWhenNotConfigured() {
        var resource = new LiveSessionResource(new FakeLiveSessionService(false, Optional.empty(), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.join(UUID.randomUUID().toString(), userSecurityContext());
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ApiError);
    }

    @Test
    void create_returnsServiceUnavailableWhenNotConfigured() {
        var resource = new ChatLiveSessionResource(new FakeLiveSessionService(false, Optional.empty(), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.create(UUID.randomUUID().toString(), null, userSecurityContext());
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
    }

    @Test
    void get_invalidSessionId_throwsInvalidUuidParameterException() {
        var resource = new LiveSessionResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.get("not-a-uuid", userSecurityContext()));
    }

    private static JoinLiveSessionResponse sampleJoin() {
        return new JoinLiveSessionResponse(
            UUID.randomUUID().toString(),
            "room-abc",
            "wss://livekit.example",
            "token",
            "viewer",
            1,
            200
        );
    }

    private static final class FakeLiveSessionService extends LiveSessionService {
        private final boolean configured;
        private final Optional<JoinLiveSessionResponse> joinResult;
        private final Optional<LiveSessionResponse> getResult;

        FakeLiveSessionService(boolean configured,
                               Optional<JoinLiveSessionResponse> joinResult,
                               Optional<LiveSessionResponse> getResult) {
            super(null, null, null, null, I18nTestFixtures.messagesEn());
            this.configured = configured;
            this.joinResult = joinResult;
            this.getResult = getResult;
        }

        @Override
        public boolean liveStreamingConfigured() {
            return configured;
        }

        @Override
        public Optional<JoinLiveSessionResponse> join(UUID sessionId, UUID userId) {
            return joinResult;
        }

        @Override
        public Optional<LiveSessionResponse> get(UUID sessionId, UUID userId) {
            return getResult;
        }

        @Override
        public List<LiveSessionResponse> listForChat(UUID chatId, UUID userId, boolean activeOnly) {
            return List.of();
        }
    }

    private static SecurityContext userSecurityContext() {
        var actorId = UUID.randomUUID().toString();
        var principal = new UserPrincipal(actorId, "user", Set.of());
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        };
    }
}
