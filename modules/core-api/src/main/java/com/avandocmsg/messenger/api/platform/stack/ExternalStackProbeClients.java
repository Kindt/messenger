package com.avandocmsg.messenger.api.platform.stack;

import java.util.function.BooleanSupplier;

public record ExternalStackProbeClients(
    BooleanSupplier redisPing,
    BooleanSupplier redisCommandSubset,
    BooleanSupplier s3BucketExists,
    BooleanSupplier s3SampleOperation,
    BooleanSupplier natsConnected,
    BooleanSupplier natsSubjectProbe,
    BooleanSupplier oidcJwksReachable,
    BooleanSupplier webEdgeSecurityHeaders
) {
}
