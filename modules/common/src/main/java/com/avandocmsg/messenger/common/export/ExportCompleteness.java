package com.avandocmsg.messenger.common.export;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Typed view of export completeness with mandatory field checklist (GDPR policy).
 */
public record ExportCompleteness(
    boolean complete,
    Map<String, Boolean> mandatoryFields
) {
    public static ExportCompleteness fromValidation(
        Map<String, Boolean> mandatoryFields,
        ValidationResult validation
    ) {
        return new ExportCompleteness(validation.isComplete(), Map.copyOf(mandatoryFields));
    }

    public void writeTo(ObjectNode completenessNode) {
        completenessNode.put("complete", complete);
        var fields = completenessNode.putObject("mandatoryFields");
        mandatoryFields.forEach(fields::put);
    }

    public static Set<String> defaultRequiredFields() {
        return Set.of(
            "messages",
            "chat",
            "chat_members",
            "referenced_users",
            "referenced_files",
            "solr_index",
            "deep_archive",
            "retention_snapshots",
            "gdpr_disclosures"
        );
    }
}
