package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link OrgUserDirectoryPort}. */
public final class JdbcOrgUserDirectoryAdapter implements OrgUserDirectoryPort {

    private final JdbcUserJdbcRepository jdbc;
    private final UserRepository testFacade;

    public JdbcOrgUserDirectoryAdapter(JdbcUserJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.testFacade = null;
    }

    public JdbcOrgUserDirectoryAdapter(UserRepository userRepository) {
        this.jdbc = null;
        this.testFacade = userRepository;
    }

    public JdbcOrgUserDirectoryAdapter(DataSource dataSource) {
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
        this.testFacade = null;
    }

    @Override
    public Optional<OrgDirectoryUser> findById(UUID id) {
        return usesTestFacade()
            ? testFacade.findById(id).map(JdbcOrgUserDirectoryAdapter::toDirectoryUser)
            : jdbc.findById(id).map(JdbcOrgUserDirectoryAdapter::toDirectoryUser);
    }

    @Override
    public List<OrgDirectoryUser> listByOrg(UUID orgId, int offset, int limit) {
        return usesTestFacade()
            ? testFacade.listByOrg(orgId, offset, limit).stream().map(JdbcOrgUserDirectoryAdapter::toDirectoryUser).toList()
            : jdbc.listByOrg(orgId, offset, limit).stream().map(JdbcOrgUserDirectoryAdapter::toDirectoryUser).toList();
    }

    @Override
    public int countByOrg(UUID orgId) {
        return usesTestFacade() ? testFacade.countByOrg(orgId) : jdbc.countByOrg(orgId);
    }

    @Override
    public boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                       String email, String displayName) {
        return usesTestFacade()
            ? testFacade.upsertFromDirectory(id, orgId, externalId, username, email, displayName)
            : jdbc.upsertFromDirectory(id, orgId, externalId, username, email, displayName);
    }

    @Override
    public boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                                  String externalId, String displayName, boolean active) {
        return usesTestFacade()
            ? testFacade.upsertFromScim(id, orgId, username, email, externalId, displayName, active)
            : jdbc.upsertFromScim(id, orgId, username, email, externalId, displayName, active);
    }

    @Override
    public boolean setActive(UUID id, boolean active) {
        return usesTestFacade() ? testFacade.setActive(id, active) : jdbc.setActive(id, active);
    }

    private boolean usesTestFacade() {
        return testFacade != null;
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
