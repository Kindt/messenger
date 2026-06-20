// Minimal settings for docker/Dockerfile.ws-gateway.war (Gradle 9 requires existing project dirs).
rootProject.name = "avandocmsg-messenger"

include(
    "modules:common",
    "modules:ws-gateway",
)
