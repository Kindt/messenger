package com.avandocmsg.messenger.worker.archiver.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesArchiverBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_archiver";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesArchiverBundleParityTest.class.getClassLoader(), BASE);
    }
}
