package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.files.dto.FileInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcFileJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcFileJdbcRepository.class);
    private final DataSource dataSource;

    public JdbcFileJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public FileInfoResponse insert(UUID id, String filename, String mimeType, long size, UUID uploadedBy) {
        var sql = "INSERT INTO file_metadata (id, filename, mime_type, size, uploaded_by, created_at) VALUES (?, ?, ?, ?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, filename);
            stmt.setString(3, mimeType);
            stmt.setLong(4, size);
            stmt.setObject(5, uploadedBy);
            stmt.executeUpdate();
            return new FileInfoResponse(id.toString(), filename, mimeType, size, uploadedBy.toString(), null);
        } catch (Exception e) {
            log.error("Failed to insert file metadata", e);
            return null;
        }
    }

    public Optional<FileInfoResponse> findById(UUID id) {
        var sql = "SELECT id, filename, mime_type, size, uploaded_by, created_at FROM file_metadata WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new FileInfoResponse(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("filename"),
                        rs.getString("mime_type"),
                        rs.getLong("size"),
                        rs.getObject("uploaded_by", UUID.class).toString(),
                        null));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find file {}", id, e);
        }
        return Optional.empty();
    }

    public boolean delete(UUID id) {
        var sql = "DELETE FROM file_metadata WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to delete file {}", id, e);
            return false;
        }
    }
}
