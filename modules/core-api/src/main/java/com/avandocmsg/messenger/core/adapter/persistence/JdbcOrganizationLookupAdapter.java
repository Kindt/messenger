package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.OrganizationLookupPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JdbcOrganizationLookupAdapter implements OrganizationLookupPort {

    private final JdbcOrganizationJdbcRepository jdbc;

    public JdbcOrganizationLookupAdapter(JdbcOrganizationJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcOrganizationLookupAdapter(DataSource dataSource, Clock clock,
                                         com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcOrganizationJdbcRepository(dataSource, clock, uuidGenerator);
    }

    @Override
    public boolean exists(UUID orgId) {
        return jdbc.exists(orgId);
    }

    @Override
    public Optional<OrgSummary> findById(UUID orgId) {
        return jdbc.findById(orgId).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Map<UUID, OrgSummary> findByIds(Collection<UUID> orgIds) {
        return jdbc.findByIds(orgIds).entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> map(e.getValue())));
    }

    @Override
    public Optional<OrgSummary> findBySlug(String slug) {
        return jdbc.findBySlug(slug).map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public Optional<OrgSummary> findSingle() {
        return jdbc.findSingle().map(JdbcOrganizationLookupAdapter::map);
    }

    @Override
    public List<OrgSummary> listAll() {
        return jdbc.listAll().stream().map(JdbcOrganizationLookupAdapter::map).toList();
    }

    private static OrgSummary map(JdbcOrganizationJdbcRepository.OrgRow row) {
        return new OrgSummary(row.id(), row.name(), row.slug(), row.createdAt());
    }
}
