package com.avandocmsg.messenger.common.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportCompletenessValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void validate_allMandatoryPresent_isComplete() {
        var completeness = MAPPER.createObjectNode();
        completeness.put("hotDbMessageRowsIncluded", true);
        completeness.put("referencedFileMetadataIncluded", true);
        completeness.put("solrIndexRequested", false);
        completeness.put("deepArchiveSnapshotsRequested", false);
        completeness.put("retentionSnapshotsRequested", false);
        completeness.set("gdprDisclosures", MAPPER.createArrayNode().add(MAPPER.createObjectNode()));

        var root = MAPPER.createObjectNode();
        root.set("chat", MAPPER.createObjectNode());
        root.set("chatMembers", MAPPER.createArrayNode());
        root.put("referencedUserCount", 0);
        root.set("referencedUsers", MAPPER.createArrayNode());

        var result = ExportCompletenessValidator.validate(
            completeness, root, ExportCompleteness.defaultRequiredFields(), false);
        assertTrue(result.isComplete());
        assertFalse(result.hasErrors());
    }

    @Test
    void validate_strictMode_missingChat_isError() {
        var completeness = MAPPER.createObjectNode();
        completeness.put("hotDbMessageRowsIncluded", true);
        completeness.set("gdprDisclosures", MAPPER.createArrayNode().add(MAPPER.createObjectNode()));
        var root = MAPPER.createObjectNode();

        var result = ExportCompletenessValidator.validate(
            completeness, root, ExportCompleteness.defaultRequiredFields(), true);
        assertFalse(result.isComplete());
        assertTrue(result.hasErrors());
    }

    @Test
    void validateAndBuild_writesMandatoryFields() {
        var completeness = MAPPER.createObjectNode();
        completeness.put("hotDbMessageRowsIncluded", true);
        completeness.set("gdprDisclosures", MAPPER.createArrayNode().add(MAPPER.createObjectNode()));
        var root = MAPPER.createObjectNode();
        root.set("chat", MAPPER.createObjectNode());
        root.set("chatMembers", MAPPER.createArrayNode());
        root.put("referencedUserCount", 1);

        var built = ExportCompletenessValidator.validateAndBuild(
            completeness, root, Set.of("messages", "chat", "gdpr_disclosures"), false);
        built.writeTo(completeness);
        assertTrue(completeness.path("complete").asBoolean());
        assertTrue(completeness.path("mandatoryFields").path("messages").asBoolean());
    }
}
