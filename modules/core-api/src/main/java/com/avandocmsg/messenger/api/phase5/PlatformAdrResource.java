package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.phase5.dto.AiAssistRequest;
import com.avandocmsg.messenger.api.phase5.dto.AiAssistResponse;
import com.avandocmsg.messenger.api.phase5.dto.PasskeyRegisterRequest;
import com.avandocmsg.messenger.api.phase5.dto.PasskeyResponse;
import com.avandocmsg.messenger.api.phase5.dto.SipGatewayRequest;
import com.avandocmsg.messenger.api.phase5.dto.SipGatewayResponse;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlatformAdrResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public PlatformAdrResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GET
    @Path("platform/sip")
    @Tag(name = "SIP", description = "Org SIP/H.323 gateway scaffold (T02315)")
    @Operation(summary = "Get org SIP gateway config")
    public Response getSip(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return service.sipStatus(userId)
            .map(row -> Response.ok(SipGatewayResponse.from(row)).build())
            .orElse(Response.ok(SipGatewayResponse.disabled()).build());
    }

    @PUT
    @Path("platform/sip")
    @Operation(summary = "Upsert org SIP gateway scaffold")
    public Response putSip(SipGatewayRequest request, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var enabled = request != null && Boolean.TRUE.equals(request.enabled());
        var uri = request != null ? request.gatewayUri() : null;
        var h323 = request != null && Boolean.TRUE.equals(request.h323Enabled());
        return service.upsertSip(userId, enabled, uri, h323)
            .map(row -> Response.ok(SipGatewayResponse.from(row)).build())
            .orElse(forbidden());
    }

    @GET
    @Path("auth/passkeys")
    @Tag(name = "Passkeys", description = "WebAuthn scaffold (T02316)")
    @Operation(summary = "List registered passkey credentials")
    public Response listPasskeys(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = service.listPasskeys(userId).stream().map(PasskeyResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("auth/passkeys")
    @Operation(summary = "Register passkey credential scaffold")
    public Response registerPasskey(PasskeyRegisterRequest request, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cred = request != null ? request.credentialId() : null;
        var pk = request != null ? request.publicKey() : null;
        return service.registerPasskeyScaffold(userId, cred, pk)
            .map(id -> Response.status(Response.Status.CREATED).entity(PasskeyResponse.created(id.toString(), cred)).build())
            .orElse(forbidden());
    }

    @POST
    @Path("chats/{chatId}/ai/assist")
    @Tag(name = "AI gateway", description = "On-prem AI chat assist (T02312)")
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
