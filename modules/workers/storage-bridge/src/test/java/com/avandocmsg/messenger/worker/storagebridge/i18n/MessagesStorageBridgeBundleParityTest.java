package com.avandocmsg.messenger.worker.storagebridge.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesStorageBridgeBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_storage_bridge";

    @Test
    void ruEnSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesStorageBridgeBundleParityTest.class.getClassLoader(), BASE);
    }
}
