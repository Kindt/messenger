package com.avandocmsg.messenger.desktop.sdk.update;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.HexFormat;
import java.util.Objects;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/** Fetch manifest, compare version, optional signature + sha256 verify. */
public final class UpdateService {

    public record UpdateCheckResult(
        boolean updateAvailable,
        String currentVersion,
        String latestVersion,
        UpdateArtifactDto artifact,
        UpdateManifestDto manifest
    ) {}

    private final OkHttpClient http;
    private final UpdateVerifier verifier;

    public UpdateService(OkHttpClient http) {
        this(http, new UpdateVerifier());
    }

    public UpdateService(OkHttpClient http, UpdateVerifier verifier) {
        this.http = Objects.requireNonNull(http, "http");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    /** Desktop UI entry — keeps OkHttp off desktop-client compile classpath. */
    public static UpdateService withDefaultClient() {
        return new UpdateService(KorusApiClient.defaultHttpClient());
    }

    public UpdateManifestDto fetchManifest(String feedUrl) throws IOException {
        if (feedUrl.startsWith("file:")) {
            var path = Path.of(java.net.URI.create(feedUrl));
            return JsonSupport.mapper().readValue(Files.readString(path), UpdateManifestDto.class);
        }
        var request = new Request.Builder().url(feedUrl).get().build();
        try (var response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("manifest HTTP " + response.code());
            }
            return JsonSupport.mapper().readValue(response.body().string(), UpdateManifestDto.class);
        }
    }

    public UpdateCheckResult checkForUpdate(
        String feedUrl,
        String currentVersion,
        String platform,
        PublicKey signaturePublicKey,
        String signaturePayloadUtf8
    ) throws IOException {
        var manifest = fetchManifest(feedUrl);
        if (manifest.schemaVersion() != 1) {
            throw new IOException("unsupported schema_version " + manifest.schemaVersion());
        }
        if (signaturePublicKey != null && manifest.signature() != null && signaturePayloadUtf8 != null) {
            if (!verifier.verifyBase64(signaturePublicKey, signaturePayloadUtf8.getBytes(java.nio.charset.StandardCharsets.UTF_8), manifest.signature().value())) {
                throw new IOException("manifest signature invalid");
            }
        }
        var artifact = manifest.artifacts().stream()
            .filter(a -> platform.equals(a.platform()))
            .findFirst()
            .orElse(null);
        var newer = VersionComparer.isNewer(manifest.version(), currentVersion);
        return new UpdateCheckResult(newer, currentVersion, manifest.version(), artifact, manifest);
    }

    public boolean verifySha256(byte[] bytes, String expectedHex) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            var actual = HexFormat.of().formatHex(digest);
            return actual.equalsIgnoreCase(expectedHex);
        } catch (Exception e) {
            return false;
        }
    }

    public byte[] downloadArtifact(String url) throws IOException {
        var request = new Request.Builder().url(url).get().build();
        try (var response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("download HTTP " + response.code());
            }
            return response.body().bytes();
        }
    }
}
