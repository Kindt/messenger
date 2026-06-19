package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.core.port.OrganizationLookupPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcOrganizationLookupAdapter implements OrganizationLookupPort {
    private final JdbcOrganizationJdbcRepository jdbc;
    private final OrganizationRepository legacy;

    public JdbcOrganizationLookupAdapter(JdbcOrganizationJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.legacy = null;
    }

    public JdbcOrganizationLookupAdapter(OrganizationRepository delegate) {
        this.jdbc = null;
        this.legacy = delegate;
    }

    public JdbcOrganizationLookupAdapter(DataSource dataSource, Clock clock,
                                         com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcOrganizationJdbcRepository(dataSource, clock, uuidGenerator);
        this.legacy = null;
    }

    @Override
    public boolean exists(UUID orgId) {
        return useLegacy() ? legacy.exists(orgId) : jdbc.exists(orgId);
    }

    @Override
    public Optional<OrgSummary> findById(UUID orgId) {
        if (useLegacy()) {
            return legacy.findById(orgId).map(JdbcOrganizationLookupAdapter::mapLegacy);
        }
        return jdbc.findById(orgId).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Optional<OrgSummary> findBySlug(String slug) {
        if (useLegacy()) {
            return legacy.findBySlug(slug).map(JdbcOrganizationLookupAdapter::mapLegacy);
        }
        return jdbc.findBySlug(slug).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Optional<OrgSummary> findSingle() {
        if (useLegacy()) {
            return legacy.findSingle().map(JdbcOrganizationLookupAdapter::mapLegacy);
        }
        return jdbc.findSingle().map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public List<OrgSummary> listAll() {
        if (useLegacy()) {
            return legacy.listAll().stream().map(JdbcOrganizationLookupAdapter::mapLegacy).toList();
        }
        return jdbc.listAll().stream().map(JdbcOrganizationLookupAdapter::map).toList();
    }

    private boolean useLegacy() {
        return legacy != null;
    }

    private static OrgSummary map(JdbcOrganizationJdbcRepository.OrgRow row) {
        return new OrgSummary(row.id(), row.name(), row.slug(), row.createdAt());
    }

    private static OrgSummary mapLegacy(OrganizationRepository.OrgRow row) {
        return new OrgSummary(row.id(), row.name(), row.slug(), row.createdAt());
    }
}
