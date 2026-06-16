plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.exchangebridge.ExchangeBridgeWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
