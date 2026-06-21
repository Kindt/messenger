package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.phase5.dto.CreateStickerPackRequest;
import com.avandocmsg.messenger.api.phase5.dto.GifSearchResponse;
import com.avandocmsg.messenger.api.phase5.dto.StickerPackResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;

@Path("/v1/stickers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Stickers", description = "Sticker packs and GIF catalog (ADR T02302)")
public class StickerCatalogResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public StickerCatalogResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GET
    @Path("packs")
    @Operation(summary = "List org sticker packs")
    public Response listPacks(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = service.listStickerPacks(userId).stream().map(StickerPackResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("packs")
    @Operation(summary = "Create sticker pack scaffold")
    public Response createPack(CreateStickerPackRequest request, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var name = request != null ? request.name() : null;
        return service.createStickerPack(userId, name)
            .map(row -> Response.status(Response.Status.CREATED).entity(StickerPackResponse.from(row)).build())
            .orElse(Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.phase5.invalid_request")))
                .build());
    }

    @GET
    @Path("gifs")
    @Operation(summary = "Search static GIF catalog")
    public Response searchGifs(@QueryParam("q") String query, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = service.searchGifs(userId, query).stream().map(GifSearchResponse::from).toList();
        return Response.ok(List.copyOf(rows)).build();
    }
}
