package com.avandocmsg.messenger.api.admin.ui;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

/**
 * Удобная точка входа из API в встроенную веб-консоль (статика вне Jersey, {@code /admin/}).
 */
@Path("/v1/admin")
@Tag(name = "Admin UI", description = "Встроенная админ-консоль")
public class AdminConsoleRedirectResource {

    static URI webConsoleLocation(UriInfo uriInfo) {
        return UriBuilder.fromUri(uriInfo.getBaseUri()).replacePath("/admin/").build();
    }

    @GET
    @Path("console")
    @Operation(summary = "Открыть веб-админку",
        description = "Редирект **303** на **`/admin/`** (тот же хост и порт). Без JWT.")
    @ApiResponse(responseCode = "303", description = "Переход на встроенную консоль")
    public Response redirectToWebConsole(@Context UriInfo uriInfo) {
        return Response.seeOther(webConsoleLocation(uriInfo)).build();
    }
}
