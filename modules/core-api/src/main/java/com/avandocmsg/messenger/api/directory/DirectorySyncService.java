package com.avandocmsg.messenger.api.directory;

import com.avandocmsg.messenger.api.auth.policy.AuthPolicyRepository;
import com.avandocmsg.messenger.api.auth.policy.AuthProviderEntry;
import com.avandocmsg.messenger.core.port.OrganizationLookupPort;
import com.avandocmsg.messenger.core.port.DirectorySyncRunRepositoryPort;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DirectorySyncService {
    private static final Logger log = LoggerFactory.getLogger(DirectorySyncService.class);

    private final AuthPolicyRepository authPolicyRepository;
    private final OrganizationLookupPort organizationLookupPort;
    private final DirectorySyncRunRepositoryPort runRepository;
    private final OrgUserDirectoryPort userDirectory;
    private final LdapDirectoryClient ldapClient;
    private final UuidGenerator uuidGenerator;

    public DirectorySyncService(
        AuthPolicyRepository authPolicyRepository,
        OrganizationLookupPort organizationLookupPort,
        DirectorySyncRunRepositoryPort runRepository,
        OrgUserDirectoryPort userDirectory,
        LdapDirectoryClient ldapClient,
        UuidGenerator uuidGenerator
    ) {
        this.authPolicyRepository = authPolicyRepository;
        this.organizationLookupPort = organizationLookupPort;
        this.runRepository = runRepository;
        this.userDirectory = userDirectory;
        this.ldapClient = ldapClient;
        this.uuidGenerator = uuidGenerator;
    }

    public Optional<DirectorySyncRunRow> latestStatus(UUID orgId) {
        if (!organizationLookupPort.exists(orgId)) {
            return Optional.empty();
        }
        return runRepository.findLatestByOrg(orgId).map(DirectorySyncService::toApiRow);
    }

    public boolean orgExists(UUID orgId) {
        return organizationLookupPort.exists(orgId);
    }

    public Optional<DirectorySyncRunRow> syncFromLdap(UUID orgId) {
        if (!organizationLookupPort.exists(orgId)) {
            return Optional.empty();
        }
        var provider = findLdapProvider(orgId);
        if (provider.isEmpty()) {
            var run = runRepository.startRun(orgId);
            runRepository.finishRun(run.id(), "error", 0, "no_enabled_ldap_provider");
            return runRepository.findLatestByOrg(orgId).map(DirectorySyncService::toApiRow);
        }
        var run = runRepository.startRun(orgId);
        try {
            var settings = resolvedSettings(provider.get());
            var entries = ldapClient.searchUsers(settings);
            var upserted = 0;
            for (var entry : entries) {
                var id = uuidGenerator.randomUuid();
                if (userDirectory.upsertFromDirectory(
                    id, orgId, entry.externalId(), entry.username(), entry.email(), entry.displayName())) {
                    upserted++;
                }
            }
            runRepository.finishRun(run.id(), "ok", upserted, null);
        } catch (Exception e) {
            log.warn("directory sync failed orgId={}: {}", orgId, e.getMessage());
            runRepository.finishRun(run.id(), "error", 0, e.getMessage());
        }
        return runRepository.findLatestByOrg(orgId).map(DirectorySyncService::toApiRow);
    }

    public void syncAllOrgsWithLdap() {
        for (var org : organizationLookupPort.listAll()) {
            var orgId = UUID.fromString(org.id());
            if (findLdapProvider(orgId).isPresent()) {
                syncFromLdap(orgId);
            }
        }
    }

    Optional<AuthProviderEntry> findLdapProvider(UUID orgId) {
        var row = authPolicyRepository.findByOrgId(orgId).orElseGet(() -> authPolicyRepository.defaultPolicy(orgId));
        return row.providers().stream()
            .filter(p -> p.enabled() && "ldap".equalsIgnoreCase(p.type()))
            .findFirst();
    }

    private static DirectorySyncRunRow toApiRow(DirectorySyncRunRepositoryPort.DirectorySyncRunRow row) {
        return new DirectorySyncRunRow(
            row.id(),
            row.orgId(),
            row.status(),
            row.usersUpserted(),
            row.error(),
            row.startedAt(),
            row.finishedAt());
    }

    private Map<String, String> resolvedSettings(AuthProviderEntry provider) {
        var out = new HashMap<String, String>();
        if (provider.settings() != null) {
            provider.settings().forEach((k, v) -> {
                if (v != null) {
                    out.put(k, v);
                }
            });
        }
        if (provider.secretRef() != null && !provider.secretRef().isBlank()) {
            var env = System.getenv(provider.secretRef());
            if (env != null && !env.isBlank()) {
                out.putIfAbsent("bind_password", env);
            }
        }
        return out;
    }
}
