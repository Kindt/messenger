plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
}

group = "com.avandocmsg.messenger.mobile"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("io.ktor:ktor-client-core:2.3.12")
            api("io.ktor:ktor-client-content-negotiation:2.3.12")
            api("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.12")
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            implementation(project(":modules:media-sfu"))
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:2.3.12")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:2.3.12")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}

android {
    namespace = "com.avandocmsg.messenger.mobile.sdk"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
