package com.avandocmsg.messenger.common.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PluginMessage(String text, String format) {
    public static PluginMessage markdown(String text) {
        return new PluginMessage(text, "markdown");
    }
}
