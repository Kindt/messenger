package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.core.application.ShellLayout;
import com.avandocmsg.messenger.core.port.UiBrandingPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUiBrandingAdapter implements UiBrandingPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcUiBrandingAdapter.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final TypeReference<Map<String, String>> TOKEN_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;

    public JdbcUiBrandingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PlatformBranding> getPlatform() {
        var sql = """
            SELECT id, palette, token_overrides, custom_css, brand_title,
                   demo_skins_enabled, shell_layout, revision, created_at, updated_at
            FROM platform_ui_branding WHERE id = 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcConnectionSupport.prepareRead(conn);
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlatformBranding(
                    rs.getLong("id"),
                    rs.getString("palette"),
                    parseTokens(rs.getString("token_overrides")),
                    rs.getString("custom_css"),
                    rs.getString("brand_title"),
                    rs.getBoolean("demo_skins_enabled"),
                    readShellLayout(rs.getString("shell_layout")),
                    rs.getLong("revision"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (Exception e) {
            log.error("load platform branding failed", e);
            return Optional.empty();
        }
    }

    @Override
    public PlatformBranding upsertPlatform(
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout
    ) {
        var layout = ShellLayout.validateRequired(shellLayout);
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            if (JdbcDialect.isPostgres(conn)) {
                upsertPlatformOnConflict(conn, palette, tokenOverrides, customCss, brandTitle, demoSkinsEnabled, layout);
            } else {
                upsertPlatformLegacy(conn, palette, tokenOverrides, customCss, brandTitle, demoSkinsEnabled, layout);
            }
            return getPlatform().orElseGet(() -> new PlatformBranding(
                1, palette, tokenOverrides, customCss, brandTitle, demoSkinsEnabled, layout, 1, null, null));
        } catch (Exception e) {
            log.error("upsert platform branding failed", e);
            return getPlatform().orElseGet(() -> new PlatformBranding(
                1, palette, tokenOverrides, customCss, brandTitle, demoSkinsEnabled, layout, 1, null, null));
        }
    }

    @Override
    public Optional<OrgBranding> getOrg(UUID orgId) {
        var sql = """
            SELECT org_id, palette, token_overrides, custom_css,
                   brand_title, shell_layout, revision, created_at, updated_at
            FROM org_ui_branding WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcConnectionSupport.prepareRead(conn);
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OrgBranding(
                    rs.getObject("org_id", UUID.class),
                    rs.getString("palette"),
                    parseTokens(rs.getString("token_overrides")),
                    rs.getString("custom_css"),
                    rs.getString("brand_title"),
                    rs.getString("shell_layout"),
                    rs.getLong("revision"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (Exception e) {
            log.error("load org branding failed orgId={}", orgId, e);
            return Optional.empty();
        }
    }

    @Override
    public OrgBranding upsertOrg(
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        String shellLayout
    ) {
        var layout = ShellLayout.validateOptional(shellLayout);
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            if (JdbcDialect.isPostgres(conn)) {
                upsertOrgOnConflict(conn, orgId, palette, tokenOverrides, customCss, brandTitle, layout);
            } else {
                upsertOrgLegacy(conn, orgId, palette, tokenOverrides, customCss, brandTitle, layout);
            }
            return getOrg(orgId).orElseGet(() -> new OrgBranding(
                orgId, palette, tokenOverrides, customCss, brandTitle, layout, 1, null, null));
        } catch (Exception e) {
            log.error("upsert org branding failed orgId={}", orgId, e);
            return getOrg(orgId).orElseGet(() -> new OrgBranding(
                orgId, palette, tokenOverrides, customCss, brandTitle, layout, 1, null, null));
        }
    }

    @Override
    public boolean deleteOrg(UUID orgId) {
        var sql = "DELETE FROM org_ui_branding WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcConnectionSupport.prepareWrite(conn);
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("delete org branding failed orgId={}", orgId, e);
            return false;
        }
    }

    @Override
    public MergedBranding mergeForOrg(UUID orgId, String logoUrl) {
        var platform = getPlatform().orElse(new PlatformBranding(
            1, "korus", Map.of(), null, null, false, ShellLayout.DEFAULT, 1, null, null));
        var org = orgId != null ? getOrg(orgId).orElse(null) : null;
        var shellLayout = org != null && org.shellLayout() != null && !org.shellLayout().isBlank()
            ? ShellLayout.validateRequired(org.shellLayout())
            : ShellLayout.normalize(platform.shellLayout());
        return new MergedBranding(
            orgId,
            org != null && org.palette() != null ? org.palette() : platform.palette(),
            mergeTokens(platform.tokenOverrides(), org != null ? org.tokenOverrides() : Map.of()),
            org != null && org.customCss() != null ? org.customCss() : platform.customCss(),
            org != null && org.brandTitle() != null ? org.brandTitle() : platform.brandTitle(),
            platform.demoSkinsEnabled(),
            shellLayout,
            org != null ? org.revision() : platform.revision(),
            logoUrl
        );
    }

    private static String readShellLayout(String raw) {
        try {
            return ShellLayout.validateRequired(raw);
        } catch (IllegalArgumentException ignored) {
            return ShellLayout.DEFAULT;
        }
    }

    private static Map<String, String> mergeTokens(Map<String, String> platform, Map<String, String> org) {
        var merged = new java.util.LinkedHashMap<String, String>();
        if (platform != null) {
            merged.putAll(platform);
        }
        if (org != null) {
            merged.putAll(org);
        }
        return merged;
    }

    private void upsertPlatformOnConflict(
        java.sql.Connection conn,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout
    ) throws Exception {
        var sql = """
            INSERT INTO platform_ui_branding (
                id, palette, token_overrides, custom_css, brand_title, demo_skins_enabled,
                shell_layout, revision, created_at, updated_at
            ) VALUES (1, ?, ?::jsonb, ?, ?, ?, ?, 1, now(), now())
            ON CONFLICT (id) DO UPDATE SET
                palette = EXCLUDED.palette,
                token_overrides = EXCLUDED.token_overrides,
                custom_css = EXCLUDED.custom_css,
                brand_title = EXCLUDED.brand_title,
                demo_skins_enabled = EXCLUDED.demo_skins_enabled,
                shell_layout = EXCLUDED.shell_layout,
                revision = platform_ui_branding.revision + 1,
                updated_at = now()
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, palette);
            stmt.setString(2, toJson(tokenOverrides));
            stmt.setString(3, customCss);
            stmt.setString(4, brandTitle);
            stmt.setBoolean(5, demoSkinsEnabled);
            stmt.setString(6, shellLayout);
            stmt.executeUpdate();
        }
    }

    private void upsertPlatformLegacy(
        java.sql.Connection conn,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        boolean demoSkinsEnabled,
        String shellLayout
    ) throws Exception {
        var updateSql = """
            UPDATE platform_ui_branding SET
                palette = ?,
                token_overrides = ?,
                custom_css = ?,
                brand_title = ?,
                demo_skins_enabled = ?,
                shell_layout = ?,
                revision = revision + 1,
                updated_at = now()
            WHERE id = 1
            """;
        try (var stmt = conn.prepareStatement(updateSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, palette);
            stmt.setString(2, toJson(tokenOverrides));
            stmt.setString(3, customCss);
            stmt.setString(4, brandTitle);
            stmt.setBoolean(5, demoSkinsEnabled);
            stmt.setString(6, shellLayout);
            if (stmt.executeUpdate() > 0) {
                return;
            }
        }
        var insertSql = """
            INSERT INTO platform_ui_branding (
                id, palette, token_overrides, custom_css, brand_title, demo_skins_enabled,
                shell_layout, revision, created_at, updated_at
            ) VALUES (1, ?, ?, ?, ?, ?, ?, 1, now(), now())
            """;
        try (var stmt = conn.prepareStatement(insertSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, palette);
            stmt.setString(2, toJson(tokenOverrides));
            stmt.setString(3, customCss);
            stmt.setString(4, brandTitle);
            stmt.setBoolean(5, demoSkinsEnabled);
            stmt.setString(6, shellLayout);
            stmt.executeUpdate();
        }
    }

    private void upsertOrgOnConflict(
        java.sql.Connection conn,
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        String shellLayout
    ) throws Exception {
        var sql = """
            INSERT INTO org_ui_branding (
                org_id, palette, token_overrides, custom_css, brand_title, shell_layout,
                revision, created_at, updated_at
            ) VALUES (?, ?, ?::jsonb, ?, ?, ?, 1, now(), now())
            ON CONFLICT (org_id) DO UPDATE SET
                palette = EXCLUDED.palette,
                token_overrides = EXCLUDED.token_overrides,
                custom_css = EXCLUDED.custom_css,
                brand_title = EXCLUDED.brand_title,
                shell_layout = EXCLUDED.shell_layout,
                revision = org_ui_branding.revision + 1,
                updated_at = now()
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            stmt.setString(2, palette);
            stmt.setString(3, toJson(tokenOverrides));
            stmt.setString(4, customCss);
            stmt.setString(5, brandTitle);
            stmt.setString(6, shellLayout);
            stmt.executeUpdate();
        }
    }

    private void upsertOrgLegacy(
        java.sql.Connection conn,
        UUID orgId,
        String palette,
        Map<String, String> tokenOverrides,
        String customCss,
        String brandTitle,
        String shellLayout
    ) throws Exception {
        var updateSql = """
            UPDATE org_ui_branding SET
                palette = ?,
                token_overrides = ?,
                custom_css = ?,
                brand_title = ?,
                shell_layout = ?,
                revision = revision + 1,
                updated_at = now()
            WHERE org_id = ?
            """;
        try (var stmt = conn.prepareStatement(updateSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, palette);
            stmt.setString(2, toJson(tokenOverrides));
            stmt.setString(3, customCss);
            stmt.setString(4, brandTitle);
            stmt.setString(5, shellLayout);
            stmt.setObject(6, orgId);
            if (stmt.executeUpdate() > 0) {
                return;
            }
        }
        var insertSql = """
            INSERT INTO org_ui_branding (
                org_id, palette, token_overrides, custom_css, brand_title, shell_layout,
                revision, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 1, now(), now())
            """;
        try (var stmt = conn.prepareStatement(insertSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            stmt.setString(2, palette);
            stmt.setString(3, toJson(tokenOverrides));
            stmt.setString(4, customCss);
            stmt.setString(5, brandTitle);
            stmt.setString(6, shellLayout);
            stmt.executeUpdate();
        }
    }

    private static String toJson(Map<String, String> tokenOverrides) {
        try {
            return MAPPER.writeValueAsString(tokenOverrides != null ? tokenOverrides : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, String> parseTokens(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        var candidate = json;
        try {
            if (candidate.startsWith("\"") && candidate.endsWith("\"")) {
                candidate = MAPPER.readValue(candidate, String.class);
            }
            var parsed = MAPPER.readValue(candidate, TOKEN_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            log.warn("parse token_overrides failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
