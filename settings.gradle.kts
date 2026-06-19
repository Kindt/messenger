rootProject.name = "avandocmsg-messenger"

include(
    "services:indexer",
    "modules:common",
    "modules:core-domain",
    "modules:core-port",
    "modules:core-api",
    "modules:web-client",
    "modules:ws-gateway",
    "modules:workers:message-pipeline",
    "modules:workers:archiver",
    "modules:workers:deep-archiver",
    "modules:workers:indexer",
    "modules:workers:preview",
    "modules:workers:push",
    "modules:workers:bot-delivery",
    "modules:workers:connector-runtime",
    "modules:workers:exchange-bridge",
    "modules:workers:storage-bridge",
    "modules:workers:onec-bridge",
    "modules:workers:export-replay",
    "modules:workers:retention"
)
