package com.avandocmsg.messenger.worker.retention.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesRetentionBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_retention";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesRetentionBundleParityTest.class.getClassLoader(), BASE);
    }
}
