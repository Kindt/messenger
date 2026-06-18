package com.avandocmsg.messenger.core.adapter.mls;

import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;

import java.util.UUID;

/** Metadata-only placeholder when MLS service is unavailable (tests). Prefer {@link HybridOpenMlsBindingAdapter}. */
public final class NoOpOpenMlsBindingAdapter implements OpenMlsBindingPort {

    public static final NoOpOpenMlsBindingAdapter INSTANCE = new NoOpOpenMlsBindingAdapter();

    private NoOpOpenMlsBindingAdapter() {
    }

    @Override
    public String wireProfile() {
        return OpenMlsWireLayout.WIRE_PROFILE;
    }

    @Override
    public String libraryVersion() {
        return OpenMlsWireLayout.LIBRARY_VERSION;
    }

    @Override
    public boolean nativeBindingAvailable() {
        return false;
    }

    @Override
    public byte[] encryptWire(UUID chatId, UUID senderId, String plaintext) {
        return null;
    }

    @Override
    public String decryptWireBase64(UUID chatId, String contentBase64) {
        return null;
    }
}
