package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageSendEvent(
    String messageId,
    String chatId,
    String senderId,
    String type,
    String content,
    String clientMsgId,
    Long createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("attachment_file_id") String attachmentFileId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("visibility_ttl_seconds") Integer visibilityTtlSeconds,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("thread_id") String threadId
) {}
