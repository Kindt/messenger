package com.avandocmsg.messenger.api.crypto;

import com.avandocmsg.messenger.api.crypto.dto.KeyPackageResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcKeyPackageJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Legacy façade for E2EE key package JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcKeyPackageJdbcRepository}.
 */
public class KeyPackageRepository {
    private final JdbcKeyPackageJdbcRepository jdbc;

    public KeyPackageRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.jdbc = new JdbcKeyPackageJdbcRepository(dataSource, clock, uuidGenerator);
    }

    public JdbcKeyPackageJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public KeyPackageResponse insert(UUID userId, byte[] publicKey, byte[] signatureKey,
                                     String cipherSuite, String protocolVersion) {
        return jdbc.insert(userId, publicKey, signatureKey, cipherSuite, protocolVersion);
    }

    public List<KeyPackageResponse> findByUserId(UUID userId) {
        return jdbc.findByUserId(userId);
    }

    public boolean delete(UUID id) {
        return jdbc.delete(id);
    }
}
