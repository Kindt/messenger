package com.avandocmsg.messenger.core.port;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReadCacheKeysTest {

    @Test
    void keys_useStablePrefix() {
        var id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertEquals("korus:rc:chat:list:" + id, ReadCacheKeys.chatList(id));
        assertEquals("korus:rc:chat:unread:" + id, ReadCacheKeys.chatUnread(id));
        assertEquals("korus:rc:user:profile:" + id, ReadCacheKeys.userProfile(id));
        assertEquals("korus:rc:user:presence:" + id, ReadCacheKeys.userPresence(id));
    }
}
