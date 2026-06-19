package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcLegalHoldAdapter;
import com.avandocmsg.messenger.core.port.LegalHoldPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for legal hold JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcLegalHoldAdapter}.
 */
public class LegalHoldRepository {
    private final LegalHoldPort port;

    public LegalHoldRepository(DataSource dataSource) {
        this.port = new JdbcLegalHoldAdapter(dataSource);
    }

    LegalHoldRepository(LegalHoldPort port) {
        this.port = port;
    }

    public Optional<LegalHoldRow> findOrg(UUID orgId) {
        return port.findOrg(orgId).map(LegalHoldRepository::map);
    }

    public Optional<LegalHoldRow> findChat(UUID chatId) {
        return port.findChat(chatId).map(LegalHoldRepository::map);
    }

    public boolean upsertOrg(UUID orgId, LegalHoldRow row, UUID updatedBy) {
        return port.upsertOrg(orgId, map(row), updatedBy);
    }

    public boolean upsertChat(UUID chatId, LegalHoldRow row, UUID updatedBy) {
        return port.upsertChat(chatId, map(row), updatedBy);
    }

    private static LegalHoldRow map(LegalHoldPort.LegalHoldRow row) {
        return new LegalHoldRow(row.legalHold(), row.legalHoldFiles(), row.legalHoldDeepArchive());
    }

    private static LegalHoldPort.LegalHoldRow map(LegalHoldRow row) {
        return new LegalHoldPort.LegalHoldRow(row.legalHold(), row.legalHoldFiles(), row.legalHoldDeepArchive());
    }

    public record LegalHoldRow(boolean legalHold, boolean legalHoldFiles, boolean legalHoldDeepArchive) {
    }
}
