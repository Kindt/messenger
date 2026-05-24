package com.avandocmsg.messenger.worker.pipeline.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesPipelineBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_message_pipeline";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesPipelineBundleParityTest.class.getClassLoader(), BASE);
    }
}
