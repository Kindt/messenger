package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
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

@Path("/scim/v2/Groups")
@Produces("application/scim+json")
@Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
@RolesAllowed("admin")
public class ScimGroupsResource {
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final ScimGroupRepositoryPort groupRepository;
    private final AppConfig appConfig;
    private final UuidGenerator uuidGenerator;

    @Inject
    public ScimGroupsResource(ScimGroupRepositoryPort groupRepository, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.groupRepository = groupRepository;
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
        var orgId = ScimGroupMapper.defaultOrgId(appConfig);
        var start = startIndex != null && startIndex > 0 ? startIndex : 1;
        var pageSize = count != null && count > 0 ? Math.min(count, 200) : 100;
        var offset = start - 1;
        var rows = groupRepository.listByOrg(orgId, offset, pageSize);
        if (filter != null && filter.contains("displayName eq")) {
            var displayName = extractEqValue(filter, "displayName");
            if (displayName != null) {
                rows = rows.stream()
                    .filter(r -> displayName.equalsIgnoreCase(r.displayName()))
                    .toList();
            }
        }
        var total = groupRepository.countByOrg(orgId);
        var base = baseLocation(uriInfo);
        return Response.ok(ScimGroupMapper.toList(rows, total, start, base)).build();
    }

    @GET
    @Path("{id}")
    public Response get(@PathParam("id") String id, @Context UriInfo uriInfo) {
        var groupId = parseUuid(id);
        var row = groupRepository.findById(groupId).orElseThrow(NotFoundException::new);
        return Response.ok(ScimGroupMapper.toResource(row, baseLocation(uriInfo))).build();
    }

    @POST
    public Response create(String body, @Context UriInfo uriInfo) throws Exception {
        var node = MAPPER.readTree(body);
        var parsed = ScimGroupMapper.parseCreate(node);
        if (parsed.displayName() == null || parsed.displayName().isBlank()) {
            return Response.status(400).entity(scimError(400, "displayName required")).build();
        }
        var orgId = ScimGroupMapper.defaultOrgId(appConfig);
        var id = uuidGenerator.randomUuid();
        var membersJson = ScimGroupMapper.membersJson(parsed.memberIds());
        if (!groupRepository.insert(id, orgId, parsed.displayName(), parsed.externalId(), membersJson)) {
            return Response.status(409).entity(scimError(409, "group create failed")).build();
        }
        var row = groupRepository.findById(id).orElseThrow();
        var location = baseLocation(uriInfo) + row.id();
        return Response.status(201).entity(ScimGroupMapper.toResource(row, baseLocation(uriInfo)))
            .header("Location", location)
            .build();
    }

    @PATCH
    @Path("{id}")
    public Response patch(@PathParam("id") String id, String body, @Context UriInfo uriInfo) throws Exception {
        var groupId = parseUuid(id);
        var existing = groupRepository.findById(groupId).orElseThrow(NotFoundException::new);
        var node = MAPPER.readTree(body);
        var existingMembers = ScimGroupMapper.membersFromJson(existing.membersJson());
        var patch = ScimGroupMapper.parsePatch(node, existingMembers);
        var displayName = patch.displayName() != null ? patch.displayName() : existing.displayName();
        var externalId = patch.externalId() != null ? patch.externalId() : existing.externalId();
        var membersJson = ScimGroupMapper.membersJson(patch.memberIds());
        groupRepository.update(groupId, displayName, externalId, membersJson);
        var updated = groupRepository.findById(groupId).orElseThrow(NotFoundException::new);
        return Response.ok(ScimGroupMapper.toResource(updated, baseLocation(uriInfo))).build();
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        var groupId = parseUuid(id);
        if (groupRepository.findById(groupId).isEmpty()) {
            throw new NotFoundException();
        }
        groupRepository.delete(groupId);
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
