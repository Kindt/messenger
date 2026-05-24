package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdatePrivacyRequest(
    @JsonProperty("privacy_disable_read_receipts") Boolean privacyDisableReadReceipts
) {}
