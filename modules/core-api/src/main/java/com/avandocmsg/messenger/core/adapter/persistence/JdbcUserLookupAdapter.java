package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUserLookupAdapter implements UserLookupPort {
    private final JdbcUserJdbcRepository jdbc;
    private final UserRepository legacy;

    public JdbcUserLookupAdapter(JdbcUserJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.legacy = null;
    }

    public JdbcUserLookupAdapter(UserRepository delegate) {
        this.jdbc = null;
        this.legacy = delegate;
    }

    public JdbcUserLookupAdapter(DataSource dataSource) {
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
        this.legacy = null;
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return useLegacy() ? legacy.findById(id) : jdbc.findById(id);
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return useLegacy() ? legacy.findByUsername(username) : jdbc.findByUsername(username);
    }

    @Override
    public boolean isReadReceiptsDisabled(UUID id) {
        return useLegacy() ? legacy.isReadReceiptsDisabled(id) : jdbc.isReadReceiptsDisabled(id);
    }

    @Override
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return useLegacy() ? legacy.searchForViewer(viewerId, query, limit) : jdbc.searchForViewer(viewerId, query, limit);
    }

    private boolean useLegacy() {
        return legacy != null;
    }
}
