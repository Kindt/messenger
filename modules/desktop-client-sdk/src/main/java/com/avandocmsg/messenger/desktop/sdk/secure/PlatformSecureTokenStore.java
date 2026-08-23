package com.avandocmsg.messenger.desktop.sdk.secure;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Factory: encrypted at-rest tokens + audit + legacy migration. */
public final class PlatformSecureTokenStore {

    private PlatformSecureTokenStore() {}

    public static SecureTokenStore create(Path stateDir) throws IOException {
        var audit = new SecurityAuditLog(stateDir);
        byte[] master = null;
        try {
            master = new MasterKeyStore(stateDir).loadOrCreate();
            var store = new AesGcmSecureTokenStore(stateDir, master);
            migratePlainJson(stateDir, store, audit);
            audit.record("token.store.init", storeLabel());
            return new AuditingTokenStore(store, audit);
        } finally {
            MasterKeyStore.wipe(master);
        }
    }

    private static void migratePlainJson(Path stateDir, AesGcmSecureTokenStore target, SecurityAuditLog audit)
        throws IOException {
        var legacy = stateDir.resolve("tokens.json");
        if (!Files.exists(legacy)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> m = JsonSupport.mapper().readValue(Files.readString(legacy), Map.class);
        for (var e : m.entrySet()) {
            target.put(e.getKey(), e.getValue());
        }
        Files.delete(legacy);
        audit.record("token.migrate", "tokens.json -> tokens.enc");
    }

    private static String storeLabel() {
        if (WindowsDpapiProtector.isAvailable()) {
            return "aes-gcm+dpapi";
        }
        return "aes-gcm+posix";
    }
}
