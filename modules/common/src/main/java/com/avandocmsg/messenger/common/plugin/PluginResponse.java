package com.avandocmsg.messenger.common.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PluginResponse(
    List<PluginMessage> messages,
    List<PluginCard> cards,
    PluginDefer defer
) {
    public static PluginResponse text(String markdown) {
        return new PluginResponse(List.of(PluginMessage.markdown(markdown)), null, null);
    }
}
