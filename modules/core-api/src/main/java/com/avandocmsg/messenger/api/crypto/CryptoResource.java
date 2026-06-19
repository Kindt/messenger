package com.avandocmsg.messenger.api.crypto;

import com.avandocmsg.messenger.api.crypto.dto.KeyPackageResponse;
import com.avandocmsg.messenger.api.crypto.dto.MlsSessionInfoResponse;
import com.avandocmsg.messenger.api.crypto.dto.UploadKeyPackageRequest;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Base64;
import java.util.Map;

@Path("/v1/e2ee")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "E2EE", description = "Key packages и MLS-ориентированная сессия на сервере; без полного handshake RFC 9420 (см. javadoc MlsService)")
public class CryptoResource {

    private final E2EEService e2eeService;
    private final KeyPackageRepository keyPackageRepository;
    private final MlsService mlsService;
    private final MlsMigrationService mlsMigrationService;
    private final SessionRepository sessionRepository;
    private final ChatPersistencePort chatPersistencePort;
    private final UserMessageSource messages;

    @Inject
    public CryptoResource(E2EEService e2eeService, KeyPackageRepository keyPackageRepository,
                          MlsService mlsService, MlsMigrationService mlsMigrationService,
                          SessionRepository sessionRepository, ChatPersistencePort chatPersistencePort,
                          UserMessageSource messages) {
        this.e2eeService = e2eeService;
        this.keyPackageRepository = keyPackageRepository;
        this.mlsService = mlsService;
        this.mlsMigrationService = mlsMigrationService;
        this.sessionRepository = sessionRepository;
        this.chatPersistencePort = chatPersistencePort;
        this.messages = messages;
    }

    @POST
    @Path("/key-packages")
    @Operation(summary = "Upload key package", description = "Upload an MLS key package for the current user")
    @ApiResponse(responseCode = "201", description = "Key package created",
        content = @Content(schema = @Schema(implementation = KeyPackageResponse.class)))
    public Response uploadKeyPackage(UploadKeyPackageRequest request,
                                     @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var publicKey = Base64.getDecoder().decode(request.publicKeyBase64());
        var signatureKey = Base64.getDecoder().decode(request.signatureKeyBase64());
        var cs = request.cipherSuite() != null ? request.cipherSuite() : "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519";
        var pv = request.protocolVersion() != null ? request.protocolVersion() : "mls10";
        var kp = keyPackageRepository.insert(userId, publicKey, signatureKey, cs, pv);
        if (kp == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.crypto.key_package_create_failed")))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(kp).build();
    }

    @GET
    @Path("/key-packages")
    @Operation(summary = "List key packages", description = "List all key packages for the current user")
    @ApiResponse(responseCode = "200", description = "List of key packages",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = KeyPackageResponse.class))))
    public Response listKeyPackages(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var packages = keyPackageRepository.findByUserId(userId);
        return Response.ok(packages).build();
    }

    @DELETE
    @Path("/key-packages/{kpId}")
    @Operation(summary = "Delete key package", description = "Delete a key package by ID")
    @ApiResponse(responseCode = "204", description = "Key package deleted")
    public Response deleteKeyPackage(@PathParam("kpId") String kpIdStr) {
        keyPackageRepository.delete(UuidParams.required(kpIdStr, "key_package_id"));
        return Response.noContent().build();
    }

    @GET
    @Path("/mls/session/{chatId}")
    @Operation(summary = "MLS session for chat",
        description = "Returns session_id and epoch for client-side MLS encrypt (chat members only)")
    @ApiResponse(responseCode = "200", description = "Session info",
        content = @Content(schema = @Schema(implementation = MlsSessionInfoResponse.class)))
    public Response getMlsSession(@PathParam("chatId") String chatIdStr,
                                @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var role = chatPersistencePort.getMemberRole(chatId, userId);
        if (role == null || chatPersistencePort.isMemberBanned(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        if (mlsMigrationService != null) {
            mlsMigrationService.migrateToMls(chatId);
        }
        var sessionIdOpt = mlsService.ensureSession(chatId);
        if (sessionIdOpt.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.crypto.mls_session_failed")))
                .build();
        }
        var sessionOpt = sessionRepository.findLatestByChatId(chatId);
        if (sessionOpt.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.crypto.mls_session_failed")))
                .build();
        }
        var session = sessionOpt.get();
        return Response.ok(new MlsSessionInfoResponse(
            session.id().toString(), session.epoch())).build();
    }

    @POST
    @Path("/generate")
    @Operation(summary = "Generate key pair", description = "Generate a new X25519 key pair for MLS")
    @ApiResponse(responseCode = "200", description = "Generated key pair")
    public Response generateKeyPair() {
        var kp = e2eeService.generateKeyPair();
        if (kp == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        var signatureKey = e2eeService.randomBytes(32);
        return Response.ok(Map.of(
            "public_key", Base64.getEncoder().encodeToString(kp.publicKey()),
            "signature_key", Base64.getEncoder().encodeToString(signatureKey),
            "private_key", Base64.getEncoder().encodeToString(kp.privateKey())
        )).build();
    }
}
