package com.avandocmsg.messenger.worker.deeparchive.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesDeepArchiverBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_deep_archiver";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesDeepArchiverBundleParityTest.class.getClassLoader(), BASE);
    }
}
