package com.avandocmsg.messenger.api.admin.ui.dto;

import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionDescriptor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdminManifestResponse(
    @JsonProperty("sections") List<AdminUiSectionDescriptor> sections,
    @JsonProperty("api_version") String apiVersion
) {}
