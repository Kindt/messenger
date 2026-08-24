plugins {
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "23.0.2"
    modules("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
}

dependencies {
    implementation(project(":modules:desktop-client-sdk"))
    implementation(project(":modules:media-sfu"))
    implementation(libs.bundles.logging)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testfx.junit5)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "com.avandocmsg.messenger.desktop.DesktopApplication"
    applicationDefaultJvmArgs = listOf("-Dkorus.desktop.demo=false")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("desktop-ui")
    }
}

tasks.register<Test>("visualCapture") {
    description = "Desktop screenshot capture (headless, no mouse)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("desktop-visual-capture")
    }
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs(
        "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "-Dtestfx.robot=glass",
        "-Dtestfx.headless=false",
        "-Dtestfx.setup.timeout=15000",
        "-Dkorus.desktop.demo=true",
    )
    systemProperty("java.awt.headless", "false")
}

tasks.register<Test>("uiTest") {
    description = "JavaFX TestFX click-through tests (headed, demo mode)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("desktop-ui")
        excludeTags("desktop-visual-capture")
    }
    maxParallelForks = 1
    forkEvery = 1
    retry {
        maxRetries.set(0)
    }
    jvmArgs(
        "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "-Dtestfx.robot=glass",
        "-Dtestfx.headless=false",
        "-Dtestfx.setup.timeout=15000",
        "-Dkorus.desktop.demo=true",
        "-Djunit.jupiter.execution.parallel.enabled=false",
    )
    systemProperty("java.awt.headless", "false")
    reports {
        junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/uiTest"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/tests/uiTest"))
    }
}

tasks.register("jpackage") {
    group = "distribution"
    description = "Stage desktop install layout under build/desktop-dist (offline; full jpackage when JDK jpackage available)"
    dependsOn("installDist")
    doLast {
        val dist = layout.buildDirectory.dir("desktop-dist").get().asFile
        dist.mkdirs()
        val install = layout.buildDirectory.dir("install/desktop-client").get().asFile
        val marker = dist.resolve("README.txt")
        marker.writeText(
            """
            Korus Messenger Desktop distribution staging
            Platform payload: copy from ${install.absolutePath}
            Full native installer: run jpackage manually when corporate feed is available.
            """.trimIndent()
        )
        logger.lifecycle("Staged desktop-dist at {}", dist.absolutePath)
    }
}
