package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ExternalStackComponentProfileSummaryCatalog(
    @JsonProperty("components") Map<String, ExternalStackComponentProfileSummary> components
) {}
