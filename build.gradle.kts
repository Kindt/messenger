plugins {
    id("java")
    id("java-library")
    id("com.diffplug.spotless") version "7.0.2"
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
    apply(plugin = "java")
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
        testImplementation("org.mockito:mockito-core:5.23.0") {
            exclude("net.bytebuddy", "byte-buddy")
            exclude("net.bytebuddy", "byte-buddy-agent")
        }
        testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
        testImplementation("net.bytebuddy:byte-buddy:1.18.8")
        testImplementation("net.bytebuddy:byte-buddy-agent:1.18.8")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs(
            "-Djdk.attach.allowAttachSelf=true",
            // Mockito / ByteBuddy on JDK 25 (inline mocks)
            "-Dnet.bytebuddy.experimental=true",
            // Suppress dynamic agent warnings on JDK 21+
            "-XX:+EnableDynamicAgentLoading"
        )
    }

    tasks.register<Test>("bundleParityTest") {
        group = "verification"
        description = "Bundle key parity (ru/en) for ${project.path}"
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

/** npm audit for webui-build (spec 014 S1-3). */
tasks.register<Exec>("checkNpmAudit") {
    group = "verification"
    description = "npm audit --audit-level=high in modules/web-client/webui-build"
    workingDir = file("modules/web-client/webui-build")
    val npmCmd = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"
    commandLine(npmCmd, "audit", "--audit-level=high")
}

/** Avoid Gradle 9 implicit-deps failure when spotless runs parallel to subproject build. */
tasks.named("spotlessJava") {
    subprojects.forEach { sub ->
        mustRunAfter(sub.tasks.named("build"))
    }
    mustRunAfter(tasks.named("checkBundleParity"))
    mustRunAfter(tasks.named("checkCellManifest"))
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
        "checkNpmAudit",
        "spotlessCheck",
        ":modules:core-api:benchmark"
    )
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.named("build"))
    }
}

/** Runs ru/en bundle parity tests in every module that defines them. */
tasks.register("checkBundleParity") {
    group = "verification"
    description = "Run all *BundleParityTest cases across subprojects"
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.matching { it.name == "bundleParityTest" })
    }
}
