package com.avandocmsg.messenger.desktop.sdk.branding;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.demo.DemoDataStore;
import com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.session.DesktopSession;
import java.util.Optional;

public final class BrandingService {

    private final ServerBrandingCache cache;

    public BrandingService(ServerBrandingCache cache) {
        this.cache = cache;
    }

    public BrandingSnapshot fetchAndCache(
        DesktopSession session,
        ServerEntry server,
        String username,
        KorusApiClient api,
        String token
    ) throws Exception {
        BrandingSnapshot snap;
        if (session.isDemo()) {
            snap = demoBranding(server.serverId());
        } else if (token != null && !token.isBlank()) {
            snap = api.brandingMe(token);
        } else {
            snap = api.brandingPublic();
        }
        cache.put(server.serverId(), snap);
        return snap;
    }

    public Optional<BrandingSnapshot> cached(String serverId) throws Exception {
        return cache.get(serverId);
    }

    private static BrandingSnapshot demoBranding(String serverId) {
        if (DemoDataStore.SERVER_B.equals(serverId)) {
            return new BrandingSnapshot(
                null, "sberbank", java.util.Map.of(), null, "Demo Partner", true,
                "default", "centered", "default", 1L, null
            );
        }
        return new BrandingSnapshot(
            null, "vtb", java.util.Map.of(), null, "Demo Corp", true,
            "default", "centered", "default", 1L, null
        );
    }
}
