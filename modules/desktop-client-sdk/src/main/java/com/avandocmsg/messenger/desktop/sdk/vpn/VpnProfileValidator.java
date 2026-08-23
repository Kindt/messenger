package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.util.ArrayList;
import java.util.List;

public final class VpnProfileValidator {

    private VpnProfileValidator() {}

    public static List<String> validate(VpnProfile profile) {
        var errors = new ArrayList<String>();
        if (profile.displayName() == null || profile.displayName().isBlank()) {
            errors.add("display_name required");
        }
        var protocol = profile.protocolEnum();
        if (protocol == VpnProtocol.DISABLED) {
            return errors;
        }
        if (protocol == VpnProtocol.WIREGUARD) {
            if (profile.wireguardConfig() == null || profile.wireguardConfig().isBlank()) {
                errors.add("wireguard_config required");
            }
        } else if (protocol == VpnProtocol.OPENVPN) {
            if (profile.openvpnInlineConfig() == null || profile.openvpnInlineConfig().isBlank()) {
                if (profile.serverHost() == null || profile.serverHost().isBlank()) {
                    errors.add("server_host or openvpn_inline_config required");
                }
            }
        } else if (protocol != VpnProtocol.CUSTOM_CLI) {
            if (profile.serverHost() == null || profile.serverHost().isBlank()) {
                errors.add("server_host required");
            }
        }
        if (protocol == VpnProtocol.CUSTOM_CLI
            && (profile.customCliTemplate() == null || profile.customCliTemplate().isBlank())) {
            errors.add("custom_cli_template required");
        }
        if (profile.authEnum().requiresTotp() && !profile.totpEnabled()) {
            errors.add("totp_enabled required for auth method " + profile.authMethod());
        }
        return errors;
    }
}
