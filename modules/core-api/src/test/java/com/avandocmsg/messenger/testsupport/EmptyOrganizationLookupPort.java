package com.avandocmsg.messenger.testsupport;

import com.avandocmsg.messenger.core.port.OrganizationLookupPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** No-op {@link OrganizationLookupPort} for unit tests. */
public class EmptyOrganizationLookupPort implements OrganizationLookupPort {

    @Override
    public boolean exists(UUID orgId) {
        return false;
    }

    @Override
    public Optional<OrgSummary> findById(UUID orgId) {
        return Optional.empty();
    }

    @Override
    public Optional<OrgSummary> findBySlug(String slug) {
        return Optional.empty();
    }

    @Override
    public Optional<OrgSummary> findSingle() {
        return Optional.empty();
    }

    @Override
    public List<OrgSummary> listAll() {
        return List.of();
    }
}
