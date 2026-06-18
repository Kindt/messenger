package com.avandocmsg.messenger.api.bots;

import com.avandocmsg.messenger.api.bots.dto.BotBanRequest;
import com.avandocmsg.messenger.api.bots.dto.BotSendMessageRequest;
import com.avandocmsg.messenger.api.bots.dto.BotSubscribeRequest;
import com.avandocmsg.messenger.api.bots.dto.BotWebhookRequest;
import com.avandocmsg.messenger.api.bots.dto.CreateBotRequest;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bots", description = "Bot API MVP — register, webhook, sendMessage")
public class BotResource {

    private final BotService botService;
    private final UserMessageSource messages;

    @Inject
    public BotResource(BotService botService, UserMessageSource messages) {
        this.botService = botService;
        this.messages = messages;
    }

    @POST
    @Path("/bots")
    @Operation(summary = "Register bot", description = "Create bot account; access_token returned once")
    public Response create(CreateBotRequest request, @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var result = botService.create(ownerId, request);
        return switch (result.outcome()) {
            case SUCCESS -> Response.status(Response.Status.CREATED).entity(result.response()).build();
            case INVALID_NAME -> badRequest("error.bot.invalid_name");
            case INVALID_WEBHOOK -> badRequest("error.bot.invalid_webhook");
            case INVALID_LISTEN_MODE -> badRequest("error.bot.invalid_listen_mode");
            case NAME_TAKEN -> Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.bot.name_taken"))).build();
            case PERSISTENCE_FAILED -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.bot.create_failed"))).build();
        };
    }

    @GET
    @Path("/bots")
    @Operation(summary = "List owned bots")
    public Response list(@Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        return Response.ok(botService.listOwned(ownerId)).build();
    }

    @GET
    @Path("/bots/{botId}")
    @Operation(summary = "Get owned bot")
    public Response get(@PathParam("botId") String botIdStr, @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var botId = UuidParams.required(botIdStr, "bot_id");
        return botService.getOwned(ownerId, botId)
            .map(b -> Response.ok(b).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.bot.not_found"))).build());
    }

    @PUT
    @Path("/bots/{botId}/webhook")
    @Operation(summary = "Set default webhook URL")
    public Response updateWebhook(@PathParam("botId") String botIdStr,
                                  BotWebhookRequest request,
                                  @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var botId = UuidParams.required(botIdStr, "bot_id");
        if (request == null || request.webhookUrl() == null || request.webhookUrl().isBlank()) {
            return badRequest("error.bot.invalid_webhook");
        }
        if (!botService.updateWebhook(ownerId, botId, request.webhookUrl())) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.bot.not_found"))).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/bots/{botId}/chats/{chatId}/subscribe")
    @Operation(summary = "Subscribe bot to chat webhook delivery")
    public Response subscribe(@PathParam("botId") String botIdStr,
                              @PathParam("chatId") String chatIdStr,
                              BotSubscribeRequest request,
                              @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var botId = UuidParams.required(botIdStr, "bot_id");
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var overrideUrl = request != null ? request.webhookUrl() : null;
        return switch (botService.subscribe(ownerId, botId, chatId, overrideUrl)) {
            case SUCCESS -> Response.status(Response.Status.CREATED).build();
            case NOT_FOUND -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.bot.not_found"))).build();
            case NOT_MEMBER -> Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.bot.not_chat_member"))).build();
            case NO_WEBHOOK -> badRequest("error.bot.no_webhook");
            case INVALID_WEBHOOK -> badRequest("error.bot.invalid_webhook");
            case PERSISTENCE_FAILED -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.bot.subscribe_failed"))).build();
        };
    }

    @DELETE
    @Path("/bots/{botId}/chats/{chatId}/subscribe")
    @Operation(summary = "Unsubscribe bot from chat")
    public Response unsubscribe(@PathParam("botId") String botIdStr,
                                @PathParam("chatId") String chatIdStr,
                                @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var botId = UuidParams.required(botIdStr, "bot_id");
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!botService.unsubscribe(ownerId, botId, chatId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.bot.subscription_not_found"))).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/bots/{botId}/token/rotate")
    @Operation(summary = "Rotate bot access token", description = "Returns new kbt_ token once; invalidates previous token")
    public Response rotateToken(@PathParam("botId") String botIdStr, @Context SecurityContext securityContext) {
        var ownerId = requireUserId(securityContext);
        var botId = UuidParams.required(botIdStr, "bot_id");
        return botService.rotateToken(ownerId, botId)
            .map(r -> Response.ok(r).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.bot.not_found"))).build());
    }

    @GET
    @Path("/bot/updates")
    @Operation(summary = "Long-poll bot updates", description = "Authorization: Bearer kbt_…")
    public Response pollUpdates(@QueryParam("offset") Long offset,
                                @QueryParam("timeout") Integer timeout,
                                @Context SecurityContext securityContext) {
        var botId = requireBotId(securityContext);
        var off = offset != null && offset >= 0 ? offset : 0L;
        var to = timeout != null ? timeout : 30;
        return Response.ok(botService.pollUpdates(botId, off, to)).build();
    }

    @DELETE
    @Path("/bot/messages/{messageId}")
    @Operation(summary = "Delete bot message", description = "Bot may delete its own messages only")
    public Response deleteBotMessage(@PathParam("messageId") String messageIdStr,
                                     @QueryParam("chat_id") String chatIdStr,
                                     @Context SecurityContext securityContext) {
        var botId = requireBotId(securityContext);
        if (chatIdStr == null || chatIdStr.isBlank()) {
            return badRequest("error.bot.chat_id_required");
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(messageIdStr, "message_id");
        if (!botService.deleteMessage(botId, chatId, msgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.bot.action_denied"))).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/bot/chats/{chatId}/messages/{messageId}/pin")
    @Operation(summary = "Pin message as bot", description = "Requires bot admin/owner role in chat")
    public Response pinBotMessage(@PathParam("chatId") String chatIdStr,
                                  @PathParam("messageId") String messageIdStr,
                                  @Context SecurityContext securityContext) {
        var botId = requireBotId(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(messageIdStr, "message_id");
        if (!botService.pinMessage(botId, chatId, msgId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.bot.action_denied"))).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/bot/chats/{chatId}/bans")
    @Operation(summary = "Ban user as bot", description = "Requires bot admin/owner role in chat")
    public Response banAsBot(@PathParam("chatId") String chatIdStr,
                             BotBanRequest request,
                             @Context SecurityContext securityContext) {
        var botId = requireBotId(securityContext);
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            return badRequest("error.bot.user_id_required");
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var targetId = UuidParams.required(request.userId(), "user_id");
        if (!botService.banUser(botId, chatId, targetId, request.reason())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.bot.action_denied"))).build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/bot/send")
    @Operation(summary = "Send message as bot", description = "Authorization: Bearer kbt_… bot access token")
    @ApiResponse(responseCode = "201", description = "Message sent")
    public Response sendAsBot(BotSendMessageRequest request, @Context SecurityContext securityContext) {
        var principal = securityContext.getUserPrincipal();
        if (!(principal instanceof BotPrincipal botPrincipal)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.bot.invalid_token"))).build();
        }
        if (request == null || request.chatId() == null || request.chatId().isBlank()) {
            return badRequest("error.bot.chat_id_required");
        }
        if (request.content() == null || request.content().isBlank()) {
            return badRequest("error.message.content_required");
        }
        var chatId = UuidParams.required(request.chatId(), "chat_id");
        var botUserId = UUID.fromString(botPrincipal.botId());
        var sendRequest = new SendMessageRequest(
            request.type() != null && !request.type().isBlank() ? request.type() : "text",
            request.content(),
            null,
            null,
            request.clientMsgId(),
            null,
            null,
            null,
            null);
        var msg = botService.sendMessage(botUserId, chatId, sendRequest);
        if (msg == null) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.send_denied.not_member"))).build();
        }
        return Response.status(Response.Status.CREATED).entity(msg).build();
    }

    private UUID requireUserId(SecurityContext securityContext) {
        return CurrentUserId.uuid(securityContext);
    }

    private UUID requireBotId(SecurityContext securityContext) {
        var principal = securityContext.getUserPrincipal();
        if (!(principal instanceof BotPrincipal botPrincipal)) {
            throw new jakarta.ws.rs.NotAuthorizedException("Bot token required");
        }
        return UUID.fromString(botPrincipal.botId());
    }

    private Response badRequest(String messageKey) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, messages.get(messageKey)))
            .build();
    }
}
