package com.avandocmsg.messenger.worker.onecbridge.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesOneCBridgeBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_onec_bridge";

    @Test
    void ruEnSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesOneCBridgeBundleParityTest.class.getClassLoader(), BASE);
    }
}
