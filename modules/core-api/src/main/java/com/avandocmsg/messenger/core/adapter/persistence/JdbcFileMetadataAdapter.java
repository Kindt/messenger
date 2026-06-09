package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link FileMetadataPort} (file metadata read). */
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
        var sql = "SELECT id, filename, mime_type, size, uploaded_by FROM file_metadata WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new StoredFile(
                        FileId.of(rs.getObject("id", UUID.class)),
                        rs.getString("filename"),
                        rs.getString("mime_type"),
                        rs.getLong("size"),
                        UserId.of(rs.getObject("uploaded_by", UUID.class))));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
