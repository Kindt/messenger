package com.avandocmsg.messenger.core.port;

import java.util.UUID;

/** Message row that shares a file with a chat member (file ACL / jump-to-message). */
public record FileMessageRef(UUID messageId, UUID chatId) {}
