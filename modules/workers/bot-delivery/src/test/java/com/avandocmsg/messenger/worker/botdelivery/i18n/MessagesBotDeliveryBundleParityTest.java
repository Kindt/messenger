package com.avandocmsg.messenger.worker.botdelivery.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesBotDeliveryBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_bot_delivery";

    @Test
    void ruAndEnHaveSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesBotDeliveryBundleParityTest.class.getClassLoader(), BASE);
    }
}
