package com.avandocmsg.messenger.worker.retention;

/**
 * Дефолты платформы для слоя org в SQL ретенции (те же env, что и {@code AppConfig} в core-api).
 */
public record RetentionPlatformDefaults(
    Integer hotBodyMaxAgeDays,
    boolean defaultDeepArchiveEnabled,
    boolean defaultLegalHold
) {
    /** Верхняя граница для {@link #snapshotTempfileThresholdBytesFromEnv()} (1 GiB). */
    public static final long SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX = 1024L * 1024L * 1024L;

    /**
     * Дефолт для {@link #minioMultipartThresholdBytesFromEnv()}: без multipart-ветки (всегда прежний
     * {@code putObject} со {@code InputStream} для temp-file снимка), пока явно не задан env.
     */
    public static final long MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT = Long.MAX_VALUE;

    public static RetentionPlatformDefaults fromEnv() {
        return new RetentionPlatformDefaults(
            parseIntOrNull(System.getenv("RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS")),
            parseBool(System.getenv("RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED"), true),
            parseBool(System.getenv("RETENTION_DEFAULT_LEGAL_HOLD"), false)
        );
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean parseBool(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    static int batchLimitFromEnv() {
        var raw = System.getenv("RETENTION_BATCH_LIMIT");
        if (raw == null || raw.isBlank()) {
            return 25;
        }
        try {
            return Math.max(1, Math.min(500, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return 25;
        }
    }

    static boolean requireMinioFromEnv() {
        return parseBool(System.getenv("RETENTION_REQUIRE_MINIO"), true);
    }

    static boolean jdbcLooksLikePostgres(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }

    /**
     * Удерживать на одном JDBC-соединении PostgreSQL session advisory lock на весь проход hot-body
     * (см. {@code RetentionAdvisoryLockIds}, {@code RetentionHotBodyJanitor}). Env: {@code RETENTION_USE_ADVISORY_LOCK};
     * по умолчанию {@code false} — без вызовов {@code pg_try_advisory_lock} / {@code pg_advisory_unlock}.
     */
    static boolean useAdvisoryLockFromEnv() {
        return useAdvisoryLockFromRaw(System.getenv("RETENTION_USE_ADVISORY_LOCK"));
    }

    /** Разбор для тестов и {@link #useAdvisoryLockFromEnv()}. */
    static boolean useAdvisoryLockFromRaw(String raw) {
        return parseBool(raw, false);
    }

    static String minioBucketFromEnv() {
        var b = System.getenv("MINIO_BUCKET");
        if (b == null || b.isBlank()) {
            return "deep-archive";
        }
        return b.trim();
    }

    /**
     * Учитывать таблицу {@code retention_hot_body_applied} (миграция {@code V013}) для исключения повторной обработки
     * и записи факта применения. Env: {@code RETENTION_USE_APPLIED_LOG}; по умолчанию {@code true}.
     */
    static boolean useAppliedLogFromEnv() {
        return parseBool(System.getenv("RETENTION_USE_APPLIED_LOG"), true);
    }

    /**
     * Писать строки в {@code audit_events} после успешной очистки тела. Env: {@code RETENTION_AUDIT_ENABLED};
     * по умолчанию {@code true}.
     */
    static boolean auditEnabledFromEnv() {
        return parseBool(System.getenv("RETENTION_AUDIT_ENABLED"), true);
    }

    /**
     * Бакет MinIO для объектов ретенции (снимки тел). Env: {@code RETENTION_MINIO_BUCKET}; если пусто — тот же,
     * что и для {@code MINIO_BUCKET} (см. {@link #minioBucketFromEnv()}).
     */
    static String retentionWriteBucketFromEnv(String minioBucketDefault) {
        var r = System.getenv("RETENTION_MINIO_BUCKET");
        if (r != null && !r.isBlank()) {
            return r.trim();
        }
        return minioBucketDefault;
    }

    /**
     * Префикс ключа объекта внутри бакета (заканчивается на {@code /}). Env: {@code RETENTION_MINIO_OBJECT_PREFIX};
     * по умолчанию {@code retention/body/}.
     */
    static String retentionObjectPrefixFromEnv() {
        return normalizeRetentionObjectPrefix(System.getenv("RETENTION_MINIO_OBJECT_PREFIX"));
    }

    /**
     * Перед записью снимков вызвать {@code bucketExists}/{@code makeBucket} для бакета ретенции (как у deep-archiver).
     * Env: {@code RETENTION_ENSURE_MINIO_BUCKET}; по умолчанию {@code true}. Отключите, если бакеты создаёт IaC.
     */
    static boolean ensureMinioBucketFromEnv() {
        return parseBool(System.getenv("RETENTION_ENSURE_MINIO_BUCKET"), true);
    }

    /**
     * Пауза перед первым проходом скана после старта процесса (секунды). Env: {@code RETENTION_INITIAL_DELAY_SECONDS};
     * по умолчанию {@code 0}. Удобно при совместном старте с core-api / миграциями.
     */
    static int initialDelaySecondsFromEnv() {
        var raw = System.getenv("RETENTION_INITIAL_DELAY_SECONDS");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(86400, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Порт HTTP для {@code /metrics} (Prometheus text). Env: {@code RETENTION_METRICS_PORT};
     * по умолчанию {@code 0} — сервер метрик не поднимается.
     */
    static int metricsPortFromEnv() {
        var raw = System.getenv("RETENTION_METRICS_PORT");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            var p = Integer.parseInt(raw.trim());
            if (p < 0 || p > 65535) {
                return 0;
            }
            return p;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * После прохода, если число успешно очищенных тел {@code >=} порога, вставляется одна строка сводного аудита
     * ({@code message.retention.bulk_cleared}). Env: {@code RETENTION_BULK_AUDIT_MIN_CLEARED}; по умолчанию {@code 0} — выключено.
     */
    static int bulkAuditMinClearedFromEnv() {
        var raw = System.getenv("RETENTION_BULK_AUDIT_MIN_CLEARED");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Перед {@code putObject} проверять существование снимка (HEAD/stat), чтобы не дублировать JSON в MinIO, если
     * тело уже в deep-archive (тот же бакет, ключ {@code messages/{id}.json}) или уже лежит снимок ретенции.
     * Env: {@code RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS}; по умолчанию {@code false} (безопасный дефолт).
     */
    static boolean skipSnapshotIfDeepExistsFromEnv() {
        return parseBool(System.getenv("RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS"), false);
    }

    /**
     * Режим «сухого» прохода hot-body: тот же SELECT кандидатов и метрики/лог, без {@code UPDATE}/MinIO/NATS/аудита.
     * Env: {@code RETENTION_DRY_RUN}; по умолчанию {@code false}.
     */
    static boolean dryRunFromEnv() {
        return parseBool(System.getenv("RETENTION_DRY_RUN"), false);
    }

    /**
     * Лимит времени выполнения JDBC для SELECT кандидатов hot-body и для {@code UPDATE messages SET content = NULL}
     * на пути воркера (секунды, {@link java.sql.Statement#setQueryTimeout(int)}). Env: {@code RETENTION_JDBC_QUERY_TIMEOUT_SECONDS};
     * по умолчанию {@code 0} — таймаут не задаётся (поведение драйвера по умолчанию). Значения {@code < 0} и нечисловые строки
     * трактуются как {@code 0}; верхняя граница {@code 86400} (сутки).
     */
    static int jdbcQueryTimeoutSecondsFromEnv() {
        var raw = System.getenv("RETENTION_JDBC_QUERY_TIMEOUT_SECONDS");
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            var v = Integer.parseInt(raw.trim());
            if (v < 0) {
                return 0;
            }
            return Math.min(v, 86400);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Пауза между обработкой соседних кандидатов в одном проходе hot-body (мс). Env: {@code RETENTION_INTER_MESSAGE_DELAY_MS};
     * по умолчанию {@code 0} — без паузы; значения ограничены диапазоном {@code 0…60000}.
     */
    static int interMessageDelayMsFromEnv() {
        return interMessageDelayMillisFromRaw(System.getenv("RETENTION_INTER_MESSAGE_DELAY_MS"));
    }

    /** Разбор для тестов и {@link #interMessageDelayMsFromEnv()}. */
    static int interMessageDelayMillisFromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            var v = Long.parseLong(raw.trim());
            if (v < 0) {
                return 0;
            }
            return (int) Math.min(v, 60_000L);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Порог UTF-8 длины {@code messages.content}: при значении {@code > 0} и длине контента строго больше порога
     * снимок JSON перед {@code putObject} пишется во временный файл под {@code java.io.tmpdir}. Env:
     * {@code RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES}; по умолчанию {@code 0} — выключено (прежний путь
     * {@code ObjectMapper.writeValueAsBytes}). Отрицательные и нечисловые строки → {@code 0}; сверху —
     * {@link #SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX}.
     */
    static long snapshotTempfileThresholdBytesFromEnv() {
        return snapshotTempfileThresholdBytesFromRaw(System.getenv("RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES"));
    }

    /** Разбор для тестов и {@link #snapshotTempfileThresholdBytesFromEnv()}. */
    static long snapshotTempfileThresholdBytesFromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v < 0) {
                return 0L;
            }
            return Math.min(v, SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Порог размера **файла** снимка на диске ({@code Files.size}): при temp-file пути и
     * {@code size >=} порога — {@link io.minio.MinioClient#uploadObject}; иначе — {@code putObject} со стримом.
     * Env: {@code RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES}; по умолчанию {@link #MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT}
     * (фактически только {@code putObject}). Нечисловые, {@code <= 0} и отсутствующий env → дефолт.
     * Типичное операционное значение для multipart — {@code 33554432} (32 MiB).
     */
    static long minioMultipartThresholdBytesFromEnv() {
        return minioMultipartThresholdBytesFromRaw(System.getenv("RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES"));
    }

    /** Разбор для тестов и {@link #minioMultipartThresholdBytesFromEnv()}. */
    static long minioMultipartThresholdBytesFromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v <= 0) {
                return MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT;
            }
            return v;
        } catch (NumberFormatException e) {
            return MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT;
        }
    }

    /** Нормализация префикса: без ведущих {@code /}, с завершающим {@code /}; пусто → {@code retention/body/}. */
    public static String normalizeRetentionObjectPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "retention/body/";
        }
        var s = raw.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isBlank()) {
            return "retention/body/";
        }
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return s;
    }
}
