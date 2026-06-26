package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateOrganizationRequest(@JsonProperty("logo_file_id") String logoFileId) {}
