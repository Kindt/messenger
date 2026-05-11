package com.avandocmsg.messenger.api.messages.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to add/remove a reaction")
public record ReactionRequest(
    @Schema(description = "Reaction emoji or string", example = "👍") String reaction
) {}
