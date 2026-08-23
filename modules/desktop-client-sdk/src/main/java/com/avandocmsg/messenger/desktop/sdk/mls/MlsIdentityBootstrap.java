package com.avandocmsg.messenger.desktop.sdk.mls;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Generate and upload MLS key package (server phase-1). */
public final class MlsIdentityBootstrap {

    private static final String FLAG = "mls-key-uploaded";

    private MlsIdentityBootstrap() {}

    public static void ensureKeyPackageUploaded(KorusApiClient api, String token) throws Exception {
        var marker = markerPath();
        if (Files.exists(marker)) {
            return;
        }
        var kpg = KeyPairGenerator.getInstance("Ed25519");
        var kp = kpg.generateKeyPair();
        var pub = kp.getPublic().getEncoded();
        var sig = kp.getPrivate().getEncoded();
        api.uploadKeyPackage(
            token,
            Base64.getEncoder().encodeToString(pub),
            Base64.getEncoder().encodeToString(sig)
        );
        Files.writeString(marker, FLAG, StandardCharsets.UTF_8);
    }

    private static Path markerPath() {
        var base = System.getProperty("korus.desktop.data.dir");
        if (base != null && !base.isBlank()) {
            return Path.of(base).resolve("mls-identity-uploaded.flag");
        }
        return Path.of(System.getProperty("user.home"), ".korus-desktop", "mls-identity-uploaded.flag");
    }
}
