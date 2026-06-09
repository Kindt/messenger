#!/bin/sh
# Plain-line Docker/Gradle build output for QEMU VGA console (no ANSI spinner UI).
export KORUS_QEMU_CONSOLE="${KORUS_QEMU_CONSOLE:-1}"
export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
export COMPOSE_ANSI="${COMPOSE_ANSI:-never}"
export GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }-Dorg.gradle.console=plain -Dorg.gradle.logging.level=info"
