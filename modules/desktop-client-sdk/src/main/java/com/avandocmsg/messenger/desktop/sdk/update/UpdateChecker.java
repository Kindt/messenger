package com.avandocmsg.messenger.desktop.sdk.update;

import java.io.IOException;
import okhttp3.OkHttpClient;

/**
 * @deprecated Use {@link UpdateService}.
 */
@Deprecated
public final class UpdateChecker {

    private final UpdateService delegate;

    public UpdateChecker(OkHttpClient http) {
        this.delegate = new UpdateService(http);
    }

    public UpdateManifestDto fetchManifest(String feedUrl) throws IOException {
        return delegate.fetchManifest(feedUrl);
    }

    public boolean verifySha256(byte[] bytes, String expectedHex) {
        return delegate.verifySha256(bytes, expectedHex);
    }

    public byte[] download(String url) throws IOException {
        return delegate.downloadArtifact(url);
    }
}
