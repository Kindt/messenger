package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.admin.dto.MigrationImportCreateRequest;
import com.avandocmsg.messenger.api.admin.dto.MigrationImportJobResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

@Path("/v1/admin/migration-import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin migration import", description = "Spec 022 US9 Telegram export scaffold")
public class AdminMigrationImportResource {

    private final MigrationImportJobRepository jobRepository;
    private final UserRepository userRepository;

    @Inject
    public AdminMigrationImportResource(DataSource dataSource, UserRepository userRepository) {
        this.jobRepository = new MigrationImportJobRepository(dataSource);
        this.userRepository = userRepository;
    }

    @POST
    @Operation(summary = "Enqueue migration import job")
    public Response create(MigrationImportCreateRequest body, @Context SecurityContext securityContext) {
        var actorId = CurrentUserId.uuid(securityContext);
        var profile = userRepository.findById(actorId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var orgId = UUID.fromString(profile.orgId());
        var source = body != null && body.source() != null ? body.source() : "telegram_export_v1";
        var config = body != null && body.configJson() != null ? body.configJson() : "{}";
        var id = jobRepository.insert(orgId, source, config, actorId);
        if (id == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED)
            .entity(jobRepository.findById(id).map(MigrationImportJobResponse::from).orElse(null))
            .build();
    }

    @GET
    @Operation(summary = "List migration import jobs for actor org")
    public Response list(@QueryParam("limit") Integer limit, @Context SecurityContext securityContext) {
        var actorId = CurrentUserId.uuid(securityContext);
        var profile = userRepository.findById(actorId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Response.ok(List.of()).build();
        }
        var orgId = UUID.fromString(profile.orgId());
        var rows = jobRepository.listForOrg(orgId, limit != null ? limit : 20);
        return Response.ok(rows.stream().map(MigrationImportJobResponse::from).toList()).build();
    }

    @GET
    @Path("/{jobId}")
    public Response get(@PathParam("jobId") String jobIdStr, @Context SecurityContext securityContext) {
        try {
            var id = UUID.fromString(jobIdStr);
            return jobRepository.findById(id)
                .map(row -> Response.ok(MigrationImportJobResponse.from(row)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
