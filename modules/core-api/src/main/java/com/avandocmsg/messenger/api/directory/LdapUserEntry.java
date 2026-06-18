package com.avandocmsg.messenger.api.directory;

/** One LDAP user entry for directory sync. */
public record LdapUserEntry(
    String externalId,
    String username,
    String email,
    String displayName
) {}
