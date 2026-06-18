package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcScimGroupRepositoryAdapter;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScimGroupsResourceTest {

    private HikariDataSource ds;
    private ScimGroupRepositoryPort groupRepository;
    private ScimGroupsResource resource;
    private UriInfo uriInfo;
    private UUID orgId;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:scim_groups_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE scim_groups (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  display_name VARCHAR(256) NOT NULL,
                  external_id VARCHAR(256),
                  members_json TEXT NOT NULL DEFAULT '[]',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        groupRepository = new JdbcScimGroupRepositoryAdapter(ds);
        var appConfig = new AppConfig() {
            @Override
            public java.util.Optional<UUID> defaultOrgId() {
                return java.util.Optional.of(orgId);
            }
        };
        resource = new ScimGroupsResource(groupRepository, appConfig, UuidGenerator.standard());
        uriInfo = mock(UriInfo.class);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromPath("http://localhost/api/scim/v2/Groups"));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void create_get_patch_delete_roundTrip() throws Exception {
        var createBody = """
            {
              "displayName": "Engineering",
              "externalId": "ext-group-1",
              "members": [{"value": "11111111-1111-4111-8111-111111111111"}]
            }
            """;
        var created = resource.create(createBody, uriInfo);
        assertEquals(201, created.getStatus());
        var scimGroup = (ScimGroupResource) created.getEntity();
        assertEquals("Engineering", scimGroup.displayName());
        assertEquals("ext-group-1", scimGroup.externalId());
        assertNotNull(scimGroup.members());
        assertEquals(1, scimGroup.members().size());

        assertEquals(200, resource.get(scimGroup.id(), uriInfo).getStatus());

        var patchBody = """
            {
              "Operations": [
                {"op": "replace", "path": "displayName", "value": "Platform Engineering"},
                {"op": "add", "path": "members", "value": [{"value": "22222222-2222-4222-8222-222222222222"}]}
              ]
            }
            """;
        var patched = resource.patch(scimGroup.id(), patchBody, uriInfo);
        assertEquals(200, patched.getStatus());
        var updated = (ScimGroupResource) patched.getEntity();
        assertEquals("Platform Engineering", updated.displayName());
        assertEquals(2, updated.members().size());

        assertEquals(200, resource.list(1, 10, null, uriInfo).getStatus());

        assertEquals(204, resource.delete(scimGroup.id()).getStatus());
        assertTrue(groupRepository.findById(UUID.fromString(scimGroup.id())).isEmpty());
    }
}
