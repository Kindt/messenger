package com.avandocmsg.messenger.worker.connectorruntime.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesConnectorRuntimeBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_connector_runtime";

    @Test
    void ruEnSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesConnectorRuntimeBundleParityTest.class.getClassLoader(), BASE);
    }
}
