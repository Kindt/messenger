package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnreadCountResponse(@JsonProperty("unread_count") int unreadCount) {}
