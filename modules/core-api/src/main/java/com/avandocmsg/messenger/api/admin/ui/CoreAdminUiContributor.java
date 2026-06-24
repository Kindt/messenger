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
                "core-fleet-stats",
                "Инфраструктура (fleet)",
                11,
                AdminUiSectionKind.FLEET_GRID,
                "/admin/ui/fleet/snapshot"
            ),
            new AdminUiSectionDescriptor(
                "core-external-stack",
                "Внешний стек (023)",
                12,
                AdminUiSectionKind.JSON_PANEL,
                "/platform/external-stack/status"
            ),
            new AdminUiSectionDescriptor(
                "core-product-modules",
                "Состав продукта",
                13,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/ui/product-modules"
            ),
            new AdminUiSectionDescriptor(
                "core-export-compliance",
                "Экспорт / GDPR",
                14,
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
                "core-directory-sync",
                "Синхронизация каталога (LDAP)",
                22,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-ip-allowlist",
                "Список разрешённых IP (org)",
                23,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-migration-import",
                "Импорт миграции",
                24,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-federation-trust",
                "Доверие федерации",
                25,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-user-organization",
                "Пользователь → организация",
                26,
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
                "Отчёты о прочтении",
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
                "Юридическое удержание (extended)",
                41,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "core-purge-status",
                "Статус очистки",
                42,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/purge/status"
            )
        );
    }
}
