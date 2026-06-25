package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.FileBlob;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link FileMetadataPort}. */
public final class JdbcFileMetadataAdapter implements FileMetadataPort {
    private final DataSource dataSource;

    public JdbcFileMetadataAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<StoredFile> findById(FileId id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT id, filename, mime_type, size, uploaded_by, content_hash, storage_key
            FROM file_metadata WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size, UserId uploadedBy) {
        return insertWithStorage(id, filename, mimeType, size, uploadedBy, null, null);
    }

    @Override
    public Optional<StoredFile> insertWithStorage(FileId id, String filename, String mimeType, long size,
                                                  UserId uploadedBy, String contentHash, String storageKey) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            INSERT INTO file_metadata (id, filename, mime_type, size, uploaded_by, content_hash, storage_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id.value());
            stmt.setString(2, filename);
            stmt.setString(3, mimeType);
            stmt.setLong(4, size);
            stmt.setObject(5, uploadedBy.value());
            stmt.setString(6, contentHash);
            stmt.setString(7, storageKey);
            stmt.executeUpdate();
            return Optional.of(new StoredFile(id, filename, mimeType, size, uploadedBy, contentHash, storageKey));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(FileId id) {
        if (dataSource == null) {
            return false;
        }
        var sql = "DELETE FROM file_metadata WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<FileBlob> findBlobByContentHash(String contentHash) {
        if (dataSource == null || contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        var sql = "SELECT content_hash, storage_key, blob_size, ref_count FROM file_blob WHERE content_hash = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, contentHash);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new FileBlob(
                        rs.getString("content_hash"),
                        rs.getString("storage_key"),
                        rs.getLong("blob_size"),
                        rs.getInt("ref_count")));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public boolean insertBlob(String contentHash, String storageKey, long blobSize) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            INSERT INTO file_blob (content_hash, storage_key, blob_size, ref_count)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (content_hash) DO NOTHING
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, contentHash);
            stmt.setString(2, storageKey);
            stmt.setLong(3, blobSize);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean incrementBlobRefCount(String contentHash) {
        if (dataSource == null) {
            return false;
        }
        var sql = "UPDATE file_blob SET ref_count = ref_count + 1 WHERE content_hash = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, contentHash);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<Integer> decrementBlobRefCount(String contentHash) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            UPDATE file_blob SET ref_count = ref_count - 1
            WHERE content_hash = ? AND ref_count > 0
            RETURNING ref_count
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, contentHash);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt(1));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static StoredFile mapRow(java.sql.ResultSet rs) throws Exception {
        return new StoredFile(
            FileId.of(rs.getObject("id", UUID.class)),
            rs.getString("filename"),
            rs.getString("mime_type"),
            rs.getLong("size"),
            UserId.of(rs.getObject("uploaded_by", UUID.class)),
            rs.getString("content_hash"),
            rs.getString("storage_key"));
    }
}
