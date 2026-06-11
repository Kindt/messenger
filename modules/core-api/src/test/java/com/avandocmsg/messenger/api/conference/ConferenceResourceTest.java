package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.common.dto.ApiError;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConferenceResourceTest {

    @Test
    void createStandalone_returnsCreatedWhenServiceSucceeds() {
        var conf = sampleConference();
        var resource = new ConferenceResource(new FakeConferenceService(Optional.of(conf), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.createStandalone(new CreateConferenceRequest("Demo", null), userSecurityContext());
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ConferenceResponse);
    }

    @Test
    void createStandalone_returnsBadRequestWhenServiceFails() {
        var resource = new ConferenceResource(new FakeConferenceService(Optional.empty(), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.createStandalone(new CreateConferenceRequest("Demo", null), userSecurityContext());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ApiError);
    }

    @Test
    void getByRoom_returnsOkWhenFound() {
        var conf = sampleConference();
        var resource = new ConferenceResource(new FakeConferenceService(Optional.empty(), Optional.of(conf)),
            I18nTestFixtures.messagesEn());
        var response = resource.getByRoom("room-abc", userSecurityContext());
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    void getByRoom_returnsNotFoundWhenMissing() {
        var resource = new ConferenceResource(new FakeConferenceService(Optional.empty(), Optional.empty()),
            I18nTestFixtures.messagesEn());
        var response = resource.getByRoom("missing", userSecurityContext());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void create_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new ChatConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.create("not-a-uuid", new CreateConferenceRequest(null, null), userSecurityContext()));
    }

    @Test
    void get_invalidConferenceId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.get("not-a-uuid", userSecurityContext()));
    }

    @Test
    void list_invalidChatId_throwsInvalidUuidParameterException() {
        var resource = new ChatConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.list("not-a-uuid", true, userSecurityContext()));
    }

    @Test
    void listParticipants_invalidConferenceId_throwsInvalidUuidParameterException() {
        var resource = new ConferenceResource(null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.listParticipants("not-a-uuid", userSecurityContext()));
    }

    private static ConferenceResponse sampleConference() {
        return new ConferenceResponse(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "Demo",
            "active",
            "room-abc",
            "https://meet.example/room-abc",
            "jitsi",
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            0);
    }

    private static final class FakeConferenceService extends ConferenceService {
        private final Optional<ConferenceResponse> standaloneResult;
        private final Optional<ConferenceResponse> byRoomResult;

        FakeConferenceService(Optional<ConferenceResponse> standaloneResult,
                              Optional<ConferenceResponse> byRoomResult) {
            super(null, null, null, null, I18nTestFixtures.messagesEn());
            this.standaloneResult = standaloneResult;
            this.byRoomResult = byRoomResult;
        }

        @Override
        public Optional<ConferenceResponse> createStandalone(UUID userId, CreateConferenceRequest request) {
            return standaloneResult;
        }

        @Override
        public Optional<ConferenceResponse> getByRoomSlug(UUID userId, String roomSlug) {
            return byRoomResult;
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
