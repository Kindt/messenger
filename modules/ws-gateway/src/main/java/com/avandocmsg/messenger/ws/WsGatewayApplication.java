package com.avandocmsg.messenger.ws;

import com.avandocmsg.messenger.ws.bootstrap.EmbeddedWsTomcatBootstrap;
import com.avandocmsg.messenger.ws.bootstrap.WsGatewayComposition;

public final class WsGatewayApplication {

    private WsGatewayApplication() {
    }

    public static void main(String[] args) throws Exception {
        var port = Integer.parseInt(System.getenv().getOrDefault("WS_PORT", "8081"));
        var composition = WsGatewayComposition.start();
        composition.registerShutdownHook();
        EmbeddedWsTomcatBootstrap.startAndAwait(port);
    }
}
