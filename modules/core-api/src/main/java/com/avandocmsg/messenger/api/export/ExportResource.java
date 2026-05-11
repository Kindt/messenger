package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * Queues a chat export job for {@link com.avandocmsg.messenger.worker.exportreplay.ExportReplayWorker}.
 */
@Path("/v1/chats/{chatId}/export")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Export", description = "Export / compliance replay job enqueue")
public class ExportResource {
    private static final Logger log = LoggerFactory.getLogger(ExportResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatRepository chatRepository;
    private final NatsOutboundPort natsOutbound;
    private final UuidGenerator uuidGenerator;
    private final UserMessageSource messages;

    @Inject
    public ExportResource(ChatRepository chatRepository, NatsOutboundPort natsOutbound,
                          UuidGenerator uuidGenerator, UserMessageSource messages) {
        this.chatRepository = chatRepository;
        this.natsOutbound = natsOutbound;
        this.uuidGenerator = uuidGenerator;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Request chat export",
        description = "Ставит в очередь задачу на **msg.export.replay** (JSON **ExportReplayJob**: job_id, chat_id, requested_by). "
            + "Доступ: участник чата с ролью **owner** или **admin**, не **banned**. "
            + "Воркер **export-replay** сейчас пишет stub-файл и опционально **msg.export.replay.complete** — полный GDPR-бандл не заявлен. "
            + "Ответ **202** с **job_id**.")
    @ApiResponse(responseCode = "202", description = "Job accepted",
        content = @Content(schema = @Schema(implementation = ExportAcceptedResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Chat or membership not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response requestExport(@PathParam("chatId") String chatIdStr,
                                  @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");

        var role = chatRepository.getMemberRole(chatId, userId);
        if (role == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.export.chat_not_found_or_member")))
                .build();
        }
        if (chatRepository.isMemberBanned(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.export.banned")))
                .build();
        }
        if (!"owner".equals(role) && !"admin".equals(role)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.export.role_required")))
                .build();
        }

        var jobId = uuidGenerator.randomUuid().toString();
        var job = new ExportReplayJob(jobId, chatIdStr, userId.toString());
        try {
            byte[] payload = MAPPER.writeValueAsBytes(job);
            natsOutbound.publish(NatsSubjects.MSG_EXPORT_REPLAY, payload);
            natsOutbound.flush(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.error("Failed to publish export job {}", jobId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.export.queue_failed")))
                .build();
        }

        return Response.status(Response.Status.ACCEPTED)
            .entity(new ExportAcceptedResponse(jobId, chatIdStr, "accepted"))
            .build();
    }
}
