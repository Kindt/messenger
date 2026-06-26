package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

/** ACL checks for avatar file access (spec 068). */
public interface AvatarAccessPort {

    /** Whether {@code viewerId} may load {@code fileId} as an avatar (user or chat). */
    boolean viewerMayAccessAsAvatar(UserId viewerId, FileId fileId);

    Optional<UserId> findUserIdByAvatarFile(FileId fileId);

    Optional<ChatId> findChatIdByAvatarFile(FileId fileId);

    /** Whether the user may upload or replace their avatar via API (not LDAP import). */
    default boolean userMayUploadAvatar(UserId userId) {
        return true;
    }
}
