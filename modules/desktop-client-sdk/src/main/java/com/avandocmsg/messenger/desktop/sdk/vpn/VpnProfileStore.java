package com.avandocmsg.messenger.desktop.sdk.vpn;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.secure.SecureTokenStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VpnProfileStore {

    private final Path path;
    private final SecureTokenStore secrets;

    public VpnProfileStore(Path stateDir, SecureTokenStore secrets) {
        this.path = stateDir.resolve("vpn-profiles.json");
        this.secrets = secrets;
    }

    public VpnProfileDocument load() throws IOException {
        if (!Files.exists(path)) {
            return new VpnProfileDocument();
        }
        return JsonSupport.mapper().readValue(Files.readString(path), VpnProfileDocument.class);
    }

    public void save(VpnProfileDocument doc) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JsonSupport.mapper().writeValueAsString(doc));
    }

    public VpnProfile upsertProfile(VpnProfile profile) throws IOException {
        var doc = load();
        var profiles = new ArrayList<>(doc.profiles());
        profiles.removeIf(p -> p.profileId().equals(profile.profileId()));
        profiles.add(profile);
        save(new VpnProfileDocument(1, List.copyOf(profiles), doc.serverBindings()));
        return profile;
    }

    public void bindServer(ServerVpnBinding binding) throws IOException {
        var doc = load();
        var bindings = new ArrayList<>(doc.serverBindings());
        bindings.removeIf(b -> b.serverId().equals(binding.serverId()));
        bindings.add(binding);
        save(new VpnProfileDocument(1, doc.profiles(), List.copyOf(bindings)));
    }

    public Optional<VpnProfile> profileForServer(String serverId) throws IOException {
        var doc = load();
        return doc.serverBindings().stream()
            .filter(b -> b.serverId().equals(serverId) && b.enabled())
            .findFirst()
            .flatMap(b -> doc.profiles().stream().filter(p -> p.profileId().equals(b.vpnProfileId())).findFirst());
    }

    public Optional<ServerVpnBinding> bindingForServer(String serverId) throws IOException {
        return load().serverBindings().stream().filter(b -> b.serverId().equals(serverId)).findFirst();
    }

    public void storePassword(String profileId, String password) {
        secrets.put("vpn:" + profileId + ":password", password);
    }

    public Optional<String> password(String profileId) {
        return Optional.ofNullable(secrets.get("vpn:" + profileId + ":password"));
    }

    public void storeTotpSecret(String profileId, String secret) {
        secrets.put("vpn:" + profileId + ":totp", secret);
    }

    public Optional<String> totpSecret(String profileId) {
        return Optional.ofNullable(secrets.get("vpn:" + profileId + ":totp"));
    }
}
