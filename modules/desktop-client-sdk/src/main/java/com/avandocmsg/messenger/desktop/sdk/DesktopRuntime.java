package com.avandocmsg.messenger.desktop.sdk;



import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;

import com.avandocmsg.messenger.desktop.sdk.attachments.AttachmentPathResolver;

import com.avandocmsg.messenger.desktop.sdk.branding.BrandingService;

import com.avandocmsg.messenger.desktop.sdk.branding.ServerBrandingCache;

import com.avandocmsg.messenger.desktop.sdk.capabilities.ServerCapabilitiesCache;

import com.avandocmsg.messenger.desktop.sdk.files.FileTransferService;

import com.avandocmsg.messenger.desktop.sdk.model.LocalProfile;

import com.avandocmsg.messenger.desktop.sdk.secure.PlatformSecureTokenStore;

import com.avandocmsg.messenger.desktop.sdk.secure.SecureTokenStore;

import com.avandocmsg.messenger.desktop.sdk.session.MultiServerSessionManager;

import com.avandocmsg.messenger.desktop.sdk.session.ServerConnectCoordinator;

import com.avandocmsg.messenger.desktop.sdk.storage.ProfileStore;

import com.avandocmsg.messenger.desktop.sdk.storage.ServerRegistry;

import com.avandocmsg.messenger.desktop.sdk.vpn.VpnOrchestrator;

import com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfileStore;

import com.avandocmsg.messenger.desktop.sdk.vpn.VpnProviderRegistry;

import com.avandocmsg.messenger.desktop.sdk.ws.MultiServerWsHub;

import java.io.IOException;

import java.nio.file.Files;



public final class DesktopRuntime {



    private final ProfileStore profileStore;

    private LocalProfile activeProfile;

    private ServerRegistry serverRegistry;

    private SecureTokenStore tokenStore;

    private MultiServerSessionManager sessions;

    private VpnProfileStore vpnStore;

    private VpnOrchestrator vpnOrchestrator;

    private BrandingService brandingService;

    private ServerBrandingCache brandingCache;

    private ServerCapabilitiesCache capabilitiesCache;

    private MultiServerWsHub wsHub;

    private FileTransferService fileTransferService;



    public DesktopRuntime() {

        this.profileStore = new ProfileStore(DesktopPaths.appRoot());

    }



    public ProfileStore profileStore() {

        return profileStore;

    }



    public LocalProfile activeProfile() {

        return activeProfile;

    }



    public ServerRegistry serverRegistry() {

        return serverRegistry;

    }



    public SecureTokenStore tokenStore() {

        return tokenStore;

    }



    public MultiServerSessionManager sessions() {

        return sessions;

    }



    public VpnProfileStore vpnStore() {

        return vpnStore;

    }



    public VpnOrchestrator vpnOrchestrator() {

        return vpnOrchestrator;

    }



    public BrandingService brandingService() {

        return brandingService;

    }



    public ServerBrandingCache brandingCache() {

        return brandingCache;

    }



    public ServerCapabilitiesCache capabilitiesCache() {

        return capabilitiesCache;

    }



    public MultiServerWsHub wsHub() {

        return wsHub;

    }



    public FileTransferService fileTransferService() {

        return fileTransferService;

    }



    public void activateProfile(LocalProfile profile) throws IOException {

        if (activeProfile != null && !activeProfile.profileId().equals(profile.profileId())) {

            wipeActiveMemory();

        }

        this.activeProfile = profile;

        profileStore.touchProfile(profile.profileId());

        var root = profileStore.profileRoot(profile.profileId());

        Files.createDirectories(profileStore.stateDir(profile.profileId()));

        this.serverRegistry = new ServerRegistry(root);

        this.tokenStore = PlatformSecureTokenStore.create(profileStore.stateDir(profile.profileId()));

        this.sessions = new MultiServerSessionManager(
            serverRegistry,
            tokenStore,
            null,
            new com.avandocmsg.messenger.desktop.sdk.security.SecuritySettingsStore(profileStore, profile.profileId()).read()
        );

        var stateDir = profileStore.stateDir(profile.profileId());

        this.vpnStore = new VpnProfileStore(stateDir, tokenStore);

        this.vpnOrchestrator = new VpnOrchestrator(vpnStore, new VpnProviderRegistry());

        this.brandingCache = new ServerBrandingCache(stateDir);

        this.brandingService = new BrandingService(brandingCache);

        this.capabilitiesCache = new ServerCapabilitiesCache(stateDir);

        this.wsHub = new MultiServerWsHub(KorusApiClient.defaultHttpClient());

        var resolver = new AttachmentPathResolver(

            DesktopPaths.downloadsRoot(),

            profile.displayName()

        );

        this.fileTransferService = new FileTransferService(resolver);

    }



    public ServerConnectCoordinator connectCoordinator() {

        return new ServerConnectCoordinator(

            sessions,

            vpnOrchestrator,

            brandingService,

            capabilitiesCache,

            wsHub

        );

    }



    public void wipeActiveMemory() {

        if (wsHub != null) {

            wsHub.disconnectAll();

        }

        if (sessions != null) {

            sessions.clearTokens();

        }

        if (tokenStore != null) {

            tokenStore.clear();

        }

        sessions = null;

        serverRegistry = null;

        tokenStore = null;

        vpnStore = null;

        vpnOrchestrator = null;

        brandingService = null;

        brandingCache = null;

        capabilitiesCache = null;

        wsHub = null;

        fileTransferService = null;

    }

}
