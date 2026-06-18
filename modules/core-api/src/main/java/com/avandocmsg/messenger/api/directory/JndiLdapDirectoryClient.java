package com.avandocmsg.messenger.api.directory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class JndiLdapDirectoryClient implements LdapDirectoryClient {
    private static final Logger log = LoggerFactory.getLogger(JndiLdapDirectoryClient.class);

    @Override
    public List<LdapUserEntry> searchUsers(Map<String, String> settings) throws Exception {
        var vendor = settings.getOrDefault("vendor", "ad");
        var usernameAttr = "ad".equalsIgnoreCase(vendor) ? "sAMAccountName" : "uid";
        var uuidAttr = "ad".equalsIgnoreCase(vendor) ? "objectGUID" : "entryUUID";
        var connectionUrl = required(settings, "connection_url");
        var usersDn = required(settings, "users_dn");
        var bindDn = required(settings, "bind_dn");
        var bindPassword = required(settings, "bind_password");
        var filter = settings.getOrDefault("user_ldap_filter",
            "ad".equalsIgnoreCase(vendor) ? "(&(objectClass=user)(!(objectClass=computer)))" : "(objectClass=inetOrgPerson)");

        var env = new Hashtable<String, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, connectionUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDn);
        env.put(Context.SECURITY_CREDENTIALS, bindPassword);

        var controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[] {usernameAttr, uuidAttr, "mail", "cn", "displayName"});

        var out = new ArrayList<LdapUserEntry>();
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            NamingEnumeration<SearchResult> results = ctx.search(usersDn, filter, controls);
            while (results.hasMore()) {
                var sr = results.next();
                var attrs = sr.getAttributes();
                var username = attrString(attrs, usernameAttr);
                if (username == null || username.isBlank()) {
                    continue;
                }
                var externalId = externalId(attrs, uuidAttr);
                if (externalId == null || externalId.isBlank()) {
                    externalId = sr.getNameInNamespace();
                }
                var email = attrString(attrs, "mail");
                var displayName = firstNonBlank(
                    attrString(attrs, "displayName"),
                    attrString(attrs, "cn"),
                    username);
                out.add(new LdapUserEntry(externalId, username, email, displayName));
            }
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {
                    log.debug("LDAP context close: {}", e.getMessage());
                }
            }
        }
        return List.copyOf(out);
    }

    private static String externalId(javax.naming.directory.Attributes attrs, String uuidAttr) throws Exception {
        var attr = attrs.get(uuidAttr);
        if (attr == null) {
            return null;
        }
        var value = attr.get();
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value != null ? value.toString() : null;
    }

    private static String attrString(javax.naming.directory.Attributes attrs, String name) throws Exception {
        Attribute attr = attrs.get(name);
        if (attr == null) {
            return null;
        }
        var value = attr.get();
        return value != null ? value.toString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (var v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String required(Map<String, String> settings, String key) {
        var v = settings.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("missing_setting:" + key);
        }
        return v;
    }
}
