package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcFileJdbcRepository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for file metadata JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcFileJdbcRepository}.
 */
public class FileRepository {
    private final JdbcFileJdbcRepository jdbc;

    public FileRepository(DataSource dataSource) {
        this.jdbc = new JdbcFileJdbcRepository(dataSource);
    }

    public JdbcFileJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public FileInfoResponse insert(UUID id, String filename, String mimeType, long size, UUID uploadedBy) {
        return jdbc.insert(id, filename, mimeType, size, uploadedBy);
    }

    public Optional<FileInfoResponse> findById(UUID id) {
        return jdbc.findById(id);
    }

    public boolean delete(UUID id) {
        return jdbc.delete(id);
    }
}
