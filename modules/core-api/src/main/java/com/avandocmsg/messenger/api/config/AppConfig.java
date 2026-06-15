package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.core.port.ReadCacheKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
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
        override("APP_HOME", "app.home");
        override("DB_JDBC_URL", "db.jdbc.url");
        override("DB_USER", "db.user");
        override("DB_PASSWORD", "db.password");
        override("DB_POOL_SIZE", "db.pool.size");
        override("DB_READ_JDBC_URL", "db.read.jdbc.url");
        override("DB_READ_POOL_SIZE", "db.read.pool.size");
        override("DB_SHARD_JDBC_URL", "db.shard.jdbc.url");
        override("API_REPLICAS", "api.replicas");
        override("POSTGRES_MAX_CONNECTIONS", "postgres.max.connections");
        override("REDIS_URI", "redis.uri");
        override("NATS_URL", "nats.url");
        override("NATS_JETSTREAM", "nats.jetstream");
        override("KEYCLOAK_ISSUER", "keycloak.issuer");
        override("KEYCLOAK_JWKS_URL", "keycloak.jwks.url");
        override("KEYCLOAK_AUDIENCE", "keycloak.audience");
        override("KEYCLOAK_MASTER_USER", "keycloak.master.user");
        override("KEYCLOAK_MASTER_PASSWORD", "keycloak.master.password");
        override("MINIO_ENDPOINT", "minio.endpoint");
        override("MINIO_ACCESS_KEY", "minio.access.key");
        override("MINIO_SECRET_KEY", "minio.secret.key");
        override("MINIO_BUCKET", "minio.bucket");
        override("HOT_RELOAD", "hot.reload");
        override("FILE_PROXY_MODE", "file.proxy.mode");
        override("FILE_PROXY_URL", "file.proxy.url");
        override("FILE_PROXY_AUTH_TOKEN", "file.proxy.auth.token");
        override("RATE_LIMIT_AUTH_ENABLED", "rate.limit.auth.enabled");
        override("RATE_LIMIT_LOGIN_PER_MINUTE", "rate.limit.auth.login.per.minute");
        override("RATE_LIMIT_REGISTER_PER_HOUR", "rate.limit.auth.register.per.hour");
        override("MEDIA_MAX_UPLOAD_BYTES", "media.max.upload.bytes");
        override("FILE_DEDUP_ENABLED", "file.dedup.enabled");
        override("JITSI_MEET_BASE_URL", "jitsi.meet.base.url");
        override("CONFERENCE_ROOM_PREFIX", "conference.room.prefix");
        override("WEBRTC_STUN_URIS", "webrtc.stun.uris");
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
        override("HOTPLUG_HEARTBEAT_TTL_MS", "hotplug.heartbeat.ttl.ms");
        override("SERVICE_HEARTBEAT_TTL_MS", "hotplug.heartbeat.ttl.ms");
        override("HOTPLUG_INDEXER_SERVICE_ID", "hotplug.indexer.service.id");
        override("HOTPLUG_INDEXER_PRESENCE_REQUIRED", "hotplug.indexer.presence.required");
        override("MLS_STATUS", "mls.status");
        override("MLS_WIRE_ENABLED", "mls.wire.enabled");
        override("MLS_WIRE_SUBSCRIBER_ENABLED", "mls.wire.subscriber.enabled");
        override("REDIS_READ_CACHE_ENABLED", "redis.read.cache.enabled");
        override("REDIS_READ_CACHE_TTL_CHAT_LIST_SECONDS", "redis.read.cache.ttl.chat_list.seconds");
        override("REDIS_READ_CACHE_TTL_CHAT_UNREAD_SECONDS", "redis.read.cache.ttl.chat_unread.seconds");
        override("REDIS_READ_CACHE_TTL_USER_PROFILE_SECONDS", "redis.read.cache.ttl.user_profile.seconds");
        override("REDIS_READ_CACHE_TTL_USER_PRESENCE_SECONDS", "redis.read.cache.ttl.user_presence.seconds");
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
        return props.getProperty("db.user", "avandocmsg");
    }

    public String dbPassword() {
        return props.getProperty("db.password", "avandocmsg");
    }

    public int dbPoolSize() {
        return Integer.parseInt(props.getProperty("db.pool.size", "10"));
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

    /** Env: {@code REDIS_READ_CACHE_ENABLED}. Default false — opt-in for Standard tier. */
    public boolean redisReadCacheEnabled() {
        return Boolean.parseBoolean(props.getProperty("redis.read.cache.enabled", "false"));
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

    /** When true, {@code msg.send} uses JetStream publish and requires pipeline with {@code NATS_JETSTREAM=true}. */
    public boolean natsJetstream() {
        return Boolean.parseBoolean(props.getProperty("nats.jetstream", "false"));
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
        return props.getProperty("keycloak.master.user", "admin");
    }

    public String keycloakMasterPassword() {
        return props.getProperty("keycloak.master.password", "admin");
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

    public String minioEndpoint() {
        return props.getProperty("minio.endpoint", "http://localhost:9000");
    }

    public String minioAccessKey() {
        return props.getProperty("minio.access.key", "avandocmsg");
    }

    public String minioSecretKey() {
        return props.getProperty("minio.secret.key", "avandocmsg123");
    }

    public String minioBucket() {
        return props.getProperty("minio.bucket", "avandocmsg");
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

    /** When true, Redis must be reachable (see {@link com.avandocmsg.messenger.api.config.RedisConfig}). */
    public boolean rateLimitAuthEnabled() {
        return Boolean.parseBoolean(props.getProperty("rate.limit.auth.enabled", "false"));
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

    /** Базовый URL Jitsi Meet (без завершающего слэша); комната = {@code base + "/" + room_slug}. */
    public String jitsiMeetBaseUrl() {
        return props.getProperty("jitsi.meet.base.url", "https://meet.jit.si");
    }

    /** Префикс имени комнаты до уникального суффикса. */
    public String conferenceRoomPrefix() {
        return props.getProperty("conference.room.prefix", "avandocmsg-");
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
        return Boolean.parseBoolean(props.getProperty("retention.default.legal_hold", "false"));
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
        return Boolean.parseBoolean(props.getProperty("retention.worker.enabled", "false"));
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
     * Allow {@code POST /admin/chats/{chatId}/export-suggest} (local / NATS dispatch). Env:
     * {@code EXPORT_ADMIN_SUGGEST_ENABLED}; default {@code false}.
     */
    public boolean exportAdminSuggestEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.admin.suggest.enabled", "false"));
    }

    /**
     * Allow {@code POST /admin/chats/{chatId}/export} (queue export job). Env:
     * {@code EXPORT_ADMIN_EXPORT_ENABLED}; default {@code false}.
     */
    public boolean exportAdminExportEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.admin.export.enabled", "false"));
    }

    /**
     * On {@code msg.export.suggested}, enqueue export (deduped). Env: {@code EXPORT_AUTO_QUEUE_ON_SUGGESTED};
     * default {@code false}.
     */
    public boolean exportAutoQueueOnSuggestedEnabled() {
        return Boolean.parseBoolean(props.getProperty("export.auto.queue.on.suggested.enabled", "false"));
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
        var raw = props.getProperty("hotplug.heartbeat.ttl.ms", "30000").trim();
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
        return Boolean.parseBoolean(props.getProperty("hotplug.indexer.presence.required", "false"));
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
}
