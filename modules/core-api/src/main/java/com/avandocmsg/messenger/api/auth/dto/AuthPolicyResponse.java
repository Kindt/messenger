package com.avandocmsg.messenger.api.auth.dto;

import com.avandocmsg.messenger.api.auth.policy.AuthProviderEntry;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AuthPolicyResponse(
    @JsonProperty("org_id") String orgId,
    @JsonProperty("allow_local_password") @JsonAlias("allowLocalPassword") boolean allowLocalPassword,
    @JsonProperty("allow_self_registration") @JsonAlias("allowSelfRegistration") boolean allowSelfRegistration,
    @JsonProperty("providers") List<AuthProviderEntry> providers,
    @JsonProperty("last_apply_status") @JsonAlias("lastApplyStatus") String lastApplyStatus,
    @JsonProperty("last_apply_error") @JsonAlias("lastApplyError") String lastApplyError
) {}
