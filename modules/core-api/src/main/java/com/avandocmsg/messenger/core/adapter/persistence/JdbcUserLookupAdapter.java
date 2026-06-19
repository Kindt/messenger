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
    private final UserRepository delegate;

    public JdbcUserLookupAdapter(UserRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcUserLookupAdapter(DataSource dataSource) {
        this.delegate = new UserRepository(dataSource);
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return delegate.findByUsername(username);
    }

    @Override
    public boolean isReadReceiptsDisabled(UUID id) {
        return delegate.isReadReceiptsDisabled(id);
    }

    @Override
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return delegate.searchForViewer(viewerId, query, limit);
    }
}
