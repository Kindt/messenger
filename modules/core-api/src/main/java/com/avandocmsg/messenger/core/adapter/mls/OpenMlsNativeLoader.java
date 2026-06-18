package com.avandocmsg.messenger.core.adapter.mls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Probes optional OpenMLS JNI/FFI library ({@code openmls_ffi}). Not shipped in Phase 2. */
public final class OpenMlsNativeLoader {

    private static final Logger log = LoggerFactory.getLogger(OpenMlsNativeLoader.class);
    private static final Object LOCK = new Object();
    private static volatile Boolean loaded;

    private OpenMlsNativeLoader() {
    }

    /**
     * Attempts to load native library when {@code nativeEnabled} is true.
     * Env {@code OPENMLS_NATIVE_LIB} may point to an absolute path; otherwise {@code System.loadLibrary("openmls_ffi")}.
     */
    public static boolean isLoaded(boolean nativeEnabled) {
        if (!nativeEnabled) {
            return false;
        }
        if (loaded != null) {
            return loaded;
        }
        synchronized (LOCK) {
            if (loaded != null) {
                return loaded;
            }
            loaded = probeLoad();
            return loaded;
        }
    }

    private static boolean probeLoad() {
        var libPath = System.getenv("OPENMLS_NATIVE_LIB");
        try {
            if (libPath != null && !libPath.isBlank()) {
                System.load(libPath.trim());
            } else {
                System.loadLibrary("openmls_ffi");
            }
            log.info("OpenMLS native library loaded");
            return true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            log.info("OpenMLS native library not available: {}", e.getMessage());
            return false;
        }
    }
}
