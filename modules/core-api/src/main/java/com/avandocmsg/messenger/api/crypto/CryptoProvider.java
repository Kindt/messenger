package com.avandocmsg.messenger.api.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

public final class CryptoProvider {
    private static final Logger log = LoggerFactory.getLogger(CryptoProvider.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle provider registered");
        }
    }

    public static void ensureLoaded() {
    }

    private CryptoProvider() {
    }
}
