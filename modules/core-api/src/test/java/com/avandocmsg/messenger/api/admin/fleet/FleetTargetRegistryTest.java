package com.avandocmsg.messenger.api.admin.fleet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetTargetRegistryTest {

    @Test
    void fromJson_parsesEnabledTargets() {
        var json = """
            [
              {"id":"web-1","role":"web-client","base_url":"http://127.0.0.1:8088","health_path":"/health"},
              {"id":"bad","role":"x","base_url":"ftp://evil","enabled":true},
              {"id":"off","role":"x","base_url":"http://127.0.0.1:9","enabled":false}
            ]
            """;
        var reg = FleetTargetRegistry.fromJson(json);
        assertEquals(1, reg.targets().size());
        assertEquals("web-1", reg.targets().get(0).id());
        assertTrue(reg.targets().get(0).isEnabled());
    }

    @Test
    void fromJson_emptyOrInvalid_returnsEmpty() {
        assertTrue(FleetTargetRegistry.fromJson("").targets().isEmpty());
        assertTrue(FleetTargetRegistry.fromJson("not-json").targets().isEmpty());
    }
}
