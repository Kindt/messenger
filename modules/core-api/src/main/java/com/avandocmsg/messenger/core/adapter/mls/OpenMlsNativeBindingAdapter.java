package com.avandocmsg.messenger.core.adapter.mls;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;

import java.util.UUID;

/**
 * OpenMLS native binding facade (spec 020 Phase 2+): reports JNI availability and delegates crypto to
 * {@link HybridOpenMlsBindingAdapter} until {@code openmls_ffi} encrypt/decrypt is wired.
 */
public final class OpenMlsNativeBindingAdapter implements OpenMlsBindingPort {

    private static final String NATIVE_VERSION = "openmls-native-pending";

    private final AppConfig appConfig;
    private final OpenMlsBindingPort hybridDelegate;
    private final boolean nativeLoaded;

    public OpenMlsNativeBindingAdapter(AppConfig appConfig, OpenMlsBindingPort hybridDelegate) {
        this(appConfig, hybridDelegate, OpenMlsNativeLoader.isLoaded(appConfig.openmlsNativeEnabled()));
    }

    OpenMlsNativeBindingAdapter(AppConfig appConfig, OpenMlsBindingPort hybridDelegate, boolean nativeLoaded) {
        this.appConfig = appConfig;
        this.hybridDelegate = hybridDelegate;
        this.nativeLoaded = nativeLoaded;
    }

    @Override
    public String wireProfile() {
        return OpenMlsWireLayout.WIRE_PROFILE;
    }

    @Override
    public String libraryVersion() {
        return nativeLoaded ? NATIVE_VERSION : hybridDelegate.libraryVersion();
    }

    @Override
    public boolean nativeBindingAvailable() {
        return nativeLoaded;
    }

    @Override
    public byte[] encryptWire(UUID chatId, UUID senderId, String plaintext) {
        if (nativeLoaded) {
            // JNI encrypt/decrypt hooks land here (T020040+); hybrid fallback until FFI ships.
        }
        return hybridDelegate.encryptWire(chatId, senderId, plaintext);
    }

    @Override
    public String decryptWireBase64(UUID chatId, String contentBase64) {
        if (nativeLoaded) {
            // JNI decrypt hook (T020040+).
        }
        return hybridDelegate.decryptWireBase64(chatId, contentBase64);
    }

    AppConfig appConfig() {
        return appConfig;
    }
}
