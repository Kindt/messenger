package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateLocaleRequest(@JsonProperty("ui_locale") String uiLocale) {}
