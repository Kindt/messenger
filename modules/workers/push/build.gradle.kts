plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.push.PushWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.web.push) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)

    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
}
