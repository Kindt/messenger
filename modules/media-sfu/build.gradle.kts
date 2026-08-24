plugins {
    application
}

application {
    mainClass.set("com.avandocmsg.messenger.media.MediaSfuApplication")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.slf4j.api)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.bctls.jdk18on)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
