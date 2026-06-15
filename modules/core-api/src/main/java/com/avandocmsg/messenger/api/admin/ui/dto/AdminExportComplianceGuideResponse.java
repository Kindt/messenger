package com.avandocmsg.messenger.api.admin.ui.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AdminExportComplianceGuideResponse(
    @JsonProperty("gdpr_disclosures_reference") JsonNode gdprDisclosuresReference,
    @JsonProperty("env_checklist") List<EnvChecklistItem> envChecklist,
    @JsonProperty("smoke_commands") List<SmokeCommandHint> smokeCommands,
    @JsonProperty("export_compliance") AdminServerStatsResponse.ExportCompliance exportCompliance,
    @JsonProperty("completeness_policy") CompletenessPolicy completenessPolicy
) {
    public record CompletenessPolicy(
        @JsonProperty("required_fields") List<String> requiredFields,
        @JsonProperty("strict") boolean strict
    ) {}
    public record EnvChecklistItem(
        @JsonProperty("env") String env,
        @JsonProperty("purpose") String purpose,
        @JsonProperty("default_value") String defaultValue
    ) {}

    public record SmokeCommandHint(
        @JsonProperty("title") String title,
        @JsonProperty("command_ps") String commandPs,
        @JsonProperty("command_sh") String commandSh
    ) {}
}
