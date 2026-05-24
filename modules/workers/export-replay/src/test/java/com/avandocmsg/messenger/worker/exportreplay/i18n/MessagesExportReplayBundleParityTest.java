package com.avandocmsg.messenger.worker.exportreplay.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesExportReplayBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_export_replay";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesExportReplayBundleParityTest.class.getClassLoader(), BASE);
    }
}
