package com.avandocmsg.messenger.api.admin.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminExportSmokeHintsTest {

    @Test
    void commands_includeCompliancePack() {
        var cmds = AdminExportSmokeHints.commands();
        assertFalse(cmds.isEmpty());
        assertTrue(cmds.stream().anyMatch(c -> c.title().contains("Compliance pack")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-export-compliance-pack")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-export-compliance-flow")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-admin-export-download")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-admin-export-inspect")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-export-compliance-flow.sh --include-file")
            || c.commandPs().contains("smoke-export-compliance-flow.ps1 -IncludeFile")));
        assertTrue(cmds.stream().anyMatch(c -> c.commandSh().contains("smoke-openapi-export-compliance")));
        assertTrue(cmds.stream().anyMatch(c -> c.title().contains("Compliance flow + file")));
    }
}
