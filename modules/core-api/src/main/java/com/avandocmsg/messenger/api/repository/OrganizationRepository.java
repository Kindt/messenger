package com.avandocmsg.messenger.api.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrganizationRepository {
    private static final Logger log = LoggerFactory.getLogger(OrganizationRepository.class);
    private final DataSource dataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public OrganizationRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    public OrgRow create(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        var id = uuidGenerator.randomUuid();
        var sql = "INSERT INTO organizations (id, name, created_at) VALUES (?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, name.trim());
            stmt.executeUpdate();
            return new OrgRow(id.toString(), name.trim(), null, clock.instant());
        } catch (Exception e) {
            log.error("create org failed", e);
            return null;
        }
    }

    public boolean exists(UUID id) {
        var sql = "SELECT 1 FROM organizations WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("exists org failed", e);
            return false;
        }
    }

    public Optional<OrgRow> findById(UUID id) {
        var sql = "SELECT id, name, slug, created_at FROM organizations WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OrgRow(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception e) {
            log.error("find org by id failed", e);
            return Optional.empty();
        }
    }

    /**
     * Удаление организации, если ни один пользователь не ссылается на неё через {@code users.org_id}.
     */
    public boolean deleteIfUnused(UUID orgId) {
        var sql = """
            DELETE FROM organizations o
            WHERE o.id = ?
              AND NOT EXISTS (SELECT 1 FROM users u WHERE u.org_id = o.id)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("delete org failed", e);
            return false;
        }
    }

    public List<OrgRow> listAll() {
        var sql = "SELECT id, name, slug, created_at FROM organizations ORDER BY name";
        var out = new ArrayList<OrgRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.add(new OrgRow(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception e) {
            log.error("list orgs failed", e);
        }
        return out;
    }

    public boolean setUserOrg(UUID userId, UUID orgId) {
        var sql = "UPDATE users SET org_id = ?, updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("set user org failed", e);
            return false;
        }
    }

    public Optional<OrgRow> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        var sql = "SELECT id, name, slug, created_at FROM organizations WHERE lower(slug) = lower(?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, slug.trim());
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("find org by slug failed", e);
            return Optional.empty();
        }
    }

    public Optional<OrgRow> findSingle() {
        var all = listAll();
        if (all.size() == 1) {
            return Optional.of(all.get(0));
        }
        return Optional.empty();
    }

    public boolean setSlug(UUID orgId, String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        var sql = "UPDATE organizations SET slug = ? WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, slug.trim().toLowerCase());
            stmt.setObject(2, orgId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("set org slug failed", e);
            return false;
        }
    }

    private static OrgRow mapRow(java.sql.ResultSet rs) throws Exception {
        return new OrgRow(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getTimestamp("created_at").toInstant());
    }

    public record OrgRow(String id, String name, String slug, Instant createdAt) {}
}
