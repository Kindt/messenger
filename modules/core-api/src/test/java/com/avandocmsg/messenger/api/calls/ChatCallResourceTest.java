package com.avandocmsg.messenger.api.calls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.avandocmsg.messenger.api.calls.dto.CallJoinResponse;
import com.avandocmsg.messenger.api.calls.dto.CallSignalRequest;
import com.avandocmsg.messenger.api.calls.dto.CallSignalResponse;
import com.avandocmsg.messenger.api.calls.dto.CreateCallRequest;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.common.dto.CallSessionEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.media.InMemoryMediaRoomService;
import com.avandocmsg.messenger.media.MediaErrorCode;
import com.avandocmsg.messenger.media.MediaSignalingProcessor;
import com.avandocmsg.messenger.media.SignalType;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatCallResourceTest {

    private static final UUID CHAT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID OTHER = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Test
    void createsJoinsSignalsAndPollsUnifiedCall() throws Exception {
        var chats = new StubChatRepository();
        chats.role = "owner";
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var events = new ArrayList<CallSessionEvent>();
        var resource = new ChatCallResource(
            new UnifiedCallService(chats, rooms, MediaSignalingProcessor.NOOP, events::add)
        );

        var createdResponse = resource.create(
            CHAT.toString(),
            new CreateCallRequest("group", "video"),
            userSecurityContext()
        );
        assertEquals(Response.Status.CREATED.getStatusCode(), createdResponse.getStatus());
        assertTrue(createdResponse.getEntity() instanceof CallJoinResponse);
        var created = (CallJoinResponse) createdResponse.getEntity();
        assertEquals("video", events.getFirst().mediaIntent());
        assertEquals(
            Response.Status.NO_CONTENT.getStatusCode(),
            resource.decline(CHAT.toString(), created.sessionId(), userSecurityContext(OTHER)).getStatus()
        );
        assertEquals(CallSessionEvent.INVITATION_DECLINED, events.getLast().type());

        var signalResponse = resource.signal(
            CHAT.toString(),
            created.sessionId(),
            created.participantId(),
            new CallSignalRequest("offer", "v=0\r\n", null),
            userSecurityContext()
        );
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), signalResponse.getStatus());

        rooms.publishNodeSignal(
            UUID.fromString(created.sessionId()),
            UUID.fromString(created.participantId()),
            SignalType.ANSWER,
            "v=0\r\nanswer",
            null
        );
        rooms.publishNodeError(
            UUID.fromString(created.sessionId()),
            UUID.fromString(created.participantId()),
            MediaErrorCode.NO_COMMON_AUDIO_CODEC
        );
        var pollResponse = resource.pollSignals(
            CHAT.toString(),
            created.sessionId(),
            created.participantId(),
            userSecurityContext()
        );
        assertEquals(Response.Status.OK.getStatusCode(), pollResponse.getStatus());
        assertTrue(pollResponse.getEntity() instanceof java.util.List<?>);
        var polled = (java.util.List<?>) pollResponse.getEntity();
        assertEquals(2, polled.size());
        assertTrue(polled.getLast() instanceof CallSignalResponse);
        var errorSignal = (CallSignalResponse) polled.getLast();
        assertEquals("error", errorSignal.type());
        assertEquals("NO_COMMON_AUDIO_CODEC", errorSignal.errorCode());
        assertTrue(
            MessengerJson.mapper().writeValueAsString(errorSignal)
                .contains("\"error_code\":\"NO_COMMON_AUDIO_CODEC\"")
        );

        var joinResponse = resource.join(
            CHAT.toString(),
            created.sessionId(),
            userSecurityContext(OTHER)
        );
        assertEquals(Response.Status.OK.getStatusCode(), joinResponse.getStatus());
        var joined = (CallJoinResponse) joinResponse.getEntity();
        assertEquals(
            Response.Status.NO_CONTENT.getStatusCode(),
            resource.leave(
                CHAT.toString(),
                created.sessionId(),
                joined.participantId(),
                userSecurityContext(OTHER)
            ).getStatus()
        );

        assertEquals(
            Response.Status.FORBIDDEN.getStatusCode(),
            resource.end(CHAT.toString(), created.sessionId(), userSecurityContext(OTHER)).getStatus()
        );
        assertEquals(
            Response.Status.NO_CONTENT.getStatusCode(),
            resource.end(CHAT.toString(), created.sessionId(), userSecurityContext()).getStatus()
        );
    }

    @Test
    void rejectsCallCreationForNonMember() {
        var chats = new StubChatRepository();
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), Duration.ofMinutes(2), "embedded-1");
        var resource = new ChatCallResource(new UnifiedCallService(chats, rooms));

        var response = resource.create(
            CHAT.toString(),
            new CreateCallRequest("direct"),
            userSecurityContext()
        );

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    private static SecurityContext userSecurityContext() {
        return userSecurityContext(USER);
    }

    private static SecurityContext userSecurityContext(UUID userId) {
        var principal = new UserPrincipal(userId.toString(), "user", Set.of());
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

    private static final class StubChatRepository extends EmptyChatPersistencePort {
        private String role;

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return role;
        }
    }
}
