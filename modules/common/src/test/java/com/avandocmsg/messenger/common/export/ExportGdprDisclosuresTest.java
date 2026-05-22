package com.avandocmsg.messenger.common.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportGdprDisclosuresTest {

    @Test
    void referenceTemplate_includesCoreIds() {
        var gdpr = ExportGdprDisclosures.referenceTemplate();
        assertTrue(gdpr.size() >= 10);
        assertTrue(containsId(gdpr, "e2ee_plaintext"));
        assertTrue(containsId(gdpr, "file_binary"));
    }

    @Test
    void build_fileBinaryDisclosureNoteReflectsBodiesFlag() {
        var without = ExportGdprDisclosures.build(
            true, true, true, true, true, false, 0, false, false, 0, false, 0, 0, 0, false, false, 0);
        assertTrue(find(without, "file_binary").orElseThrow().get("note").asText()
            .contains("EXPORT_REPLAY_INCLUDE_FILE_BODIES"));

        var withBodies = ExportGdprDisclosures.build(
            true, true, true, true, true, false, 0, false, false, 0, false, 0, 0, 0, true, false, 0);
        assertTrue(find(withBodies, "file_binary").orElseThrow().get("note").asText()
            .contains("attachments/"));
    }

    private static boolean containsId(
        com.fasterxml.jackson.databind.JsonNode gdpr,
        String id
    ) {
        return find(gdpr, id).isPresent();
    }

    private static java.util.Optional<com.fasterxml.jackson.databind.JsonNode> find(
        com.fasterxml.jackson.databind.JsonNode gdpr,
        String id
    ) {
        for (var item : gdpr) {
            if (id.equals(item.path("id").asText())) {
                return java.util.Optional.of(item);
            }
        }
        return java.util.Optional.empty();
    }
}
