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
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
        testImplementation("org.mockito:mockito-core:5.14.0") {
            exclude("net.bytebuddy", "byte-buddy")
            exclude("net.bytebuddy", "byte-buddy-agent")
        }
        testImplementation("org.mockito:mockito-junit-jupiter:5.14.0")
        testImplementation("net.bytebuddy:byte-buddy:1.16.0")
        testImplementation("net.bytebuddy:byte-buddy-agent:1.16.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs(
            "-Djdk.attach.allowAttachSelf=true",
            // Mockito / ByteBuddy on JDK 25 (inline mocks)
            "-Dnet.bytebuddy.experimental=true"
        )
    }
}

/** Runs `build` (compile, test, assemble) on every subproject — CI smoke / integrity gate. */
tasks.register("buildIntegrity") {
    group = "verification"
    description = "Compile, run all unit tests, and assemble every module"
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.named("build"))
    }
}
