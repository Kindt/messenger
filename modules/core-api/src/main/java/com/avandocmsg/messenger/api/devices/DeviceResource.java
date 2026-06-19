package com.avandocmsg.messenger.api.devices;

import com.avandocmsg.messenger.api.devices.dto.DeviceListResponse;
import com.avandocmsg.messenger.api.devices.dto.DeviceResponse;
import com.avandocmsg.messenger.api.devices.dto.RegisterDeviceRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.DevicePort;
import io.swagger.v3.oas.annotations.Operation;
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

@Path("/v1/me/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Devices", description = "Регистрация устройств и push-токенов")
public class DeviceResource {

    private final DevicePort devicePort;
    private final UserMessageSource messages;

    @Inject
    public DeviceResource(DevicePort devicePort, UserMessageSource messages) {
        this.devicePort = devicePort;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "List current user devices")
    @ApiResponse(responseCode = "200", description = "Device list",
        content = @Content(schema = @Schema(implementation = DeviceListResponse.class)))
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return Response.ok(new DeviceListResponse(devicePort.listForUser(userId))).build();
    }

    @DELETE
    @Path("{deviceName}")
    @Operation(summary = "Unregister device push token", description = "Clears push_token for the named device")
    @ApiResponse(responseCode = "204", description = "Push token cleared")
    @ApiResponse(responseCode = "404", description = "Device not found")
    public Response unregister(@PathParam("deviceName") String deviceName,
                               @Context SecurityContext securityContext) {
        if (deviceName == null || deviceName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.device.device_name_required")))
                .build();
        }
        var name = deviceName.trim();
        if (name.length() > 256) {
            name = name.substring(0, 256);
        }
        var userId = CurrentUserId.uuid(securityContext);
        if (!devicePort.clearPushToken(userId, name)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.device.not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    @POST
    @Operation(summary = "Register or update device push token", description = "Upsert by device_name for current user")
    @ApiResponse(responseCode = "200", description = "Device registered",
        content = @Content(schema = @Schema(implementation = DeviceResponse.class)))
    public Response register(RegisterDeviceRequest request, @Context SecurityContext securityContext) {
        if (request == null || request.pushToken() == null || request.pushToken().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.device.push_token_required")))
                .build();
        }
        var provider = request.pushProvider() != null && !request.pushProvider().isBlank()
            ? request.pushProvider().trim()
            : "web";
        var name = request.deviceName() != null && !request.deviceName().isBlank()
            ? request.deviceName().trim()
            : "web-client";
        if (name.length() > 256) {
            name = name.substring(0, 256);
        }
        var token = request.pushToken().trim();
        if (token.length() > 16_384) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.device.push_token_too_long")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var device = devicePort.upsertPushDevice(userId, name, provider, token);
        if (device == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.device.register_failed")))
                .build();
        }
        return Response.ok(device).build();
    }
}
