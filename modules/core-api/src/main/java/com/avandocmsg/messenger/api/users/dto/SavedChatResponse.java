package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SavedChatResponse(@JsonProperty("saved_chat_id") String savedChatId) {}
