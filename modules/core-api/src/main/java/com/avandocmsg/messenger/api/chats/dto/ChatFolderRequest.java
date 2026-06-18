package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatFolderRequest(
    @JsonProperty("folder_tag") String folderTag
) {}
