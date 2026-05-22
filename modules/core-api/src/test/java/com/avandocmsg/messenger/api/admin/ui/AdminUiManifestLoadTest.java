package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUiManifestLoadTest {

    @Test
    void load_includesCoreSectionsFromSpi() {
        var manifest = AdminUiManifest.load(CoreAdminUiContributor.class.getClassLoader());
        assertTrue(manifest.sections().size() >= 8);
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-export-compliance".equals(s.id())));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-server-stats".equals(s.id())));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-organizations".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-audit-events".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-retention".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-user-organization".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-admin-session".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertTrue(manifest.sections().stream().anyMatch(s -> "core-admin-manifest".equals(s.id())
            && s.kind() == AdminUiSectionKind.JSON_PANEL));
        assertEquals("core-server-stats", manifest.sections().get(0).id());
    }
}
