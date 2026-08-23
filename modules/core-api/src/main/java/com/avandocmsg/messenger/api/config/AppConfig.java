package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.core.port.ReadCacheKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String PROP_FILE_RESIZE_ENABLED = "file.resize.enabled";
    private static final String PROP_HOTPLUG_HEARTBEAT_TTL_MS = "hotplug.heartbeat.ttl.ms";
    private static final String ENV_FLEET_PROBE_TIMEOUT_MS = "FLEET_PROBE_TIMEOUT_MS";
    private static final String DEFAULT_AVANDOCMSG = "avandocmsg";
    private static final String STRING_FALSE = "false";
    private static final String DEFAULT_ADMIN = "admin";
    private final Properties props;

    public AppConfig() {
        this.props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            log.warn("Cannot load application.properties, using env defaults", e);
        }
        overrideFromEnv();
    }

    private void overrideFromEnv() {
        override("APP_PORT", "server.port");
        override("SECURITY_TIMING_NORMALIZATION_MIN_MS", "security.timing.normalization.min.ms");
        override("SECURITY_TIMING_NOT_FOUND_EXTRA_MS", "security.timing.not_found.extra.ms");
        override("SECURITY_TIMING_AUTH_FAILURE_EXTRA_MS", "security.timing.auth_failure.extra.ms");
        override("APP_HOME", "app.home");
        override("DB_JDBC_URL", "db.jdbc.url");
        override("DB_USER", "db.user");
        override("DB_PASSWORD", "db.password");
        override("DB_POOL_SIZE", "db.pool.size");
        override("DB_POOL_MINIMUM_IDLE", "db.pool.minimum.idle");
        override("DB_POOL_CONNECTION_TIMEOUT_MS", "db.pool.connection.timeout.ms");
        override("DB_POOL_IDLE_TIMEOUT_MS", "db.pool.idle.timeout.ms");
        override("DB_POOL_MAX_LIFETIME_MS", "db.pool.max.lifetime.ms");
        override("DB_POOL_KEEPALIVE_TIME_MS", "db.pool.keepalive.time.ms");
        override("DB_JDBC_CACHE_PREP_STMTS", "db.jdbc.cache.prep.stmts");
        override("DB_JDBC_PREP_STMT_CACHE_SIZE", "db.jdbc.prep.stmt.cache.size");
        override("DB_JDBC_PREP_STMT_CACHE_SQL_LIMIT", "db.jdbc.prep.stmt.cache.sql.limit");
        override("DB_JDBC_PREPARE_THRESHOLD", "db.jdbc.prepare.threshold");
        override("DB_READ_JDBC_URL", "db.read.jdbc.url");
        override("DB_READ_POOL_SIZE", "db.read.pool.size");
        override("DB_SHARD_JDBC_URL", "db.shard.jdbc.url");
        override("API_REPLICAS", "api.replicas");
        override("POSTGRES_MAX_CONNECTIONS", "postgres.max.connections");
        override("REDIS_URI", "redis.uri");
        override("REDIS_CONNECT_TIMEOUT_MS", "redis.connect.timeout.ms");
        override("REDIS_COMMAND_TIMEOUT_MS", "redis.command.timeout.ms");
        override("NATS_URL", "nats.url");
        override("NATS_JETSTREAM", "nats.jetstream");
        override("NATS_RECONNECT_WAIT_MS", "nats.reconnect.wait.ms");
        override("NATS_MAX_RECONNECTS", "nats.max.reconnects");
        override("NATS_CONNECTION_TIMEOUT_MS", "nats.connection.timeout.ms");
        override("NATS_PING_INTERVAL_MS", "nats.ping.interval.ms");
        override("KEYCLOAK_ISSUER", "keycloak.issuer");
        override("KEYCLOAK_JWKS_URL", "keycloak.jwks.url");
        override("KEYCLOAK_AUDIENCE", "keycloak.audience");
        override("KEYCLOAK_MASTER_USER", "keycloak.master.user");
        override("KEYCLOAK_MASTER_PASSWORD", "keycloak.master.password");
        override("KORUS_DEFAULT_ORG_ID", "korus.default.org.id");
        override("WEB_PUBLIC_BASE_URL", "web.public.base.url");
        override("WEB_CLIENT_VAPID_PUBLIC_KEY", "web.client.vapid.public.key");
        override("MINIO_ENDPOINT", "minio.endpoint");
        override("MINIO_ACCESS_KEY", "minio.access.key");
        override("MINIO_SECRET_KEY", "minio.secret.key");
        override("MINIO_BUCKET", "minio.bucket");
        override("MINIO_CONNECT_TIMEOUT_MS", "minio.connect.timeout.ms");
        override("MINIO_READ_TIMEOUT_MS", "minio.read.timeout.ms");
        override("MINIO_WRITE_TIMEOUT_MS", "minio.write.timeout.ms");
        override("MINIO_HTTP_MAX_IDLE_CONNECTIONS", "minio.http.max.idle.connections");
        override("MINIO_HTTP_KEEP_ALIVE_MINUTES", "minio.http.keep.alive.minutes");
        override("MINIO_HTTP_RETRY_ON_CONNECTION_FAILURE", "minio.http.retry.on.connection.failure");
        override("HOT_RELOAD", "hot.reload");
        override("FILE_PROXY_MODE", "file.proxy.mode");
        override("FILE_PROXY_URL", "file.proxy.url");
        override("FILE_PROXY_AUTH_TOKEN", "file.proxy.auth.token");
        override("FILE_RESIZE_ENABLED", PROP_FILE_RESIZE_ENABLED);
        override("FILE_RESIZE_MAX_WIDTH", "file.resize.max.width");
        override("FILE_RESIZE_MAX_HEIGHT", "file.resize.max.height");
        override("FILE_RESIZE_MAX_SOURCE_PIXELS", "file.resize.max.source.pixels");
        override("RATE_LIMIT_AUTH_ENABLED", "rate.limit.auth.enabled");
        override("RATE_LIMIT_AUTH_FAIL_OPEN", "rate.limit.auth.fail.open");
        override("RATE_LIMIT_LOGIN_PER_MINUTE", "rate.limit.auth.login.per.minute");
        override("RATE_LIMIT_REGISTER_PER_HOUR", "rate.limit.auth.register.per.hour");
        override("MEDIA_MAX_UPLOAD_BYTES", "media.max.upload.bytes");
        override("FILE_DEDUP_ENABLED", "file.dedup.enabled");
        override("FILE_UPLOAD_MAX_CONCURRENT", "file.upload.max.concurrent");
        override("JITSI_MEET_BASE_URL", "jitsi.meet.base.url");
        override("CONFERENCE_ROOM_PREFIX", "conference.room.prefix");
        override("LIVEKIT_URL", "livekit.url");
        override("LIVEKIT_API_KEY", "livekit.api.key");
        override("LIVEKIT_API_SECRET", "livekit.api.secret");
        override("LIVEKIT_INGRESS_URL", "livekit.ingress.url");
        override("LIVEKIT_EGRESS_URL", "livekit.egress.url");
        override("LIVESTREAM_ROOM_PREFIX", "livestream.room.prefix");
        override("LIVESTREAM_MAX_WEBRTC_VIEWERS", "livestream.max.webrtc.viewers");
        override("INTEGRATIONS_BASE_URL", "integrations.base.url");
        override("WEBRTC_STUN_URIS", "webrtc.stun.uris");
        override("EXTERNAL_STACK_MANIFEST_PATH", "external.stack.manifest.path");
        override("SOLR_ZK", "solr.zk.hosts");
        override("SOLR_URL", "solr.http.url");
        override("SOLR_COLLECTION", "solr.collection");
        override("SEARCH_MODE", "search.mode");
        override("FILE_PUBLIC_LINK_DEFAULT_TTL_SECONDS", "file.public.link.default.ttl.seconds");
        override("CORS_ALLOWED_ORIGINS", "cors.allowed.origins");
        override("RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS", "retention.default.hot_body_max_age_days");
        override("RETENTION_DEFAULT_HOT_METADATA_MIN_AGE_DAYS", "retention.default.hot_metadata_min_age_days");
        override("RETENTION_DEFAULT_ARCHIVE_METADATA_ENABLED", "retention.default.archive_metadata_enabled");
        override("RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED", "retention.default.deep_archive_enabled");
        override("RETENTION_DEFAULT_LEGAL_HOLD", "retention.default.legal_hold");
        override("MESSAGE_VISIBILITY_TTL_MAX_SECONDS", "message.visibility.ttl.max.seconds");
        override("MESSAGE_ARCHIVE_TTL_MAX_SECONDS", "message.archive.ttl.max.seconds");
        override("RETENTION_WORKER_ENABLED", "retention.worker.enabled");
        override("RETENTION_SCAN_INTERVAL_SECONDS", "retention.scan.interval.seconds");
        override("EXPORT_DIR", "export.dir");
        override("EXPORT_COMPLETE_SUBSCRIBER_ENABLED", "export.complete.subscriber.enabled");
        override("EXPORT_SUGGESTED_SUBSCRIBER_ENABLED", "export.suggested.subscriber.enabled");
        override("EXPORT_ADMIN_SUGGEST_ENABLED", "export.admin.suggest.enabled");
        override("EXPORT_ADMIN_EXPORT_ENABLED", "export.admin.export.enabled");
        override("EXPORT_AUTO_QUEUE_ON_SUGGESTED", "export.auto.queue.on.suggested.enabled");
        override("EXPORT_AUTO_QUEUE_ACTOR_USER_ID", "export.auto.queue.actor.user.id");
        override("EXPORT_AUTO_QUEUE_COOLDOWN_MINUTES", "export.auto.queue.cooldown.minutes");
        override("HOTPLUG_HEARTBEAT_TTL_MS", PROP_HOTPLUG_HEARTBEAT_TTL_MS);
        override("SERVICE_HEARTBEAT_TTL_MS", PROP_HOTPLUG_HEARTBEAT_TTL_MS);
        override("HOTPLUG_INDEXER_SERVICE_ID", "hotplug.indexer.service.id");
        override("HOTPLUG_INDEXER_PRESENCE_REQUIRED", "hotplug.indexer.presence.required");
        override("FLEET_TARGETS_JSON", "fleet.targets.json");
        override(ENV_FLEET_PROBE_TIMEOUT_MS, "fleet.probe.timeout.ms");
        override("FLEET_AGGREGATOR_NODE", "fleet.aggregator.node");
        override("MLS_STATUS", "mls.status");
        override("MLS_WIRE_ENABLED", "mls.wire.enabled");
        override("MLS_WIRE_SUBSCRIBER_ENABLED", "mls.wire.subscriber.enabled");
        override("OPENMLS_NATIVE", "openmls.native.enabled");
        override("REDIS_READ_CACHE_ENABLED", "redis.read.cache.enabled");
        override("REDIS_READ_CACHE_TTL_CHAT_LIST_SECONDS", "redis.read.cache.ttl.chat_list.seconds");
        override("REDIS_READ_CACHE_TTL_CHAT_UNREAD_SECONDS", "redis.read.cache.ttl.chat_unread.seconds");
        override("REDIS_READ_CACHE_TTL_USER_PROFILE_SECONDS", "redis.read.cache.ttl.user_profile.seconds");
        override("REDIS_READ_CACHE_TTL_USER_PRESENCE_SECONDS", "redis.read.cache.ttl.user_presence.seconds");
        override("READ_CACHE_NATS_INVALIDATE_ENABLED", "read.cache.nats.invalidate.enabled");
        override("API_JDBC_QUERY_TIMEOUT_SECONDS", "api.jdbc.query.timeout.seconds");
        override("MINIO_PRESIGN_TTL_SECONDS", "minio.presign.ttl.seconds");
        override("FILE_PRESIGN_REDIRECT_ENABLED", "file.presign.redirect.enabled");
        override("KORUS_PRODUCT_ADDONS", "korus.product.addons");
        override("KORUS_LAB_ALLOW_DEV_SECRETS", "korus.lab.allow.dev.secrets");
        override("DIRECTORY_SYNC_INTERVAL_MINUTES", "directory.sync.interval.minutes");
        override("SCIM_BEARER_TOKEN", "scim.bearer.token");
        override("AVATARS_ENABLED", "avatars.enabled");
        override("AVATAR_TOKEN_HMAC_SECRET", "avatar.token.hmac.secret");
        override("AVATAR_TOKEN_HMAC_SECRET_PREVIOUS", "avatar.token.hmac.secret.previous");
        override("API_PUBLIC_BASE_URL", "api.public.base.url");
        override("KEYCLOAK_AVATAR_IMPORT_ENABLED", "keycloak.avatar.import.enabled");
        override("KEYCLOAK_AVATAR_IMPORT_MAX_BYTES", "keycloak.avatar.import.max.bytes");
        override("FILE_RESIZE_ENABLED", PROP_FILE_RESIZE_ENABLED);
    }

    private void override(String envKey, String propKey) {
        var envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            props.setProperty(propKey, envVal);
        }
    }

    public int port() {
        return Integer.parseInt(props.getProperty("server.port", "8080"));
    }

    public String version() {
        return props.getProperty("app.version", "0.1.0-SNAPSHOT");
    }

    /**
     * Локаль текстов API ({@link com.avandocmsg.messenger.common.dto.ApiError#message()} и проверки UUID).
     * По умолчанию русская ({@code ru}). Env: {@code APP_LOCALE} — значения вида {@code ru}, {@code en}, {@code en-US}.
     */
    public Locale locale() {
        return localeFromProperty(props.getProperty("app.locale", "ru"));
    }

    /**
     * Разбор значения {@code app.locale} / {@code APP_LOCALE}: пустая строка → {@code ru}; подчёркивания → дефисы для BCP 47.
     */
    static Locale localeFromProperty(String raw) {
        var s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return Locale.forLanguageTag("ru");
        }
        return Locale.forLanguageTag(s.replace('_', '-'));
    }

    public String dbJdbcUrl() {
        return props.getProperty("db.jdbc.url", "jdbc:postgresql://localhost:5432/avandocmsg_hot");
    }

    public String dbUser() {
        return props.getProperty("db.user", DEFAULT_AVANDOCMSG);
    }

    public String dbPassword() {
        return props.getProperty("db.password", DEFAULT_AVANDOCMSG);
    }

    public int dbPoolSize() {
        return Integer.parseInt(props.getProperty("db.pool.size", "10"));
    }

    public int dbPoolMinimumIdle() {
        return Integer.parseInt(props.getProperty("db.pool.minimum.idle", "2"));
    }

    public long dbPoolConnectionTimeoutMs() {
        return Long.parseLong(props.getProperty("db.pool.connection.timeout.ms", "5000"));
    }

    public long dbPoolIdleTimeoutMs() {
        return Long.parseLong(props.getProperty("db.pool.idle.timeout.ms", "300000"));
    }

    public long dbPoolMaxLifetimeMs() {
        return Long.parseLong(props.getProperty("db.pool.max.lifetime.ms", "600000"));
    }

    /** HikariCP keepalive for stale connection detection (0 = disabled). Env: DB_POOL_KEEPALIVE_TIME_MS. */
    public long dbPoolKeepaliveTimeMs() {
        return Long.parseLong(props.getProperty("db.pool.keepalive.time.ms", "120000"));
    }

    public boolean dbJdbcCachePrepStmts() {
        return Boolean.parseBoolean(props.getProperty("db.jdbc.cache.prep.stmts", "true"));
    }

    public int dbJdbcPrepStmtCacheSize() {
        return Integer.parseInt(props.getProperty("db.jdbc.prep.stmt.cache.size", "250"));
    }

    public int dbJdbcPrepStmtCacheSqlLimit() {
        return Integer.parseInt(props.getProperty("db.jdbc.prep.stmt.cache.sql.limit", "2048"));
    }

    /** PostgreSQL JDBC prepareThreshold (spec 025 FR-027). Default 1. Env: DB_JDBC_PREPARE_THRESHOLD. */
    public int dbJdbcPrepareThreshold() {
        return Integer.parseInt(props.getProperty("db.jdbc.prepare.threshold", "1"));
    }

    /** Optional read replica JDBC URL (spec 006 FR-OPT-05). Empty = route reads to primary. */
    public String dbReadJdbcUrl() {
        return props.getProperty("db.read.jdbc.url", "").trim();
    }

    public int dbReadPoolSize() {
        return Integer.parseInt(props.getProperty("db.read.pool.size", "10"));
    }

    /** Optional FR-OPT-09 shard JDBC URL (scaffold). Empty = single primary pool only. */
    public String dbShardJdbcUrl() {
        return props.getProperty("db.shard.jdbc.url", "").trim();
    }

    /** Declared core-api replica count for pool sizing warnings. Env: API_REPLICAS. */
    public int apiReplicas() {
        return Integer.parseInt(props.getProperty("api.replicas", "1"));
    }

    /** postgres max_connections for pool sizing warnings. Env: POSTGRES_MAX_CONNECTIONS. */
    public int postgresMaxConnections() {
        return Integer.parseInt(props.getProperty("postgres.max.connections", "100"));
    }

    public String redisUri() {
        return props.getProperty("redis.uri", "redis://localhost:6379");
    }

    public Duration redisConnectTimeout() {
        return Duration.ofMillis(Long.parseLong(props.getProperty("redis.connect.timeout.ms", "5000")));
    }

    public Duration redisCommandTimeout() {
        return Duration.ofMillis(Long.parseLong(props.getProperty("redis.command.timeout.ms", "3000")));
    }

    /** Env: {@code REDIS_READ_CACHE_ENABLED}. Default true (spec 025 FR-110). */
    public boolean redisReadCacheEnabled() {
        return Boolean.parseBoolean(props.getProperty("redis.read.cache.enabled", "true"));
    }

    /** TTL seconds for {@link com.avandocmsg.messenger.core.port.ReadCacheKind} (env overrides per kind). */
    public int readCacheTtlSeconds(ReadCacheKind kind) {
        var propKey = switch (kind) {
            case CHAT_LIST -> "redis.read.cache.ttl.chat_list.seconds";
            case CHAT_UNREAD -> "redis.read.cache.ttl.chat_unread.seconds";
            case USER_PROFILE -> "redis.read.cache.ttl.user_profile.seconds";
            case USER_PRESENCE -> "redis.read.cache.ttl.user_presence.seconds";
        };
        var raw = props.getProperty(propKey);
        if (raw != null && !raw.isBlank()) {
            return Integer.parseInt(raw.trim());
        }
        return kind.defaultTtlSeconds();
    }

    public String natsUrl() {
        return props.getProperty("nats.url", "nats://localhost:4222");
    }

    public Duration natsReconnectWait() {
        return Duration.ofMillis(Long.parseLong(props.getProperty("nats.reconnect.wait.ms", "2000")));
    }

    public int natsMaxReconnects() {
        return Integer.parseInt(props.getProperty("nats.max.reconnects", "-1"));
    }

    public Duration natsConnectionTimeout() {
        return Duration.ofMillis(Long.parseLong(props.getProperty("nats.connection.timeout.ms", "5000")));
    }

    public Duration natsPingInterval() {
        return Duration.ofMillis(Long.parseLong(props.getProperty("nats.ping.interval.ms", "120000")));
    }

    /**
     * When true, {@code msg.send} uses JetStream publish and requires pipeline with {@code NATS_JETSTREAM=true}.
     * Env: {@code NATS_JETSTREAM}. Lab compose uses false; enable for FR-052 JetStream/DLQ features.
     */
    public boolean natsJetstream() {
        return Boolean.parseBoolean(props.getProperty("nats.jetstream", STRING_FALSE));
    }

    public String keycloakIssuer() {
        return props.getProperty("keycloak.issuer", "http://localhost:8081/realms/avandocmsg");
    }

    public String keycloakJwksUrl() {
        return props.getProperty("keycloak.jwks.url", "http://localhost:8081/realms/avandocmsg/protocol/openid-connect/certs");
    }

    /**
     * Ожидаемое значение claim {@code aud} в access token. Пусто — проверка не выполняется.
     * Env: {@code KEYCLOAK_AUDIENCE}.
     */
    public String keycloakAudience() {
        return props.getProperty("keycloak.audience", "").trim();
    }

    /** Например {@code http://localhost:8081} — без {@code /realms/...}. */
    public String keycloakBaseUrl() {
        var issuer = keycloakIssuer();
        var idx = issuer.indexOf("/realms/");
        return idx > 0 ? issuer.substring(0, idx) : issuer.replaceAll("/realms/.*", "");
    }

    /** Token endpoint realm {@code master} (admin-cli для создания пользователей в realm). */
    public String keycloakMasterTokenEndpoint() {
        return keycloakBaseUrl() + "/realms/master/protocol/openid-connect/token";
    }

    /** Учётная запись в realm {@code master} для password grant клиента {@code admin-cli}. */
    public String keycloakMasterUser() {
        return props.getProperty("keycloak.master.user", DEFAULT_ADMIN);
    }

    public String keycloakMasterPassword() {
        return props.getProperty("keycloak.master.password", DEFAULT_ADMIN);
    }

    /** База Admin REST для текущего realm: {@code .../admin/realms/avandocmsg}. */
    public String keycloakAdminRealmBase() {
        var issuer = keycloakIssuer();
        var marker = "/realms/";
        var idx = issuer.indexOf(marker);
        if (idx < 0) {
            throw new IllegalStateException("keycloak.issuer must contain " + marker + ", got: " + issuer);
        }
        var realm = issuer.substring(idx + marker.length());
        var base = issuer.substring(0, idx);
        return base + "/admin/realms/" + realm;
    }

    /** Optional default org when host/subdomain cannot be resolved (single-tenant dev). */
    public Optional<UUID> defaultOrgId() {
        var raw = props.getProperty("korus.default.org.id");
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid korus.default.org.id: {}", raw);
            return Optional.empty();
        }
    }

    /** Public web origin for OIDC redirect_uri (e.g. http://127.0.0.1:19088/). */
    public String webPublicBaseUrl() {
        var v = props.getProperty("web.public.base.url", "http://127.0.0.1:19088/");
        return v.endsWith("/") ? v : v + "/";
    }

    public String minioEndpoint() {
        return props.getProperty("minio.endpoint", "http://localhost:9000");
    }

    public String minioAccessKey() {
        return props.getProperty("minio.access.key", DEFAULT_AVANDOCMSG);
    }

    public String minioSecretKey() {
        return props.getProperty("minio.secret.key", "avandocmsg123");
    }

    public String minioBucket() {
        return props.getProperty("minio.bucket", DEFAULT_AVANDOCMSG);
    }

    public int minioConnectTimeoutMs() {
        return Integer.parseInt(props.getProperty("minio.connect.timeout.ms", "10000"));
    }

    public int minioReadTimeoutMs() {
        return Integer.parseInt(props.getProperty("minio.read.timeout.ms", "60000"));
    }

    public int minioWriteTimeoutMs() {
        return Integer.parseInt(props.getProperty("minio.write.timeout.ms", "60000"));
    }

    public int minioHttpMaxIdleConnections() {
        return Integer.parseInt(props.getProperty("minio.http.max.idle.connections", "10"));
    }

    public int minioHttpKeepAliveMinutes() {
        return Integer.parseInt(props.getProperty("minio.http.keep.alive.minutes", "5"));
    }

    public int minioPresignTtlSeconds() {
        return Integer.parseInt(props.getProperty("minio.presign.ttl.seconds", "300"));
    }

    public boolean filePresignRedirectEnabled() {
        return Boolean.parseBoolean(props.getProperty("file.presign.redirect.enabled", "true"));
    }

    public String filePresignRedirectCacheControl() {
        return props.getProperty("file.presign.redirect.cache-control", "private, max-age=0, no-store");
    }

    public boolean minioHttpRetryOnConnectionFailure() {
        return Boolean.parseBoolean(props.getProperty("minio.http.retry.on.connection.failure", "true"));
    }

    public boolean hotReloadEnabled() {
        return Boolean.parseBoolean(props.getProperty("hot.reload", "true"));
    }

    public String fileProxyMode() {
        return props.getProperty("file.proxy.mode", "minio");
    }

    public String fileProxyUrl() {
        return props.getProperty("file.proxy.url", "http://file-proxy:8090");
    }

    public String fileProxyAuthToken() {
        return props.getProperty("file.proxy.auth.token", "");
    }

    /** Embedded on-the-fly image resize in core-api (remote file-proxy sidecar — future). */
    public boolean fileResizeEnabled() {
        return Boolean.parseBoolean(props.getProperty(PROP_FILE_RESIZE_ENABLED, "true"));
    }

    /** Upper bound for resize query param {@code w}. */
    public int fileResizeMaxWidth() {
        return Integer.parseInt(props.getProperty("file.resize.max.width", "2048"));
    }

    /** Upper bound for resize query param {@code h}. */
    public int fileResizeMaxHeight() {
        return Integer.parseInt(props.getProperty("file.resize.max.height", "2048"));
    }

    /** Reject resize when source width×height exceeds this (DoS guard). */
    public long fileResizeMaxSourcePixels() {
        return Long.parseLong(props.getProperty("file.resize.max.source.pixels", "25000000"));
    }

    /** When true, Redis must be reachable (see {@link com.avandocmsg.messenger.api.config.RedisConfig}). */
    public boolean rateLimitAuthEnabled() {
        return Boolean.parseBoolean(props.getProperty("rate.limit.auth.enabled", STRING_FALSE));
    }

    /** When false, Redis errors deny auth (fail-closed). Env: RATE_LIMIT_AUTH_FAIL_OPEN. */
    public boolean rateLimitAuthFailOpen() {
        return Boolean.parseBoolean(props.getProperty("rate.limit.auth.fail.open", STRING_FALSE));
    }

    public int rateLimitLoginMaxPerMinute() {
        return Integer.parseInt(props.getProperty("rate.limit.auth.login.per.minute", "60"));
    }

    public int rateLimitRegisterMaxPerHour() {
        return Integer.parseInt(props.getProperty("rate.limit.auth.register.per.hour", "5"));
    }

    /** Максимальный размер загрузки файла (байты), синхронно с лимитом {@link com.avandocmsg.messenger.api.files.FileService}. */
    public long mediaMaxUploadBytes() {
        return Long.parseLong(props.getProperty("media.max.upload.bytes", "52428800"));
    }

    /** FR-OPT-08: content-hash deduplication on upload (default on). */
    public boolean fileDedupEnabled() {
        return Boolean.parseBoolean(props.getProperty("file.dedup.enabled", "true"));
    }

    /** Max concurrent uploads spooling to disk (PS-1.2). */
    public int fileUploadMaxConcurrent() {
        return Integer.parseInt(props.getProperty("file.upload.max.concurrent", "20"));
    }

    /** JDBC statement timeout for hot read paths (PS-0.2). {@code 0} = disabled. */
    public int apiJdbcQueryTimeoutSeconds() {
        var raw = props.getProperty("api.jdbc.query.timeout.seconds", "30").trim();
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /** Comma-separated add-on ids; empty = Base-only. Env: {@code KORUS_PRODUCT_ADDONS}. */
    public String korusProductAddons() {
        return props.getProperty("korus.product.addons", "").trim();
    }

    /** Selected add-ons whose deploy bundle is installed; empty means selected add-ons are installed. */
    public String korusProductInstalledAddons() {
        return props.getProperty("korus.product.installed.addons", "").trim();
    }

    /** Selected add-ons whose optional schema bundle is installed; empty means installed add-ons are schema-ready. */
    public String korusProductSchemaInstalledAddons() {
        return props.getProperty("korus.product.schema.installed.addons", "").trim();
    }

    /** Selected add-ons whose runtime services/workers are ready; empty means schema-ready add-ons are runtime-ready. */
    public String korusProductRuntimeReadyAddons() {
        return props.getProperty("korus.product.runtime.ready.addons", "").trim();
    }

    /** Selected add-ons currently in deploy/pre-migration hot install lifecycle. */
    public String korusProductInstallingAddons() {
        return props.getProperty("korus.product.installing.addons", "").trim();
    }

    /** Platform core availability for add-on reason {@code core_unavailable}. */
    public boolean coreAvailable() {
        return true;
    }

    /**
     * Fail-fast when dev-default secrets are used with non-empty product addons (PS-0.6).
     */
    public void validateProductionSecrets() {
        var addons = korusProductAddons();
        if (addons == null || addons.isBlank()) {
            return;
        }
        if (korusLabAllowDevSecrets()) {
            log.warn("KORUS_LAB_ALLOW_DEV_SECRETS=true: skipping production secret check (QEMU/lab only)");
            return;
        }
        var issues = new ArrayList<String>();
        if (isDevDefaultSecret(dbPassword(), DEFAULT_AVANDOCMSG)) {
            issues.add("DB_PASSWORD");
        }
        if (isDevDefaultSecret(keycloakMasterPassword(), DEFAULT_ADMIN)) {
            issues.add("KEYCLOAK_MASTER_PASSWORD");
        }
        if (isDevDefaultSecret(minioSecretKey(), "avandocmsg123")) {
            issues.add("MINIO_SECRET_KEY");
        }
        if (issues.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
            "Insecure default secrets with product addons enabled: " + String.join(", ", issues));
    }

    /** Startup validation for operability gates (spec 025 FR-100). */
    public void validateStartup() {
        validateProductionSecrets();
        var presignTtl = minioPresignTtlSeconds();
        if (presignTtl < 60 || presignTtl > 3600) {
            throw new IllegalStateException(
                "minio.presign.ttl.seconds must be between 60 and 3600, got " + presignTtl);
        }
        if (dbPoolSize() < 1) {
            throw new IllegalStateException("db.pool.size must be >= 1, got " + dbPoolSize());
        }
        if (dbPoolMinimumIdle() < 0 || dbPoolMinimumIdle() > dbPoolSize()) {
            throw new IllegalStateException(
                "db.pool.minimum.idle must be 0.." + dbPoolSize() + ", got " + dbPoolMinimumIdle());
        }
        var nats = natsUrl();
        if (nats == null || nats.isBlank()) {
            throw new IllegalStateException("nats.url must not be blank");
        }
    }

    /** QEMU/lab only: allow dev-default secrets when full addon matrix is enabled. */
    public boolean korusLabAllowDevSecrets() {
        return Boolean.parseBoolean(props.getProperty("korus.lab.allow.dev.secrets", STRING_FALSE));
    }

    private boolean isDevDefaultSecret(String actual, String devDefault) {
        return actual != null && devDefault.equals(actual.trim());
    }

    /** Базовый URL Jitsi Meet (без завершающего слэша); комната = {@code base + "/" + room_slug}. */
    public String jitsiMeetBaseUrl() {
        return props.getProperty("jitsi.meet.base.url", "https://meet.jit.si");
    }

    /** Префикс имени комнаты до уникального суффикса. */
    public String conferenceRoomPrefix() {
        return props.getProperty("conference.room.prefix", "avandocmsg-");
    }

    /** WebSocket URL LiveKit (wss://…); empty = live-streaming API disabled for token issue. */
    public String livekitUrl() {
        return props.getProperty("livekit.url", "").trim();
    }

    public String livekitApiKey() {
        return props.getProperty("livekit.api.key", "").trim();
    }

    public String livekitApiSecret() {
        return props.getProperty("livekit.api.secret", "").trim();
    }

    public boolean liveStreamingEnabled() {
        return !livekitUrl().isEmpty() && !livekitApiKey().isEmpty() && !livekitApiSecret().isEmpty();
    }

    /** RTMP ingress base URL (e.g. rtmp://host:1935/live); derived from LiveKit URL when unset. */
    public String livekitIngressUrl() {
        var explicit = props.getProperty("livekit.ingress.url", "").trim();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        var lk = livekitUrl();
        if (lk.isEmpty()) {
            return "";
        }
        try {
            var normalized = lk.replace("wss://", "https://").replace("ws://", "http://");
            var uri = java.net.URI.create(normalized);
            var host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            return "rtmp://" + host + ":1935/live";
        } catch (Exception e) {
            return "";
        }
    }

    /** LiveKit egress HTTP base (e.g. http://livekit-egress:8080). Empty = client-side mesh recording only. */
    public String livekitEgressUrl() {
        return props.getProperty("livekit.egress.url", "").trim();
    }

    public boolean compositeCallRecordingEnabled() {
        return liveStreamingEnabled() && !livekitEgressUrl().isBlank();
    }

    public String livestreamRoomPrefix() {
        return props.getProperty("livestream.room.prefix", "korus-live-");
    }

    public int livestreamMaxWebrtcViewers() {
        return Integer.parseInt(props.getProperty("livestream.max.webrtc.viewers", "200"));
    }

    /** Список STUN через запятую (ICE для WebRTC у клиента). */
    public String webrtcStunUris() {
        return props.getProperty("webrtc.stun.uris", "stun:stun.l.google.com:19302");
    }

    /** Comma-separated ZooKeeper hosts for SolrCloud; empty = do not use Solr from core-api. */
    public String solrZkHosts() {
        return props.getProperty("solr.zk.hosts", "").trim();
    }

    /** Solr HTTP base (single-node); empty if using {@link #solrZkHosts()} instead. */
    public String solrHttpUrl() {
        return props.getProperty("solr.http.url", "").trim();
    }

    public String solrCollection() {
        return props.getProperty("solr.collection", "messages_meta");
    }

    /** Explicit search backend: {@code sql}, {@code solr}, or empty (auto from Solr env). */
    public String searchMode() {
        return props.getProperty("search.mode", "").trim();
    }

    /** Default TTL for created public file links (ТЗ п. 15), seconds. */
    public long filePublicLinkDefaultTtlSeconds() {
        return Long.parseLong(props.getProperty("file.public.link.default.ttl.seconds", "604800"));
    }

    /**
     * Дефолт платформы: макс. возраст тела сообщения в Hot DB (дни) до выноса по политике; пусто = без лимита ({@code null}).
     * Env: {@code RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS}.
     */
    public Integer retentionDefaultHotBodyMaxAgeDays() {
        return intPropOrNull("retention.default.hot_body_max_age_days");
    }

    /**
     * Мин. возраст метаданных в Hot (дни); пусто = {@code null} (не задано).
     * Env: {@code RETENTION_DEFAULT_HOT_METADATA_MIN_AGE_DAYS}.
     */
    public Integer retentionDefaultHotMetadataMinAgeDays() {
        return intPropOrNull("retention.default.hot_metadata_min_age_days");
    }

    /** Env: {@code RETENTION_DEFAULT_ARCHIVE_METADATA_ENABLED}. */
    public boolean retentionDefaultArchiveMetadataEnabled() {
        return Boolean.parseBoolean(props.getProperty("retention.default.archive_metadata_enabled", "true"));
    }

    /** Env: {@code RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED}. */
    public boolean retentionDefaultDeepArchiveEnabled() {
        return Boolean.parseBoolean(props.getProperty("retention.default.deep_archive_enabled", "true"));
    }

    /** Env: {@code RETENTION_DEFAULT_LEGAL_HOLD}. */
    public boolean retentionDefaultLegalHold() {
        return Boolean.parseBoolean(props.getProperty("retention.default.legal_hold", STRING_FALSE));
    }

    /**
     * Максимум для {@code visibility_ttl_seconds} при отправке сообщения (включительно). Env: {@code MESSAGE_VISIBILITY_TTL_MAX_SECONDS}.
     */
    public int visibilityTtlMaxSeconds() {
        return Integer.parseInt(props.getProperty("message.visibility.ttl.max.seconds", "31536000"));
    }

    /**
     * Максимум для {@code archive_ttl_seconds} при отправке сообщения (включительно). Env: {@code MESSAGE_ARCHIVE_TTL_MAX_SECONDS}.
     */
    public int archiveTtlMaxSeconds() {
        return Integer.parseInt(props.getProperty("message.archive.ttl.max.seconds", "31536000"));
    }

    /**
     * Включить отдельный процесс {@code RetentionWorker} (модуль {@code workers:retention}).
     * По умолчанию {@code false} — безопасный деплой без фоновой очистки. Env: {@code RETENTION_WORKER_ENABLED}.
     */
    public boolean retentionWorkerEnabled() {
        return Boolean.parseBoolean(props.getProperty("retention.worker.enabled", STRING_FALSE));
    }

    /**
     * Интервал тика цикла сканирования (секунды) для {@code RetentionWorker}. Env: {@code RETENTION_SCAN_INTERVAL_SECONDS}.
     */
    public int retentionScanIntervalSeconds() {
        return Integer.parseInt(props.getProperty("retention.scan.interval.seconds", "3600"));
    }

    private Integer intPropOrNull(String key) {
        var s = props.getProperty(key, "").trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for {}: {}", key, s);
            return null;
        }
    }

    /**
     * Allowed browser origins for CORS (comma-separated). Use {@code *} for development.
     * Env: {@code CORS_ALLOWED_ORIGINS}.
     */
    public List<String> corsAllowedOrigins() {
        return CorsOriginPolicy.parseOriginsList(props.getProperty("cors.allowed.origins", "*"));
    }

    /**
     * Каталог JSON-экспортов (тот же том, что у export-replay worker). Пусто — скачивание через API недоступно.
     * Env: {@code EXPORT_DIR}.
     */
    public Optional<Path> exportDir() {
        var raw = props.getProperty("export.dir", "").trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(raw));
    }

    /**
     * Subscribe to {@code msg.export.replay.complete} and sync {@code export_jobs}. Env: {@code EXPORT_COMPLETE_SUBSCRIBER_ENABLED}.
     */
    public boolean exportCompleteSubscriberEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.complete.subscriber.enabled", "true"));
    }

    /**
     * Subscribe to {@code msg.export.suggested} and write {@code export.suggested} audit rows.
     * Env: {@code EXPORT_SUGGESTED_SUBSCRIBER_ENABLED}.
     */
    public boolean exportSuggestedSubscriberEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.suggested.subscriber.enabled", "true"));
    }

    /**
     * Subscribe to deprecated {@code msg.cache.invalidate} for Redis read-cache rollback (FR-009).
     * Default {@code false} — pipeline invalidates Redis directly. Env: {@code READ_CACHE_NATS_INVALIDATE_ENABLED}.
     */
    public boolean readCacheNatsInvalidateEnabled() {
        return Boolean.parseBoolean(props.getProperty("read.cache.nats.invalidate.enabled", STRING_FALSE));
    }

    /**
     * Allow {@code POST /admin/chats/{chatId}/export-suggest} (local / NATS dispatch). Env:
     * {@code EXPORT_ADMIN_SUGGEST_ENABLED}; default {@code false}.
     */
    public boolean exportAdminSuggestEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.admin.suggest.enabled", STRING_FALSE));
    }

    /**
     * Allow {@code POST /admin/chats/{chatId}/export} (queue export job). Env:
     * {@code EXPORT_ADMIN_EXPORT_ENABLED}; default {@code false}.
     */
    public boolean exportAdminExportEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.admin.export.enabled", STRING_FALSE));
    }

    /**
     * On {@code msg.export.suggested}, enqueue export (deduped). Env: {@code EXPORT_AUTO_QUEUE_ON_SUGGESTED};
     * default {@code false}.
     */
    public boolean exportAutoQueueOnSuggestedEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.auto.queue.on.suggested.enabled", STRING_FALSE));
    }

    /**
     * {@code requested_by} for auto-queued jobs; if unset — {@code chats.owner_id}. Env:
     * {@code EXPORT_AUTO_QUEUE_ACTOR_USER_ID}.
     */
    public java.util.Optional<java.util.UUID> exportAutoQueueActorUserId() {
        var raw = props.getProperty("export.auto.queue.actor.user.id", "").trim();
        if (raw.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(java.util.UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    /** Min minutes between auto-queues per chat (pending jobs always block). Env: {@code EXPORT_AUTO_QUEUE_COOLDOWN_MINUTES}. */
    public int exportAutoQueueCooldownMinutes() {
        var raw = props.getProperty("export.auto.queue.cooldown.minutes", "1440").trim();
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1440;
        }
    }

    /** Heartbeat TTL for hot-plug service presence detection (milliseconds). */
    public long hotplugHeartbeatTtlMs() {
        var raw = props.getProperty(PROP_HOTPLUG_HEARTBEAT_TTL_MS, "30000").trim();
        try {
            return Math.max(1000L, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 30000L;
        }
    }

    /** Service id expected for indexer hot-plug presence checks. */
    public String hotplugIndexerServiceId() {
        return props.getProperty("hotplug.indexer.service.id", "indexer-service").trim();
    }

    /** When true, core-api skips index events if indexer heartbeat is missing. */
    public boolean hotplugIndexerPresenceRequired() {
        return Boolean.parseBoolean(props.getProperty("hotplug.indexer.presence.required", STRING_FALSE));
    }

    /** JSON array of {@link com.avandocmsg.messenger.api.admin.fleet.FleetTarget} for admin fleet snapshot. */
    public String fleetTargetsJson() {
        var env = System.getenv("FLEET_TARGETS_JSON");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return props.getProperty("fleet.targets.json", "").trim();
    }

    /** HTTP probe timeout for fleet snapshot (ms). Env: {@code FLEET_PROBE_TIMEOUT_MS}; default {@code 2000}. */
    public int fleetProbeTimeoutMs() {
        var raw = props.getProperty("fleet.probe.timeout.ms", "2000").trim();
        if (System.getenv(ENV_FLEET_PROBE_TIMEOUT_MS) != null) {
            raw = System.getenv(ENV_FLEET_PROBE_TIMEOUT_MS).trim();
        }
        try {
            return Math.min(10000, Math.max(500, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return 2000;
        }
    }

    /** Label of this core-api node in fleet snapshot. Env: {@code FLEET_AGGREGATOR_NODE} or hostname. */
    public String fleetAggregatorNode() {
        var env = System.getenv("FLEET_AGGREGATOR_NODE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        var prop = props.getProperty("fleet.aggregator.node", "").trim();
        if (!prop.isBlank()) {
            return prop;
        }
        var host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host.trim();
        }
        return "core-api-local";
    }

    /**
     * Jobs in {@code processing} with {@code updated_at} older than this are "stale" (metrics + admin stats).
     * Env: {@code EXPORT_PROCESSING_STALE_MINUTES}; default {@code 30}.
     */
    public int exportProcessingStaleMinutes() {
        var raw = props.getProperty("export.processing.stale.minutes", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("EXPORT_PROCESSING_STALE_MINUTES", "30");
        }
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /** Env: {@code SECURITY_HEADERS_ENABLED}; default {@code true}. */
    public boolean securityHeadersEnabled() {
        return Boolean.parseBoolean(props.getProperty("security.headers.enabled", "true"));
    }

    /** Optional CSP header value. Env: {@code CSP_POLICY}. */
    public String cspPolicy() {
        return props.getProperty("csp.policy", "").trim();
    }

    /** CSV mandatory export completeness fields. Env: {@code EXPORT_REQUIRED_FIELDS}. */
    public java.util.Set<String> exportRequiredFields() {
        return com.avandocmsg.messenger.common.export.ExportCompletenessConfig.requiredFieldsFromEnv(
            props.getProperty("export.required.fields", System.getenv("EXPORT_REQUIRED_FIELDS")));
    }

    /** Env: {@code EXPORT_COMPLETENESS_STRICT}; default {@code false}. */
    public boolean exportCompletenessStrict() {
        return com.avandocmsg.messenger.common.export.ExportCompletenessConfig.strictFromEnv(
            props.getProperty("export.completeness.strict", System.getenv("EXPORT_COMPLETENESS_STRICT")));
    }

    /** General endpoint rate limit capacity. Env: {@code RATE_LIMITER_DEFAULT_CAPACITY}; default {@code 100}. */
    public int rateLimiterDefaultCapacity() {
        var raw = props.getProperty("rate.limiter.default.capacity", "100").trim();
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    /** Env: {@code RATE_LIMITER_ENABLED}; default follows auth rate limit flag. */
    public boolean rateLimiterEnabled() {
        var raw = props.getProperty("rate.limiter.enabled", "").trim();
        if (!raw.isEmpty()) {
            return Boolean.parseBoolean(raw);
        }
        return rateLimitAuthEnabled();
    }

    /** Max message IDs per read-batch call. Env: {@code READ_RECEIPT_BATCH_MAX}; default {@code 100}. */
    public int readReceiptBatchMax() {
        var raw = props.getProperty("read.receipt.batch.max", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("READ_RECEIPT_BATCH_MAX", "100");
        }
        try {
            return Math.max(1, Math.min(500, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    /** Retention for {@code message_read_receipts}; {@code 0} disables purge. Env: {@code READ_RECEIPT_RETENTION_DAYS}. */
    public int readReceiptRetentionDays() {
        var raw = props.getProperty("read.receipt.retention.days", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("READ_RECEIPT_RETENTION_DAYS", "365");
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 365;
        }
    }

    /** RFC 9420 phase-1 MLS wire codec enabled. Env: {@code MLS_WIRE_ENABLED}; default {@code true}. */
    public boolean mlsWireEnabled() {
        return Boolean.parseBoolean(props.getProperty("mls.wire.enabled", "true"));
    }

    /** Subscribe to {@code mls.*} NATS subjects. Env: {@code MLS_WIRE_SUBSCRIBER_ENABLED}; default {@code true}. */
    public boolean mlsWireSubscriberEnabled() {
        return Boolean.parseBoolean(props.getProperty("mls.wire.subscriber.enabled", "true"));
    }

    /**
     * MLS rollout status for capabilities/admin. Env: {@code MLS_STATUS};
     * default {@code active} when wire enabled, else {@code stub}.
     */
    public String mlsStatus() {
        var configured = props.getProperty("mls.status", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        return mlsWireEnabled() ? "active" : "stub";
    }

    public List<String> e2eeSchemes() {
        return mlsWireEnabled()
            ? List.of("legacy", "mls")
            : List.of("legacy", "mls-stub");
    }

    /**
     * Attempt OpenMLS JNI/FFI load at startup. Env: {@code OPENMLS_NATIVE}; property {@code openmls.native.enabled};
     * default {@code false}.
     */
    public boolean openmlsNativeEnabled() {
        return Boolean.parseBoolean(props.getProperty("openmls.native.enabled", STRING_FALSE));
    }

    /** Spec 014: connector-runtime base URL. Env: INTEGRATIONS_BASE_URL. */
    public String integrationsBaseUrl() {
        return props.getProperty("integrations.base.url", "http://localhost:8091").trim();
    }

    /** Optional desired external-stack manifest rendered by deploy tooling (spec 023). */
    public String externalStackManifestPath() {
        return props.getProperty("external.stack.manifest.path", "").trim();
    }

    /** Web Push VAPID public key for browser subscription (optional until ops configures). */
    public Optional<String> webClientVapidPublicKey() {
        var raw = props.getProperty("web.client.vapid.public.key", "").trim();
        return raw.isBlank() ? Optional.empty() : Optional.of(raw);
    }

    /** Minimum handler duration for timing normalization (GET chat etc.). 0 = disabled. Env: SECURITY_TIMING_NORMALIZATION_MIN_MS. */
    public long timingNormalizationMinNanos() {
        var raw = props.getProperty("security.timing.normalization.min.ms", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("SECURITY_TIMING_NORMALIZATION_MIN_MS", "0");
        }
        try {
            var ms = Math.max(0, Integer.parseInt(raw));
            return ms * 1_000_000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Extra delay on 404 when normalization enabled (closes response-size / serialization gap). Env: SECURITY_TIMING_NOT_FOUND_EXTRA_MS. */
    public long timingNotFoundExtraNanos() {
        if (timingNormalizationMinNanos() <= 0) {
            return 0L;
        }
        var raw = props.getProperty("security.timing.not_found.extra.ms", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("SECURITY_TIMING_NOT_FOUND_EXTRA_MS", "35");
        }
        try {
            var ms = Math.max(0, Integer.parseInt(raw));
            return ms * 1_000_000L;
        } catch (NumberFormatException e) {
            return 35L * 1_000_000L;
        }
    }

    /** Extra delay on failed login when normalization enabled (Keycloak exist vs miss). Env: SECURITY_TIMING_AUTH_FAILURE_EXTRA_MS. */
    public long timingAuthFailureExtraNanos() {
        if (timingNormalizationMinNanos() <= 0) {
            return 0L;
        }
        var raw = props.getProperty("security.timing.auth_failure.extra.ms", "").trim();
        if (raw.isEmpty()) {
            raw = System.getenv().getOrDefault("SECURITY_TIMING_AUTH_FAILURE_EXTRA_MS", "80");
        }
        try {
            var ms = Math.max(0, Integer.parseInt(raw));
            return ms * 1_000_000L;
        } catch (NumberFormatException e) {
            return 80L * 1_000_000L;
        }
    }

    /** LDAP directory sync interval in minutes; 0 disables scheduler. Default 60. */
    public long directorySyncIntervalMinutes() {
        var raw = props.getProperty("directory.sync.interval.minutes", "60").trim();
        try {
            return Math.max(0, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 60L;
        }
    }

    /** Migration import poll interval in seconds; 0 disables scheduler. Default 60. */
    public long migrationImportPollSeconds() {
        var raw = props.getProperty("migration.import.poll.seconds", "60").trim();
        try {
            return Math.max(0, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 60L;
        }
    }

    /** Max pending jobs processed per scheduler tick. Default 5. */
    public int migrationImportBatchSize() {
        var raw = props.getProperty("migration.import.batch.size", "5").trim();
        try {
            return Math.max(1, Math.min(Integer.parseInt(raw), 50));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    /** Scheduled message poll interval in seconds; 0 disables scheduler. Default 30. */
    public long scheduledMessagePollSeconds() {
        var raw = props.getProperty("scheduled.message.poll.seconds", "30").trim();
        try {
            return Math.max(0, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 30L;
        }
    }

    /** Max due scheduled messages processed per tick. Default 20. */
    public int scheduledMessageBatchSize() {
        var raw = props.getProperty("scheduled.message.batch.size", "20").trim();
        try {
            return Math.max(1, Math.min(Integer.parseInt(raw), 100));
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    /** Message reminder poll interval in seconds; 0 disables scheduler. Default 60. */
    public long messageReminderPollSeconds() {
        var raw = props.getProperty("message.reminder.poll.seconds", "60").trim();
        try {
            return Math.max(0, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 60L;
        }
    }

    /** Max due reminders processed per tick. Default 50. */
    public int messageReminderBatchSize() {
        var raw = props.getProperty("message.reminder.batch.size", "50").trim();
        try {
            return Math.max(1, Math.min(Integer.parseInt(raw), 200));
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /** Optional bearer token for SCIM provisioning without admin JWT. */
    public Optional<String> scimBearerToken() {
        var raw = props.getProperty("scim.bearer.token", "").trim();
        return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
    }

    /** When true, enforce {@code org_ip_allowlist} in Jersey filter (lab). Env: ORG_IP_ALLOWLIST_ENFORCE. */
    public boolean orgIpAllowlistEnforce() {
        var env = System.getenv("ORG_IP_ALLOWLIST_ENFORCE");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        return Boolean.parseBoolean(props.getProperty("org.ip.allowlist.enforce", STRING_FALSE));
    }

    /** Entity avatars feature flag (spec 068). Env: {@code AVATARS_ENABLED}. */
    public boolean avatarsEnabled() {
        return Boolean.parseBoolean(props.getProperty("avatars.enabled", "true"));
    }

    /** HMAC secret for signed avatar resize tokens. Env: {@code AVATAR_TOKEN_HMAC_SECRET}. */
    public String avatarTokenHmacSecret() {
        return props.getProperty("avatar.token.hmac.secret", "").trim();
    }

    /** Previous HMAC secret for 24h rotation window. Env: {@code AVATAR_TOKEN_HMAC_SECRET_PREVIOUS}. */
    public String avatarTokenHmacSecretPrevious() {
        return props.getProperty("avatar.token.hmac.secret.previous", "").trim();
    }

    /** Public API base URL for absolute avatar/push links. Env: {@code API_PUBLIC_BASE_URL}. */
    public String apiPublicBaseUrl() {
        return props.getProperty("api.public.base.url", "").trim();
    }

    /** Import Keycloak {@code picture} claim on login when user has no avatar. Env: {@code KEYCLOAK_AVATAR_IMPORT_ENABLED}. */
    public boolean keycloakAvatarImportEnabled() {
        return Boolean.parseBoolean(props.getProperty("keycloak.avatar.import.enabled", "true"));
    }

    /** Max bytes when downloading Keycloak picture URL. Env: {@code KEYCLOAK_AVATAR_IMPORT_MAX_BYTES}. */
    public int keycloakAvatarImportMaxBytes() {
        return Integer.parseInt(props.getProperty("keycloak.avatar.import.max.bytes", "524288"));
    }
}
