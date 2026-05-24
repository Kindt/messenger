plugins {
    id("java")
    id("java-library")
}

group = "com.avandocmsg"
version = "0.1.0-SNAPSHOT"

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
            "-Dnet.bytebuddy.experimental=true"
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

/** Runs `build` (compile, test, assemble) on every subproject — CI smoke / integrity gate. */
tasks.register("buildIntegrity") {
    group = "verification"
    description = "Compile, run all unit tests, and assemble every module"
    dependsOn("checkBundleParity")
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
