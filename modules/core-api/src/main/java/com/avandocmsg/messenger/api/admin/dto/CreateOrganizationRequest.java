package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrganizationRequest(@JsonProperty("name") String name) {}
