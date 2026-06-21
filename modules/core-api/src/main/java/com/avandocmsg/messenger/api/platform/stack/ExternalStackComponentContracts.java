package com.avandocmsg.messenger.api.platform.stack;

import java.util.List;
import java.util.Map;

public final class ExternalStackComponentContracts {

    private static final Map<String, ComponentValidationContract> CONTRACTS = Map.ofEntries(
        Map.entry("relational-db-hot",
            contract(
                "relational-db-hot",
                List.of(
                "jdbc_connectivity",
                "version_allow_list",
                "encoding_timezone_collation",
                "flyway_privileges",
                "required_extensions",
                "pool_sizing",
                "lock_timeout"
                ),
                "fail_closed"
            )),
        Map.entry("object-storage",
            contract(
                "object-storage",
                List.of(
                "tls_auth",
                "bucket_policy",
                "put_get_head_delete_list",
                "multipart",
                "checksum",
                "lifecycle_object_lock"
                ),
                "uploads_controlled_error_no_purge_without_snapshot"
            )),
        Map.entry("messaging",
            contract(
                "messaging",
                List.of(
                "auth_tls",
                "publish_subscribe_subject_prefixes",
                "queue_groups",
                "jetstream_if_required",
                "max_payload",
                "drain_behavior"
                ),
                "workers_pause_no_silent_fallback"
            )),
        Map.entry("idp",
            contract(
                "idp",
                List.of(
                "issuer_jwks_tls",
                "token_signature_audience_issuer",
                "clock_skew",
                "required_claims_user_org_roles",
                "managed_keycloak_admin_api_if_required"
                ),
                "fail_closed"
            )),
        Map.entry("cache",
            contract(
                "cache",
                List.of(
                "ping_auth_tls",
                "command_subset_get_set_del_expire_counters_ttl",
                "key_prefix_isolation",
                "cluster_sentinel_support_flag"
                ),
                "read_cache_fail_open_rate_limit_policy"
            )),
        Map.entry("web-edge",
            contract(
                "web-edge",
                List.of(
                "health_routing",
                "websocket_upgrade",
                "upload_limits",
                "forwarded_headers",
                "tls_chain",
                "security_headers"
                ),
                "app_or_realtime_degraded"
            )),
        Map.entry("media",
            contract(
                "media",
                List.of(
                "livekit_token_issue",
                "room_join",
                "vks_integration_candidate_gate"
                ),
                "calls_degraded_to_text_chat"
            )),
        Map.entry("turn",
            contract(
                "turn",
                List.of(
                "realm_secret",
                "relay_reachability",
                "udp_tcp_ports"
                ),
                "call_relay_degraded"
            )),
        Map.entry("notifications",
            contract(
                "notifications",
                List.of(
                "vapid_config",
                "gateway_config",
                "best_effort_semantics"
                ),
                "best_effort_degraded"
            )),
        Map.entry("dlp",
            contract(
                "dlp",
                List.of(
                "endpoint_auth_tls",
                "verdict_schema",
                "timeout",
                "payload_limits",
                "tenant_policy",
                "e2ee_plaintext_boundary"
                ),
                "policy_configured_fail_open_or_quarantine"
            )),
        Map.entry("integrations",
            contract(
                "integrations",
                List.of(
                "webhook_plugin_bot_gateway_endpoint",
                "event_schema",
                "retry_timeout",
                "audit_boundary"
                ),
                "audit_and_retry_degraded"
            ))
    );

    private ExternalStackComponentContracts() {
    }

    private static ComponentValidationContract contract(
        String component,
        List<String> requiredChecks,
        String failurePolicy
    ) {
        return new ComponentValidationContract(component, requiredChecks, failurePolicy);
    }

    public static ComponentValidationContract contractFor(String component) {
        var contract = CONTRACTS.get(component);
        if (contract == null) {
            throw new IllegalArgumentException("No external stack contract for component: " + component);
        }
        return contract;
    }
}
