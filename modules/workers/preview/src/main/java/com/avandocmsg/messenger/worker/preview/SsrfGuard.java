package com.avandocmsg.messenger.worker.preview;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Blocks localhost, link-local, private, multicast, and IPv6 ULA targets before outbound HTTP (MVP SSRF guard).
 */
final class SsrfGuard {

    private SsrfGuard() {
    }

    static URI parseHttpUri(String rawUrl) throws IOException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IOException("Empty URL");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL", e);
        }
        var scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IOException("Only http/https allowed");
        }
        var host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("Missing host");
        }
        return uri;
    }

    static void validateHostAllowed(String host) throws IOException {
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IOException("DNS resolution failed for " + host, e);
        }
        if (resolved.length == 0) {
            throw new IOException("No addresses for host");
        }
        for (var addr : resolved) {
            if (isBlocked(addr)) {
                throw new IOException("Blocked address for host " + host + ": " + addr.getHostAddress());
            }
        }
    }

    static boolean isBlocked(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
            || addr.isMulticastAddress()) {
            return true;
        }
        if (addr instanceof Inet4Address a4) {
            var b = a4.getAddress();
            int o1 = b[0] & 0xff;
            int o2 = b[1] & 0xff;
            if (o1 == 0) {
                return true;
            }
            if (addr.isSiteLocalAddress()) {
                return true;
            }
            if (o1 == 169 && o2 == 254) {
                return true;
            }
            return false;
        }
        if (addr instanceof Inet6Address a6) {
            var b = a6.getAddress();
            if (b[0] == (byte) 0xfe && (b[1] & 0xc0) == (byte) 0x80) {
                return true;
            }
            if ((b[0] & 0xfe) == (byte) 0xfc) {
                return true;
            }
            if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0 && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                && b[8] == 0 && b[9] == 0 && b[10] == 0 && b[11] == 0 && b[12] == 0 && b[13] == 0 && b[14] == 0
                && b[15] == 1) {
                return true;
            }
            return addr.isSiteLocalAddress();
        }
        return false;
    }
}
