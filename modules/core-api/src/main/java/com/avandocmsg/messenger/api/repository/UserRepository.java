package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserJdbcRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for user JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcUserJdbcRepository}.
 */
public class UserRepository {
    private final JdbcUserJdbcRepository jdbc;

    public UserRepository(DataSource dataSource) {
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
    }

    public JdbcUserJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public void upsertFromKeycloak(UUID id, String username, String displayName) {
        jdbc.upsertFromKeycloak(id, username, displayName);
    }

    public boolean create(UUID id, String username, String displayName) {
        return jdbc.create(id, username, displayName);
    }

    public Optional<UserProfile> findById(UUID id) {
        return jdbc.findById(id);
    }

    public Optional<UserProfile> findByUsername(String username) {
        return jdbc.findByUsername(username);
    }

    public Optional<UserProfile> findByEmail(String email) {
        return jdbc.findByEmail(email);
    }

    public Optional<UserProfile> findByOrgAndExternalId(UUID orgId, String externalId) {
        return jdbc.findByOrgAndExternalId(orgId, externalId);
    }

    public List<UserProfile> listByOrg(UUID orgId, int offset, int limit) {
        return jdbc.listByOrg(orgId, offset, limit);
    }

    public int countByOrg(UUID orgId) {
        return jdbc.countByOrg(orgId);
    }

    public boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                       String email, String displayName) {
        return jdbc.upsertFromDirectory(id, orgId, externalId, username, email, displayName);
    }

    public boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                                  String externalId, String displayName, boolean active) {
        return jdbc.upsertFromScim(id, orgId, username, email, externalId, displayName, active);
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.setActive(id, active);
    }

    public boolean isReadReceiptsDisabled(UUID id) {
        return jdbc.isReadReceiptsDisabled(id);
    }

    public List<UserProfile> search(String query, int limit) {
        return jdbc.search(query, limit);
    }

    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return jdbc.searchForViewer(viewerId, query, limit);
    }
}
