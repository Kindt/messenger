package com.avandocmsg.messenger.api.devices;

import com.avandocmsg.messenger.api.devices.dto.RegisterDeviceRequest;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceResourceTest {

    @Test
    void register_requiresPushToken() {
        var resource = new DeviceResource(null, I18nTestFixtures.messagesEn());
        var resp = resource.register(new RegisterDeviceRequest("web-client", "web", "  "), null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), resp.getStatus());
    }

    @Test
    void unregister_requiresDeviceName() {
        var resource = new DeviceResource(null, I18nTestFixtures.messagesEn());
        var resp = resource.unregister("  ", null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), resp.getStatus());
    }
}
