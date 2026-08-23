package com.avandocmsg.messenger.desktop.sdk.json;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {
    private JsonSupport() {}

    public static ObjectMapper mapper() {
        return MessengerJson.mapper();
    }
}
