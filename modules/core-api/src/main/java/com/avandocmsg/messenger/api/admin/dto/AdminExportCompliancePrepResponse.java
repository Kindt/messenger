package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Результат export-compliance-prep")
public record AdminExportCompliancePrepResponse(
    @Schema(example = "11111111-1111-1111-1111-111111111111")
    @JsonProperty("chat_id") String chatId,
    @Schema(example = "[\"msg-1\",\"msg-2\",\"msg-3\"]")
    @JsonProperty("message_ids") List<String> messageIds,
    @Schema(example = "true")
    @JsonProperty("retention_patched") boolean retentionPatched,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(example = "22222222-2222-2222-2222-222222222222")
    @JsonProperty("file_id") String fileId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(example = "msg-file-1")
    @JsonProperty("file_message_id") String fileMessageId
) {}
