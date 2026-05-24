package com.avandocmsg.messenger.worker.push.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesPushBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_push";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesPushBundleParityTest.class.getClassLoader(), BASE);
    }
}
