package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.common.admin.ui.AdminUiContributor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionDescriptor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionKind;

import java.util.List;

/**
 * Ядерный модуль: стартовые разделы встроенной админ-консоли (статистика, справочники admin API).
 */
public final class CoreAdminUiContributor implements AdminUiContributor {

    @Override
    public List<AdminUiSectionDescriptor> sections() {
        return List.of(
            new AdminUiSectionDescriptor(
                "core-server-stats",
                "Статистика сервера",
                10,
                AdminUiSectionKind.CORE_STATS,
                "/admin/ui/stats"
            ),
            new AdminUiSectionDescriptor(
                "core-export-compliance",
                "Экспорт / GDPR",
                12,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/ui/export-compliance-guide"
            ),
            new AdminUiSectionDescriptor(
                "core-admin-session",
                "Сессия (admin)",
                15,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/session"
            ),
            new AdminUiSectionDescriptor(
                "core-organizations",
                "Организации",
                20,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/organizations"
            ),
            new AdminUiSectionDescriptor(
                "core-auth-policy",
                "Вход / Identity",
                21,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-user-organization",
                "Пользователь → организация",
                25,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-audit-events",
                "Аудит (последние)",
                30,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/audit-events"
            ),
            new AdminUiSectionDescriptor(
                "core-admin-manifest",
                "Манифест консоли",
                35,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/ui/manifest"
            ),
            new AdminUiSectionDescriptor(
                "core-read-receipts",
                "Read receipts",
                38,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/read-receipts/stats"
            ),
            new AdminUiSectionDescriptor(
                "core-e2ee-mls",
                "E2EE / MLS",
                39,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/e2ee/status"
            ),
            new AdminUiSectionDescriptor(
                "core-retention",
                "Ретенция (org / chat)",
                40,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-legal-hold",
                "Legal hold (extended)",
                41,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-purge-status",
                "Purge status",
                42,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/purge/status"
            )
        );
    }
}
