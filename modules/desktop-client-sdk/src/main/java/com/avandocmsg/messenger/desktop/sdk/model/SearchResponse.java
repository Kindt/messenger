package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(List<SearchHit> hits, @JsonProperty("total") Integer total) {
    public SearchResponse() {
        this(List.of(), 0);
    }
}
