package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.admin.ui.AdminUiContributor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionDescriptor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionKind;

import java.util.List;

public final class PluginAdminUiContributor implements AdminUiContributor {

    @Override
    public List<AdminUiSectionDescriptor> sections() {
        return List.of(
            new AdminUiSectionDescriptor(
                "plugins-l0-wizard",
                "Создать L0-бота",
                48,
                AdminUiSectionKind.JSON_PANEL,
                null
            ),
            new AdminUiSectionDescriptor(
                "plugins-policies",
                "Плагины: org policy",
                49,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/plugins/policies"
            ),
            new AdminUiSectionDescriptor(
                "plugins-presets",
                "Плагины: presets",
                50,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/plugins/presets"
            ),
            new AdminUiSectionDescriptor(
                "plugins-instances",
                "Плагины: instances",
                51,
                AdminUiSectionKind.JSON_PANEL,
                "/admin/plugins/instances"
            )
        );
    }
}
