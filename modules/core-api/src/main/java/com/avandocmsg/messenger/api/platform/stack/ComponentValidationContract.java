package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ComponentValidationContract(
    @JsonProperty("component") String component,
    @JsonProperty("required_checks") List<String> requiredChecks,
    @JsonProperty("failure_policy") String failurePolicy
) {
    public ComponentValidationContract {
        requiredChecks = requiredChecks == null ? List.of() : List.copyOf(requiredChecks);
    }
}
