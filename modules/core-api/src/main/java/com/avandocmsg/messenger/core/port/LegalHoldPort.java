package com.avandocmsg.messenger.core.port;

import java.util.Optional;
import java.util.UUID;

/** Legal hold flags for org and chat scopes. */
public interface LegalHoldPort {
    Optional<LegalHoldRow> findOrg(UUID orgId);

    Optional<LegalHoldRow> findChat(UUID chatId);

    boolean upsertOrg(UUID orgId, LegalHoldRow row, UUID updatedBy);

    boolean upsertChat(UUID chatId, LegalHoldRow row, UUID updatedBy);

    record LegalHoldRow(boolean legalHold, boolean legalHoldFiles, boolean legalHoldDeepArchive) {}
}
