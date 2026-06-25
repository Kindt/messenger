plugins {
    id("java")
    id("java-library")
    alias(libs.plugins.spotless)
    id("org.gradle.test-retry") version "1.6.5"
}

group = "com.avandocmsg"
version = "0.0.1-SNAPSHOT"

spotless {
    java {
        ratchetFrom("origin/main")
        target("modules/**/src/main/java/**/*.java", "modules/**/src/test/java/**/*.java", "services/**/src/**/*.java")
        toggleOffOn()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("spotlessCheckIncremental") {
    group = "verification"
    description = "Spotless check on configured Java paths (incremental policy; run before merge)"
    dependsOn("spotlessCheck")
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    if (childProjects.isNotEmpty()) {
        return@subprojects
    }

    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "org.gradle.test-retry")

    configurations.all {
        resolutionStrategy {
            eachDependency {
                when {
                    requested.group == "commons-codec" && requested.name == "commons-codec" ->
                        useVersion("1.17.1")
                    requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
                        useVersion("3.20.0")
                    requested.group == "org.checkerframework" && requested.name == "checker-qual" ->
                        useVersion("3.43.0")
                    requested.group == "org.yaml" && requested.name == "snakeyaml" ->
                        useVersion("2.6")
                    requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib") ->
                        useVersion("1.9.10")
                    requested.group == "jakarta.xml.bind" && requested.name == "jakarta.xml.bind-api" ->
                        useVersion("4.0.2")
                    requested.group == "jakarta.validation" && requested.name == "jakarta.validation-api" ->
                        useVersion("3.1.0")
                    requested.group == "com.fasterxml.jackson.dataformat"
                        && requested.name == "jackson-dataformat-toml" ->
                        useVersion("2.21.3")
                }
            }
            failOnVersionConflict()
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    dependencies {
        implementation(enforcedPlatform(rootProject.libs.netty.bom))

        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
        "testImplementation"(rootProject.libs.mockito.core) {
            exclude("net.bytebuddy", "byte-buddy")
            exclude("net.bytebuddy", "byte-buddy-agent")
        }
        "testImplementation"(rootProject.libs.mockito.junit.jupiter) {
            exclude("net.bytebuddy", "byte-buddy")
            exclude("net.bytebuddy", "byte-buddy-agent")
        }
        "testImplementation"(rootProject.libs.byte.buddy)
        "testImplementation"(rootProject.libs.byte.buddy.agent)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        val configuredForks = (findProperty("korus.test.maxParallelForks") as String?)?.toIntOrNull() ?: 0
        maxParallelForks = if (configuredForks > 0) {
            configuredForks
        } else {
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        }
        val retryCount = (findProperty("korus.test.retry.maxRetries") as String?)?.toIntOrNull() ?: 0
        if (retryCount > 0) {
            retry {
                maxRetries.set(retryCount)
                failOnPassedAfterRetry.set(false)
            }
        }
        jvmArgs(
            "-Djdk.attach.allowAttachSelf=true",
            // Mockito / ByteBuddy on JDK 25 (inline mocks)
            "-Dnet.bytebuddy.experimental=true",
            // Suppress dynamic agent warnings on JDK 21+
            "-XX:+EnableDynamicAgentLoading"
        )
    }

    plugins.withId("application") {
        tasks.named("startScripts") {
            notCompatibleWithConfigurationCache("startScripts resolves application runtime classpath")
        }
    }

    tasks.register<Test>("bundleParityTest") {
        group = "verification"
        description = "Bundle key parity (ru/en) for ${project.path}"
        notCompatibleWithConfigurationCache("bundleParityTest wires source set classpath directly")
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter {
            isFailOnNoMatchingTests = false
            includeTestsMatching("*BundleParityTest")
        }
    }
}

/** Python PR gate: Korus Cloud Cell manifests (spec 011). */
tasks.register<Exec>("checkCellManifest") {
    group = "verification"
    description = "Validate Korus Cloud Cell manifests (test_cell_manifest.py)"
    workingDir = rootDir
    val pythonCmd = System.getenv("PYTHON")
        ?: if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3"
    commandLine(pythonCmd, "scripts/test_cell_manifest.py")
}

/** WebUI locale parity + iconBtn/i18n label lint (spec 026 L4). */
tasks.register<Exec>("checkWebuiLabelLint") {
    group = "verification"
    description = "Locale parity + iconBtn tooltip lint for messenger webui"
    val nodeCmd = if (System.getProperty("os.name").lowercase().contains("win")) "node.exe" else "node"
    commandLine(nodeCmd, "scripts/webui-label-lint.js")
    inputs.file(file("scripts/webui-label-lint.js"))
    inputs.file(file("scripts/webui-locale-parity-audit.js"))
    inputs.dir(file("modules/web-client/webui-build/locales/messages"))
    inputs.files(
        file("modules/web-client/src/main/resources/webui/app.js"),
        file("modules/web-client/src/main/resources/webui/ui-icon-buttons.js"),
        file("modules/web-client/src/main/resources/webui/ui-phase5-ext.js"),
    )
}

/** npm audit for webui-build (spec 014 S1-3). */
tasks.register<Exec>("checkNpmAudit") {
    group = "verification"
    description = "npm audit --audit-level=high in modules/web-client/webui-build"
    workingDir = file("modules/web-client/webui-build")
    val npmCmd = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"
    commandLine(npmCmd, "audit", "--audit-level=high")
}

/** Leaf modules only (skip :modules, :modules:workers, :services container projects). */
fun Project.leafSubprojects(): Sequence<Project> =
    subprojects.asSequence().filter { it.childProjects.isEmpty() }

/** Avoid Gradle 9 implicit-deps failure when spotless runs parallel to subproject build. */
tasks.named("spotlessJava") {
    leafSubprojects().forEach { sub ->
        mustRunAfter(sub.tasks.named("build"))
    }
    mustRunAfter(tasks.named("checkBundleParity"))
    mustRunAfter(tasks.named("checkCellManifest"))
    mustRunAfter(tasks.named("checkWebuiLabelLint"))
    mustRunAfter(tasks.named("checkNpmAudit"))
    mustRunAfter(project(":modules:core-api").tasks.named("benchmark"))
}

/** Runs `build` (compile, test, assemble) on every subproject — CI smoke / integrity gate. */
tasks.register("buildIntegrity") {
    group = "verification"
    description = "Compile, run all unit tests, assemble, spotless (ratchet), npm audit, benchmark"
    dependsOn(
        "checkBundleParity",
        "checkCellManifest",
        "checkWebuiLabelLint",
        "checkNpmAudit",
        "spotlessCheck",
        ":modules:core-api:benchmark"
    )
    leafSubprojects().forEach { sub ->
        dependsOn(sub.tasks.named("build"))
    }
}

/** Runs ru/en bundle parity tests in every module that defines them. */
tasks.register("checkBundleParity") {
    group = "verification"
    description = "Run all *BundleParityTest cases across subprojects"
    leafSubprojects().forEach { sub ->
        dependsOn(sub.tasks.matching { it.name == "bundleParityTest" })
    }
}
