package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StartMeshCallRequest(@JsonProperty("media_mode") String mediaMode) {}
