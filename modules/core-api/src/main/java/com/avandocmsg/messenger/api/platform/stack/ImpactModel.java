package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImpactModel(
    @JsonProperty("performance_impact") String performanceImpact,
    @JsonProperty("resilience_impact") String resilienceImpact,
    @JsonProperty("resource_impact") String resourceImpact,
    @JsonProperty("price_impact") String priceImpact,
    @JsonProperty("admin_impact") String adminImpact
) {
    public boolean complete() {
        return notBlank(performanceImpact)
            && notBlank(resilienceImpact)
            && notBlank(resourceImpact)
            && notBlank(priceImpact)
            && notBlank(adminImpact);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
