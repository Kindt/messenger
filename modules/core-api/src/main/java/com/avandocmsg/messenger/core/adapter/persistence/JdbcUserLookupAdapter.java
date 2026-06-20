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
    private final UserRepository testFacade;

    public JdbcUserLookupAdapter(JdbcUserJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.testFacade = null;
    }

    public JdbcUserLookupAdapter(UserRepository delegate) {
        this.jdbc = null;
        this.testFacade = delegate;
    }

    public JdbcUserLookupAdapter(DataSource dataSource) {
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
        this.testFacade = null;
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return usesTestFacade() ? testFacade.findById(id) : jdbc.findById(id);
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return usesTestFacade() ? testFacade.findByUsername(username) : jdbc.findByUsername(username);
    }

    @Override
    public boolean isReadReceiptsDisabled(UUID id) {
        return usesTestFacade() ? testFacade.isReadReceiptsDisabled(id) : jdbc.isReadReceiptsDisabled(id);
    }

    @Override
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return usesTestFacade() ? testFacade.searchForViewer(viewerId, query, limit) : jdbc.searchForViewer(viewerId, query, limit);
    }

    private boolean usesTestFacade() {
        return testFacade != null;
    }
}
