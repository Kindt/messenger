package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetUserOrganizationRequest(@JsonProperty("org_id") String orgId) {}
