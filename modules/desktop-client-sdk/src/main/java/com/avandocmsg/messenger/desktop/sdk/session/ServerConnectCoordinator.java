package com.avandocmsg.messenger.desktop.sdk.session;

import com.avandocmsg.messenger.desktop.sdk.branding.BrandingService;
import com.avandocmsg.messenger.desktop.sdk.capabilities.ServerCapabilitiesCache;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnConnectionState;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnOrchestrator;
import com.avandocmsg.messenger.desktop.sdk.ws.MultiServerWsHub;

/** Login sequence: optional VPN (before_api) → auth → branding + capabilities cache + WS. */
public final class ServerConnectCoordinator {

    private final MultiServerSessionManager sessions;
    private final VpnOrchestrator vpn;
    private final BrandingService branding;
    private final ServerCapabilitiesCache capabilitiesCache;
    private final MultiServerWsHub wsHub;

    public ServerConnectCoordinator(
        MultiServerSessionManager sessions,
        VpnOrchestrator vpn,
        BrandingService branding
    ) {
        this(sessions, vpn, branding, null, null);
    }

    public ServerConnectCoordinator(
        MultiServerSessionManager sessions,
        VpnOrchestrator vpn,
        BrandingService branding,
        ServerCapabilitiesCache capabilitiesCache,
        MultiServerWsHub wsHub
    ) {
        this.sessions = sessions;
        this.vpn = vpn;
        this.branding = branding;
        this.capabilitiesCache = capabilitiesCache;
        this.wsHub = wsHub;
    }

    public ConnectResult connect(
        ServerEntry entry,
        String username,
        String password,
        String vpnTotpCode,
        boolean demoSession
    ) throws Exception {
        var serverId = new ServerId(entry.serverId());
        VpnConnectionState vpnState = VpnConnectionState.disconnected();
        if (!demoSession && vpn != null) {
            vpnState = vpn.ensureBeforeApi(serverId.value(), vpnTotpCode);
        }
        sessions.registerServer(entry);
        var token = sessions.login(serverId, username, password);
        BrandingSnapshot brandingSnap = null;
        if (!demoSession) {
            var api = sessions.clientFor(entry);
            if (branding != null) {
                brandingSnap = branding.fetchAndCache(
                    new ApiDesktopSession(sessions),
                    entry,
                    username,
                    api,
                    token
                );
            }
            afterAuth(serverId, entry, token, api);
        }
        return new ConnectResult(token, vpnState, brandingSnap);
    }

    /** Re-attach WS + refresh capabilities for saved token (profile reopen). */
    public void resumeConnected(ServerEntry entry, String username) throws Exception {
        var serverId = new ServerId(entry.serverId());
        var token = sessions.token(serverId, username);
        if (token == null || token.isBlank()) {
            return;
        }
        var api = sessions.clientFor(entry);
        afterAuth(serverId, entry, token, api);
    }

    private void afterAuth(
        ServerId serverId,
        ServerEntry entry,
        String token,
        com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient api
    ) throws Exception {
        if (capabilitiesCache != null) {
            capabilitiesCache.put(serverId, api.capabilities(token));
        }
        if (wsHub != null) {
            wsHub.connect(serverId, entry, token);
        }
    }

    public record ConnectResult(String token, VpnConnectionState vpnState, BrandingSnapshot branding) {}
}
