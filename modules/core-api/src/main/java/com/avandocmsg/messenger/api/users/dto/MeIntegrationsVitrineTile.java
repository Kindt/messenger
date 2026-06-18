package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeIntegrationsVitrineTile(
    String id,
    String label,
    @JsonProperty("connector_key") String connectorKey,
    @JsonProperty("icon_url") String iconUrl,
    @JsonProperty("info_url") String infoUrl,
    String status
) {}
