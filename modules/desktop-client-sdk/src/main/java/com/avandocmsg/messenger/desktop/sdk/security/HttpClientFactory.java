package com.avandocmsg.messenger.desktop.sdk.security;

import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;

/** TLS pinning + lab self-signed trust (per server). */
public final class HttpClientFactory {

    private HttpClientFactory() {}

    public static OkHttpClient forServer(ServerEntry entry, SecuritySettings policy) {
        return forServer(entry, policy.tlsPinningRequired());
    }

    public static OkHttpClient forServer(ServerEntry entry, boolean pinningRequired) {
        var builder = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS);
        if (entry.trustSelfSigned()) {
            builder.sslSocketFactory(trustAllSsl(), trustAllManager());
            builder.hostnameVerifier((h, s) -> true);
        }
        var pin = entry.pinnedCertSha256();
        if (pinningRequired && pin != null && !pin.isBlank()) {
            var host = hostFromUrl(entry.apiBaseUrl());
            if (host != null) {
                var normalized = pin.startsWith("sha256/") ? pin : "sha256/" + pin;
                builder.certificatePinner(new CertificatePinner.Builder().add(host, normalized).build());
            }
        }
        return builder.build();
    }

    public static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    private static String hostFromUrl(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("java:S4830") // trust-all only when ServerEntry.trustSelfSigned() (explicit lab/QEMU opt-in)
    private static X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // Lab self-signed: client certs not used.
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // Lab self-signed: caller opted in via ServerEntry.trustSelfSigned().
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static javax.net.ssl.SSLSocketFactory trustAllSsl() {
        try {
            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { trustAllManager() }, new java.security.SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
