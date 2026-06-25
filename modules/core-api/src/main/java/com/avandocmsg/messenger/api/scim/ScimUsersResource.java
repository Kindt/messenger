package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.UUID;

@Path("/scim/v2/Users")
@Produces("application/scim+json")
@Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
@RolesAllowed("admin")
public class ScimUsersResource {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final OrgUserDirectoryPort userDirectory;
    private final AppConfig appConfig;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ScimUsersResource(OrgUserDirectoryPort userDirectory, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.userDirectory = userDirectory;
        this.appConfig = appConfig;
        this.uuidGenerator = uuidGenerator;
    }

    @GET
    public Response list(
        @QueryParam("startIndex") Integer startIndex,
        @QueryParam("count") Integer count,
        @QueryParam("filter") String filter,
        @Context UriInfo uriInfo
    ) {
        var orgId = ScimUserMapper.defaultOrgId(appConfig);
        var start = startIndex != null && startIndex > 0 ? startIndex : 1;
        var pageSize = count != null && count > 0 ? Math.min(count, 200) : 100;
        var offset = start - 1;
        var profiles = userDirectory.listByOrg(orgId, offset, pageSize);
        if (filter != null && filter.contains("userName eq")) {
            var userName = extractEqValue(filter, "userName");
            if (userName != null) {
                profiles = profiles.stream()
                    .filter(p -> userName.equalsIgnoreCase(p.username()))
                    .toList();
            }
        }
        var total = userDirectory.countByOrg(orgId);
        var base = baseLocation(uriInfo);
        return Response.ok(ScimUserMapper.toList(profiles, total, start, base)).build();
    }

    @GET
    @Path("{id}")
    public Response get(@PathParam("id") String id, @Context UriInfo uriInfo) {
        var userId = parseUuid(id);
        var profile = userDirectory.findById(userId)
            .orElseThrow(NotFoundException::new);
        return Response.ok(ScimUserMapper.toResource(profile, baseLocation(uriInfo))).build();
    }

    @POST
    public Response create(String body, @Context UriInfo uriInfo) throws Exception {
        var node = MAPPER.readTree(body);
        var parsed = ScimUserMapper.parseCreate(node);
        if (parsed.userName() == null || parsed.userName().isBlank()) {
            return Response.status(400).entity(scimError(400, "userName required")).build();
        }
        var orgId = ScimUserMapper.defaultOrgId(appConfig);
        var id = uuidGenerator.randomUuid();
        if (!userDirectory.upsertFromScim(
            id, orgId, parsed.userName(), parsed.email(), parsed.externalId(), parsed.displayName(), parsed.active())) {
            return Response.status(409).entity(scimError(409, "user create failed")).build();
        }
        var profile = userDirectory.findById(id).orElseThrow();
        var location = baseLocation(uriInfo) + profile.id();
        return Response.status(201).entity(ScimUserMapper.toResource(profile, baseLocation(uriInfo)))
            .header("Location", location)
            .build();
    }

    @PATCH
    @Path("{id}")
    public Response patch(@PathParam("id") String id, String body, @Context UriInfo uriInfo) throws Exception {
        var userId = parseUuid(id);
        var existing = userDirectory.findById(userId).orElseThrow(NotFoundException::new);
        var node = MAPPER.readTree(body);
        var patch = ScimUserMapper.parsePatch(node);
        var userName = patch.userName() != null ? patch.userName() : existing.username();
        var displayName = patch.displayName() != null ? patch.displayName() : existing.displayName();
        var active = patch.active() != null ? patch.active() : !existing.hidden();
        var orgId = existing.orgId() != null ? existing.orgId() : ScimUserMapper.defaultOrgId(appConfig);
        userDirectory.upsertFromScim(userId, orgId, userName, patch.email(), patch.externalId(), displayName, active);
        var updated = userDirectory.findById(userId).orElseThrow(NotFoundException::new);
        return Response.ok(ScimUserMapper.toResource(updated, baseLocation(uriInfo))).build();
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        var userId = parseUuid(id);
        if (userDirectory.findById(userId).isEmpty()) {
            throw new NotFoundException();
        }
        userDirectory.setActive(userId, false);
        return Response.noContent().build();
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            throw new NotFoundException();
        }
    }

    private static String baseLocation(UriInfo uriInfo) {
        return uriInfo.getAbsolutePathBuilder().path("").build().toString().replaceAll("/$", "") + "/";
    }

    private static String extractEqValue(String filter, String field) {
        var needle = field + " eq \"";
        var idx = filter.indexOf(needle);
        if (idx < 0) {
            needle = field + " eq ";
            idx = filter.indexOf(needle);
            if (idx < 0) {
                return null;
            }
            var rest = filter.substring(idx + needle.length()).trim();
            if (rest.startsWith("\"")) {
                rest = rest.substring(1);
            }
            var end = rest.indexOf('"');
            return end >= 0 ? rest.substring(0, end) : rest;
        }
        var rest = filter.substring(idx + needle.length());
        var end = rest.indexOf('"');
        return end >= 0 ? rest.substring(0, end) : null;
    }

    private static Object scimError(int status, String detail) {
        return java.util.Map.of(
            "schemas", java.util.List.of("urn:ietf:params:scim:api:messages:2.0:Error"),
            "status", String.valueOf(status),
            "detail", detail);
    }
}
