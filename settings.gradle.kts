pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        // Maven Central first: server/desktop deps (e.g. slf4j) must not depend on dl.google.com DNS in QEMU/Docker.
        mavenCentral()
        google()
    }
}

rootProject.name = "avandocmsg-messenger"

include(
    "services:indexer",
    "modules:common",
    "modules:core-domain",
    "modules:core-port",
    "modules:media-sfu",
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
    "modules:workers:retention",
    "modules:desktop-client-sdk",
    "modules:desktop-client",
    "mobile:mobile-client-sdk",
    "mobile:mobile-client-android"
)

project(":mobile:mobile-client-sdk").projectDir = file("mobile/mobile-client-sdk")
project(":mobile:mobile-client-android").projectDir = file("mobile/mobile-client-android")
