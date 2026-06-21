package com.avandocmsg.messenger.api.platform.stack;

@FunctionalInterface
public interface ExternalStackActiveProbe {

    ExternalStackProbeResult probe(ComponentBackendManifest manifest);
}
