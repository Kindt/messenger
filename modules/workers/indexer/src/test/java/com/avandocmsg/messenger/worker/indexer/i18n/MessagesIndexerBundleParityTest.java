package com.avandocmsg.messenger.worker.indexer.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesIndexerBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_indexer";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesIndexerBundleParityTest.class.getClassLoader(), BASE);
    }
}
