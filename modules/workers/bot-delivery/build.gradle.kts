plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.botdelivery.BotDeliveryWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(libs.h2)
}
