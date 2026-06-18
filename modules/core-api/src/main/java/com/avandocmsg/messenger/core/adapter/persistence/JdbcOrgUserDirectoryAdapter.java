package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link OrgUserDirectoryPort} (delegates to legacy {@link UserRepository}). */
public final class JdbcOrgUserDirectoryAdapter implements OrgUserDirectoryPort {

    private final UserRepository userRepository;

    public JdbcOrgUserDirectoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public List<UserProfile> listByOrg(UUID orgId, int offset, int limit) {
        return userRepository.listByOrg(orgId, offset, limit);
    }

    @Override
    public int countByOrg(UUID orgId) {
        return userRepository.countByOrg(orgId);
    }

    @Override
    public boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                       String email, String displayName) {
        return userRepository.upsertFromDirectory(id, orgId, externalId, username, email, displayName);
    }

    @Override
    public boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                                  String externalId, String displayName, boolean active) {
        return userRepository.upsertFromScim(id, orgId, username, email, externalId, displayName, active);
    }

    @Override
    public boolean setActive(UUID id, boolean active) {
        return userRepository.setActive(id, active);
    }
}
