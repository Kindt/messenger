package com.avandocmsg.messenger.api.scim;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrgUserDirectoryAdapter;
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

class ScimUsersResourceTest {

    private HikariDataSource ds;
    private UserRepository userRepository;
    private JdbcOrgUserDirectoryAdapter userDirectory;
    private ScimUsersResource resource;
    private UriInfo uriInfo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:scim_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL UNIQUE,
                  display_name VARCHAR(128) NOT NULL,
                  email VARCHAR(256),
                  external_id VARCHAR(256),
                  phone VARCHAR(20),
                  hidden BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
                  last_seen_at TIMESTAMP,
                  org_id UUID,
                  privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false,
                  ui_locale VARCHAR(8),
                  custom_status_text VARCHAR(128) NOT NULL DEFAULT '',
                  dnd_until TIMESTAMP
                )
                """);
        }
        userRepository = new UserRepository(ds);
        userDirectory = new JdbcOrgUserDirectoryAdapter(userRepository);
        resource = new ScimUsersResource(userDirectory, new AppConfig(), UuidGenerator.standard());
        uriInfo = mock(UriInfo.class);
        when(uriInfo.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromPath("http://localhost/api/scim/v2/Users"));
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
              "userName": "scim-user",
              "externalId": "ext-scim-1",
              "active": true,
              "emails": [{"value": "scim-user@example.com", "primary": true}],
              "displayName": "SCIM User"
            }
            """;
        var created = resource.create(createBody, uriInfo);
        assertEquals(201, created.getStatus());
        var scimUser = (ScimUserResource) created.getEntity();
        assertEquals("scim-user", scimUser.userName());
        assertEquals("ext-scim-1", scimUser.externalId());
        assertTrue(scimUser.active());

        assertEquals(200, resource.get(scimUser.id(), uriInfo).getStatus());

        var patchBody = """
            {"Operations":[{"op":"replace","path":"active","value":false}]}
            """;
        var patched = resource.patch(scimUser.id(), patchBody, uriInfo);
        assertEquals(200, patched.getStatus());
        assertFalse(((ScimUserResource) patched.getEntity()).active());

        assertEquals(204, resource.delete(scimUser.id()).getStatus());
        var profile = userRepository.findById(UUID.fromString(scimUser.id())).orElseThrow();
        assertTrue(profile.hidden());
    }
}
