package com.avandocmsg.messenger.api.directory;

import javax.naming.NamingException;
import java.util.List;
import java.util.Map;

/** LDAP search for directory sync (JNDI in production, mock in tests). */
public interface LdapDirectoryClient {

    List<LdapUserEntry> searchUsers(Map<String, String> settings) throws NamingException;
}
