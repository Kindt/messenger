dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:media-sfu"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("live-server")
    }
}

tasks.register<Test>("liveServerTest") {
    description = "Integration tests against live QEMU API (:18080)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("live-server")
    }
}
