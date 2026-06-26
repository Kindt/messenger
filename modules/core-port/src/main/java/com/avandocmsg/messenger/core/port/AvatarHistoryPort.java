package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;

/** Avatar change audit trail (spec 068 W9). */
public interface AvatarHistoryPort {

    void recordUserAvatar(UserId entityId, FileId fileId, UserId setByUserId);

    void recordChatAvatar(ChatId entityId, FileId fileId, UserId setByUserId);
}
