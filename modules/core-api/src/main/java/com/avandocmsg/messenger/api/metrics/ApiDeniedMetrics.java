package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** Counters for denied operations (403); registered once on the default registry. */
public final class ApiDeniedMetrics {

    private static final Counter FILE_ACCESS_DENIED = Counter.build()
        .name("api_denied_file_access_total")
        .help("Authenticated requests denied file metadata or download (403)")
        .register();

    private static final Counter MESSAGE_SEND_DENIED = Counter.build()
        .name("api_denied_message_send_total")
        .help("Message send denied by policy, e.g. membership, ban, or block (403)")
        .register();

    private static final Counter IP_ALLOWLIST_DENIED = Counter.build()
        .name("api_denied_ip_allowlist_total")
        .help("Authenticated requests blocked by org IP allowlist (403)")
        .register();

    private static final Counter GEO_DENIED = Counter.build()
        .name("api_denied_geo_total")
        .help("Authenticated requests blocked by org geo deny policy (403)")
        .register();

    private ApiDeniedMetrics() {
    }

    public static void fileAccessDenied() {
        FILE_ACCESS_DENIED.inc();
    }

    public static void messageSendDenied() {
        MESSAGE_SEND_DENIED.inc();
    }

    public static void ipAllowlistDenied() {
        IP_ALLOWLIST_DENIED.inc();
    }

    public static void geoDenied() {
        GEO_DENIED.inc();
    }
}
