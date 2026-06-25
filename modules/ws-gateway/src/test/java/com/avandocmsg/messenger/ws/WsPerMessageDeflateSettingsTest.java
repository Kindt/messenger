package com.avandocmsg.messenger.ws;

import jakarta.websocket.Extension;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsPerMessageDeflateSettingsTest {

    @Test
    void defaultEnabledWhenUnset() {
        assertTrue(WsPerMessageDeflateSettings.parseEnabled(null, true));
        assertTrue(WsPerMessageDeflateSettings.parseEnabled("  ", true));
    }

    @Test
    void parsesExplicitBoolean() {
        assertTrue(WsPerMessageDeflateSettings.parseEnabled("true", false));
        assertFalse(WsPerMessageDeflateSettings.parseEnabled("false", true));
    }

    @Test
    void configuratorStripsDeflateWhenDisabled() {
        MessagingWebSocket.configureStaticContext(null, null, null, null, null, null, List.of("*"), false);

        var configurator = new MessagingWebSocket.OriginHandshakeConfigurator();
        var installed = List.<Extension>of(new StubExtension("permessage-deflate"));
        var requested = List.<Extension>of(new StubExtension("permessage-deflate"));

        assertTrue(configurator.getNegotiatedExtensions(installed, requested).isEmpty());
    }

    @Test
    void configuratorKeepsDeflateWhenEnabled() {
        MessagingWebSocket.configureStaticContext(null, null, null, null, null, null, List.of("*"), true);

        var configurator = new MessagingWebSocket.OriginHandshakeConfigurator();
        var installed = List.<Extension>of(new StubExtension("permessage-deflate"));
        var requested = List.<Extension>of(new StubExtension("permessage-deflate"));

        assertEquals(1, configurator.getNegotiatedExtensions(installed, requested).size());
    }

    private static final class StubExtension implements Extension {
        private final String name;

        private StubExtension(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Extension.Parameter> getParameters() {
            return List.of();
        }
    }
}
