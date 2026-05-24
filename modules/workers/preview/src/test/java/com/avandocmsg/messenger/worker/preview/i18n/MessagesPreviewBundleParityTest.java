package com.avandocmsg.messenger.worker.preview.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesPreviewBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_preview";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesPreviewBundleParityTest.class.getClassLoader(), BASE);
    }
}
