package com.avandocmsg.messenger.api.platform.stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExternalStackPolicyValidator {

    private ExternalStackPolicyValidator() {
    }

    public static ValidationResult validateFailurePolicy(String componentUse, String policy) {
        var failures = new ArrayList<String>();
        var allowed = allowedPolicies(componentUse);
        if (!allowed.contains(policy)) {
            failures.add("component " + componentUse + " does not allow " + policy);
        }
        return new ValidationResult(false, failures, List.of(), false, Map.of());
    }

    private static List<String> allowedPolicies(String componentUse) {
        return switch (componentUse) {
            case "idp", "relational-db-hot" -> List.of("fail_closed");
            case "cache:read-cache", "notifications", "media", "turn" -> List.of("fail_open", "degraded");
            case "cache:rate-limit" -> List.of("fail_closed", "controlled_degraded");
            case "dlp" -> List.of("fail_open", "fail_closed", "quarantine");
            default -> List.of("degraded", "fail_closed");
        };
    }
}
