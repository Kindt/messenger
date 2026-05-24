package com.avandocmsg.messenger.common.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates export completeness JSON against configurable mandatory fields. */
public final class ExportCompletenessValidator {

    private ExportCompletenessValidator() {
    }

    public static ValidationResult validate(
        ObjectNode completeness,
        ObjectNode exportRoot,
        Set<String> requiredFields,
        boolean strict
    ) {
        var result = new ValidationResult();
        var mandatoryFields = new LinkedHashMap<String, Boolean>();
        for (var field : requiredFields) {
            var present = isFieldPresent(field, completeness, exportRoot);
            mandatoryFields.put(field, present);
            if (!present) {
                if (strict) {
                    result.addError("missing_field", field);
                } else {
                    result.addWarning("missing_field", field);
                }
            }
        }
        return result;
    }

    public static ExportCompleteness validateAndBuild(
        ObjectNode completeness,
        ObjectNode exportRoot,
        Set<String> requiredFields,
        boolean strict
    ) {
        var validation = validate(completeness, exportRoot, requiredFields, strict);
        var mandatoryFields = new LinkedHashMap<String, Boolean>();
        for (var field : requiredFields) {
            mandatoryFields.put(field, isFieldPresent(field, completeness, exportRoot));
        }
        return ExportCompleteness.fromValidation(mandatoryFields, validation);
    }

    static boolean isFieldPresent(String field, ObjectNode completeness, ObjectNode exportRoot) {
        var key = field.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "messages" -> completeness.path("hotDbMessageRowsIncluded").asBoolean(false);
            case "chat" -> exportRoot.has("chat") && exportRoot.get("chat").isObject();
            case "chat_members" -> exportRoot.has("chatMembers") && exportRoot.get("chatMembers").isArray();
            case "referenced_users" -> exportRoot.path("referencedUserCount").asInt(-1) >= 0
                || exportRoot.has("referencedUsers");
            case "referenced_files" -> completeness.path("referencedFileMetadataIncluded").asBoolean(false)
                || exportRoot.has("referencedFiles");
            case "solr_index" -> !completeness.path("solrIndexRequested").asBoolean(false)
                || completeness.path("solrIndexIncluded").asBoolean(false)
                || completeness.path("solrIndexExported").asInt(0) >= 0;
            case "deep_archive" -> !completeness.path("deepArchiveSnapshotsRequested").asBoolean(false)
                || completeness.path("deepArchiveSnapshotsFound").asInt(0) >= 0;
            case "retention_snapshots" -> !completeness.path("retentionSnapshotsRequested").asBoolean(false)
                || completeness.path("retentionSnapshotsFound").asInt(0) >= 0;
            case "gdpr_disclosures" -> hasNonEmptyArray(completeness.get("gdprDisclosures"));
            default -> false;
        };
    }

    private static boolean hasNonEmptyArray(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }
}
