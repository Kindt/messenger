// Minimal settings for docker/Dockerfile.core-api.war (Gradle 9 requires existing project dirs).
rootProject.name = "avandocmsg-messenger"

include(
    "modules:common",
    "modules:core-domain",
    "modules:core-port",
    "modules:core-api",
)
