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
    public Optional<OrgDirectoryUser> findById(UUID id) {
        return userRepository.findById(id).map(JdbcOrgUserDirectoryAdapter::toDirectoryUser);
    }

    @Override
    public List<OrgDirectoryUser> listByOrg(UUID orgId, int offset, int limit) {
        return userRepository.listByOrg(orgId, offset, limit).stream()
            .map(JdbcOrgUserDirectoryAdapter::toDirectoryUser)
            .toList();
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

    private static OrgDirectoryUser toDirectoryUser(UserProfile profile) {
        return new OrgDirectoryUser(
            UUID.fromString(profile.id()),
            profile.username(),
            profile.displayName(),
            profile.email(),
            profile.externalId(),
            profile.hidden(),
            profile.orgId() != null ? UUID.fromString(profile.orgId()) : null);
    }
}
