package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUserLookupAdapter implements UserLookupPort {

    private final JdbcUserJdbcRepository jdbc;

    public JdbcUserLookupAdapter(JdbcUserJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcUserLookupAdapter(DataSource dataSource) {
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return jdbc.findById(id);
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return jdbc.findByUsername(username);
    }

    @Override
    public Optional<UserProfile> findByExternalId(String externalId) {
        return jdbc.findByExternalId(externalId);
    }

    @Override
    public boolean isReadReceiptsDisabled(UUID id) {
        return jdbc.isReadReceiptsDisabled(id);
    }

    @Override
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return jdbc.searchForViewer(viewerId, query, limit);
    }
}
