plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.exchangebridge.ExchangeBridgeWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
}
