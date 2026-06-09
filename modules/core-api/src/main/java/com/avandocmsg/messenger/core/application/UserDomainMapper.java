package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.UserProfile;

public final class UserDomainMapper {
    private UserDomainMapper() {
    }

    public static com.avandocmsg.messenger.api.users.dto.UserProfile toResponse(UserProfile profile) {
        return new com.avandocmsg.messenger.api.users.dto.UserProfile(
            profile.id().value().toString(),
            profile.username(),
            profile.displayName(),
            profile.phone(),
            profile.hidden(),
            profile.createdAt(),
            profile.presenceStatus(),
            profile.lastSeenAt(),
            profile.orgId(),
            profile.privacyDisableReadReceipts());
    }
}
