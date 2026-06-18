package com.avandocmsg.messenger.api.security;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

/** Lab app-layer IP allowlist enforcement (spec 022 US32). */
public class OrgIpAllowlistService {
    private final OrgIpAllowlistRepository repository;

    public OrgIpAllowlistService(OrgIpAllowlistRepository repository) {
        this.repository = repository;
    }

    public Optional<OrgIpAllowlistRepository.Row> get(UUID orgId) {
        return repository.findByOrgId(orgId);
    }

    public OrgIpAllowlistRepository.Row update(UUID orgId, boolean enabled, String allowedCidrs) {
        return repository.upsert(orgId, enabled, normalizeCidrs(allowedCidrs));
    }

    public boolean isAllowed(UUID orgId, String clientIp) {
        if (orgId == null || clientIp == null || clientIp.isBlank()) {
            return true;
        }
        var row = repository.findByOrgId(orgId).orElse(null);
        if (row == null || !row.enabled()) {
            return true;
        }
        var cidrs = row.allowedCidrs();
        if (cidrs == null || cidrs.isBlank()) {
            return false;
        }
        for (var part : cidrs.split("[,\\n;]")) {
            var rule = part.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (matchesRule(clientIp.trim(), rule)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCidrs(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private static boolean matchesRule(String clientIp, String rule) {
        if (clientIp.equals(rule)) {
            return true;
        }
        if (!rule.contains("/")) {
            return false;
        }
        var slash = rule.indexOf('/');
        var network = rule.substring(0, slash).trim();
        var prefixLen = Integer.parseInt(rule.substring(slash + 1).trim());
        try {
            var client = InetAddress.getByName(clientIp);
            var net = InetAddress.getByName(network);
            if (client.getAddress().length != net.getAddress().length) {
                return false;
            }
            return prefixMatch(client.getAddress(), net.getAddress(), prefixLen);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean prefixMatch(byte[] client, byte[] network, int prefixLen) {
        var maxBits = client.length * 8;
        if (prefixLen < 0 || prefixLen > maxBits) {
            return false;
        }
        var fullBytes = prefixLen / 8;
        var remBits = prefixLen % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (client[i] != network[i]) {
                return false;
            }
        }
        if (remBits == 0) {
            return true;
        }
        var mask = (byte) (0xFF << (8 - remBits));
        return (client[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}
