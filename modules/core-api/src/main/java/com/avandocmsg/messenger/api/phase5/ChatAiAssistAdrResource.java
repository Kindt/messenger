package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.phase5.dto.AiAssistRequest;
import com.avandocmsg.messenger.api.phase5.dto.AiAssistResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/chats/{chatId}/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "AI gateway", description = "On-prem AI chat assist (T02312)")
public class ChatAiAssistAdrResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public ChatAiAssistAdrResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @POST
    @Path("assist")
    @Operation(summary = "AI assist via L2 bridge preset")
    public Response aiAssist(@PathParam("chatId") String chatId,
                             AiAssistRequest request,
                             @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var prompt = request != null ? request.prompt() : null;
        return service.aiAssist(cid, userId, prompt)
            .map(r -> Response.ok(new AiAssistResponse(r.status(), r.reply())).build())
            .orElse(forbidden());
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.phase5.forbidden")))
            .build();
    }
}
