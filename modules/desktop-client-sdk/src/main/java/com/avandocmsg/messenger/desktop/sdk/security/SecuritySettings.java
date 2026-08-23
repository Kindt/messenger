package com.avandocmsg.messenger.desktop.sdk.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Desktop security policy (FSTEC / ИС controls). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecuritySettings(
    boolean tlsPinningRequired,
    int idleLockMinutes,
    boolean clipboardAutoClearSec,
    boolean clearTokensOnExit,
    boolean auditLogEnabled,
    boolean soundNotifications,
    boolean osNotificationsEnabled,
    boolean blockScreenshots,
    boolean requireSecureUpdates
) {
    public SecuritySettings() {
        this(true, 15, true, false, true, true, true, false, true);
    }

    public static SecuritySettings fstecMaximum() {
        return new SecuritySettings(true, 5, true, true, true, true, true, true, true);
    }
}
