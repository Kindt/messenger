package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.users.dto.MeIntegrationsVitrineTile;

import java.util.List;

/** Static connector vitrine tiles for web launcher (spec 022 US25). */
final class MeIntegrationsVitrine {
    private MeIntegrationsVitrine() {
    }

    static List<MeIntegrationsVitrineTile> tiles() {
        return List.of(
            tile("outlook-mail", "Outlook (почта)", "outlook", "https://outlook.office.com/mail/"),
            tile("outlook-calendar", "Outlook (календарь)", "exchange", "https://outlook.office.com/calendar/"),
            tile("sharepoint", "SharePoint / OneDrive", "storage", "https://www.microsoft.com/microsoft-365/sharepoint/collaboration"),
            tile("jira", "Jira", "jira", "https://www.atlassian.com/software/jira"),
            tile("confluence", "Confluence", "confluence", "https://www.atlassian.com/software/confluence"),
            tile("naumen", "Naumen", "naumen", "https://www.naumen.ru/products/"),
            tile("bitrix", "Bitrix24", "bitrix", "https://www.bitrix24.ru/")
        );
    }

    private static MeIntegrationsVitrineTile tile(String id, String label, String key, String infoUrl) {
        return new MeIntegrationsVitrineTile(id, label, key, null, infoUrl, "scaffold");
    }
}
