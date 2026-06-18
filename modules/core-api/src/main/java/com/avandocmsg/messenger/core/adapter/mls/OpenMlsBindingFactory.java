package com.avandocmsg.messenger.core.adapter.mls;

import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;


/** Composition-root factory for {@link OpenMlsBindingPort} (spec 020). */
public final class OpenMlsBindingFactory {

    private OpenMlsBindingFactory() {
    }

    public static OpenMlsBindingPort create(com.avandocmsg.messenger.api.config.AppConfig appConfig, MlsService mlsService) {
        var hybrid = new HybridOpenMlsBindingAdapter(mlsService);
        if (appConfig != null && appConfig.openmlsNativeEnabled()) {
            return new OpenMlsNativeBindingAdapter(appConfig, hybrid);
        }
        return hybrid;
    }
}
