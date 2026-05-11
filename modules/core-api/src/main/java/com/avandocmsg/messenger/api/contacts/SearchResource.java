package com.avandocmsg.messenger.api.contacts;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.search.MessageSearchService;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/search")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search", description = "Поиск пользователей и сообщений")
public class SearchResource {

    private final UserRepository userRepository;
    private final MessageSearchService messageSearchService;
    private final UserMessageSource messages;

    @Inject
    public SearchResource(UserRepository userRepository, MessageSearchService messageSearchService,
                          UserMessageSource messages) {
        this.userRepository = userRepository;
        this.messageSearchService = messageSearchService;
        this.messages = messages;
    }

    @GET
    @Path("/users")
    @Operation(summary = "Поиск пользователей для UI",
        description = "Исключает текущего пользователя, скрытые профили и пары с блокировкой. Ответ: UserSearchHit.")
    @ApiResponse(responseCode = "200", description = "Список пользователей",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSearchHit.class))))
    @ApiResponse(responseCode = "400", description = "Неверный запрос",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response searchUsers(@QueryParam("q") String query,
                                @Context SecurityContext securityContext) {
        if (query == null || query.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.search.q_required")))
                .build();
        }
        if (query.length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.search.q_min_length")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var results = userRepository.searchForViewer(userId, query, 20);
        return Response.ok(results).build();
    }

    /**
     * Поиск сообщений: Solr ({@code content_txt}) при {@code SOLR_URL}/{@code SOLR_ZK}, иначе SQL по plaintext (не E2EE).
     */
    @GET
    @Path("/messages")
    @Operation(summary = "Поиск сообщений", description = "Solr или SQL fallback по plaintext")
    public Response searchMessages(@QueryParam("q") String query,
                                   @QueryParam("limit") @DefaultValue("20") int limit,
                                   @Context SecurityContext securityContext) {
        if (query == null || query.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.search.q_required")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var results = messageSearchService.search(userId, query, limit);
        return Response.ok(results).build();
    }
}
