package com.avandocmsg.messenger.api.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FederationStatusResourceTest {

    @Test
    void status_returnsScaffoldDisabled() {
        var res = new FederationStatusResource().status();
        assertEquals("scaffold", res.mode());
        assertFalse(res.enabled());
        assertEquals(0, res.partnerOrgIds().size());
    }
}
