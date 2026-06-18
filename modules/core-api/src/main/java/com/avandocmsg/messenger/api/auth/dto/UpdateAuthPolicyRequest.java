package com.avandocmsg.messenger.api.auth.dto;

import com.avandocmsg.messenger.api.auth.policy.AuthProviderEntry;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UpdateAuthPolicyRequest(
    @JsonProperty("allow_local_password") @JsonAlias("allowLocalPassword") Boolean allowLocalPassword,
    @JsonProperty("allow_self_registration") @JsonAlias("allowSelfRegistration") Boolean allowSelfRegistration,
    @JsonProperty("providers") List<AuthProviderEntry> providers,
    @JsonProperty("apply_to_keycloak") @JsonAlias("applyToKeycloak") Boolean applyToKeycloak
) {}
