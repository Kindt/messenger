package com.avandocmsg.messenger.worker.exportreplay;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRetentionPolicyLoaderTest {

    @Test
    void effectivePolicy_mergesChatOverOrg() {
        var orgId = UUID.randomUUID();
        var platform = new ExportPlatformDefaults(null, null, true, true, false);
        var org = Optional.of(new ExportRetentionPolicyLoader.OrgPolicyRow(90, 365, true, true, false));
        var chat = Optional.of(new ExportRetentionPolicyLoader.ChatPolicyRow(30, null, true, true, false));

        var node = ExportRetentionPolicyLoader.toJsonNode(Optional.of(orgId), chat, org, platform);

        assertEquals(30, node.get("hotMessageBodyMaxAgeDays").asInt());
        assertEquals(365, node.get("hotMetadataMinAgeDays").asInt());
        assertTrue(node.get("exportRecommendedBeforeHotBodyPurge").asBoolean());
    }

    @Test
    void exportRecommended_falseWhenLegalHold() {
        var platform = new ExportPlatformDefaults(90, null, true, true, false);
        var chat = Optional.of(new ExportRetentionPolicyLoader.ChatPolicyRow(null, null, true, true, true));

        var node = ExportRetentionPolicyLoader.toJsonNode(Optional.empty(), chat, Optional.empty(), platform);

        assertFalse(node.get("exportRecommendedBeforeHotBodyPurge").asBoolean());
    }

    @Test
    void exportCompleteness_listsKnownLimits() {
        var node = ExportRetentionPolicyLoader.buildExportCompleteness(
            true, true, false, true, true, false, 0, 0, false, false, 0, 0, false, 0, 0, 2, 8, false, false, 0);
        assertEquals(2, node.get("e2eeMessageCount").asInt());
        assertEquals(8, node.get("nonE2eeMessageCount").asInt());
        assertTrue(node.get("e2eeMessagePlaintextOmitted").asBoolean());
        assertFalse(node.get("deepArchiveMessageBodiesIncluded").asBoolean());
        assertFalse(node.get("retentionMinioSnapshotsIncluded").asBoolean());
        assertTrue(node.get("messageTtlFilterApplied").asBoolean());
    }

    @Test
    void exportCompleteness_reflectsDeepArchiveFound() {
        var node = ExportRetentionPolicyLoader.buildExportCompleteness(
            true, true, true, true, true, true, 10, 3, true, false, 0, 0, false, 0, 0, 0, 10, false, false, 0);
        assertTrue(node.get("deepArchiveSnapshotsRequested").asBoolean());
        assertEquals(3, node.get("deepArchiveSnapshotsFound").asInt());
        assertTrue(node.get("deepArchiveMessageBodiesIncluded").asBoolean());
    }

    @Test
    void exportCompleteness_reflectsRetentionFound() {
        var node = ExportRetentionPolicyLoader.buildExportCompleteness(
            true, true, true, true, true, false, 0, 0, false, true, 20, 5, false, 0, 0, 0, 20, false, false, 0);
        assertTrue(node.get("retentionSnapshotsRequested").asBoolean());
        assertEquals(5, node.get("retentionSnapshotsFound").asInt());
        assertTrue(node.get("retentionMinioSnapshotsIncluded").asBoolean());
    }

    @Test
    void exportCompleteness_includesGdprDisclosures() {
        var node = ExportRetentionPolicyLoader.buildExportCompleteness(
            true, false, false, false, true, true, 4, 2, false, false, 0, 0, false, 0, 0, 1, 3, true, false, 0);
        var gdpr = node.get("gdprDisclosures");
        assertTrue(containsDisclosureId(gdpr, "e2ee_file_refs"));
        assertTrue(gdpr.isArray());
        assertTrue(gdpr.size() >= 5);
        assertTrue(containsDisclosureId(gdpr, "e2ee_plaintext"));
    }

    @Test
    void gdprDisclosures_marksFileBinaryAbsent() {
        var gdpr = ExportRetentionPolicyLoader.buildGdprDisclosures(
            true, true, true, true, true, false, 0, false, false, 0, false, 0, 0, 0, false, false, 0);
        var fileBinary = disclosureById(gdpr, "file_binary");
        assertTrue(fileBinary.isPresent());
        assertFalse(fileBinary.get().get("included").asBoolean());
    }

    private static boolean containsDisclosureId(
        com.fasterxml.jackson.databind.JsonNode gdpr,
        String id
    ) {
        return disclosureById(gdpr, id).isPresent();
    }

    private static java.util.Optional<com.fasterxml.jackson.databind.JsonNode> disclosureById(
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
