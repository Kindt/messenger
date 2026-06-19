package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.core.port.OrganizationLookupPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcOrganizationLookupAdapter implements OrganizationLookupPort {
    private final OrganizationRepository delegate;

    public JdbcOrganizationLookupAdapter(OrganizationRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcOrganizationLookupAdapter(DataSource dataSource, Clock clock,
                                         com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.delegate = new OrganizationRepository(dataSource, clock, uuidGenerator);
    }

    @Override
    public boolean exists(UUID orgId) {
        return delegate.exists(orgId);
    }

    @Override
    public Optional<OrgSummary> findById(UUID orgId) {
        return delegate.findById(orgId).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Optional<OrgSummary> findBySlug(String slug) {
        return delegate.findBySlug(slug).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Optional<OrgSummary> findSingle() {
        return delegate.findSingle().map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public List<OrgSummary> listAll() {
        return delegate.listAll().stream().map(JdbcOrganizationLookupAdapter::map).toList();
    }

    private static OrgSummary map(OrganizationRepository.OrgRow row) {
        return new OrgSummary(row.id(), row.name(), row.slug(), row.createdAt());
    }
}
