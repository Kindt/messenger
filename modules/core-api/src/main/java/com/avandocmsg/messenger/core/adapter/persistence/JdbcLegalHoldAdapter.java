package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.LegalHoldRepository;
import com.avandocmsg.messenger.core.port.LegalHoldPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcLegalHoldAdapter implements LegalHoldPort {
    private final LegalHoldRepository delegate;

    public JdbcLegalHoldAdapter(LegalHoldRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcLegalHoldAdapter(DataSource dataSource) {
        this.delegate = new LegalHoldRepository(dataSource);
    }

    @Override
    public Optional<LegalHoldRow> findOrg(UUID orgId) {
        return delegate.findOrg(orgId).map(JdbcLegalHoldAdapter::map);
    }

    @Override
    public Optional<LegalHoldRow> findChat(UUID chatId) {
        return delegate.findChat(chatId).map(JdbcLegalHoldAdapter::map);
    }

    @Override
    public boolean upsertOrg(UUID orgId, LegalHoldRow row, UUID updatedBy) {
        return delegate.upsertOrg(orgId, map(row), updatedBy);
    }

    @Override
    public boolean upsertChat(UUID chatId, LegalHoldRow row, UUID updatedBy) {
        return delegate.upsertChat(chatId, map(row), updatedBy);
    }

    private static LegalHoldRow map(LegalHoldRepository.LegalHoldRow row) {
        return new LegalHoldRow(row.legalHold(), row.legalHoldFiles(), row.legalHoldDeepArchive());
    }

    private static LegalHoldRepository.LegalHoldRow map(LegalHoldRow row) {
        return new LegalHoldRepository.LegalHoldRow(row.legalHold(), row.legalHoldFiles(), row.legalHoldDeepArchive());
    }
}
