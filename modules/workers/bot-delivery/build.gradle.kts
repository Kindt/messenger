plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.botdelivery.BotDeliveryWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.17.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation("com.h2database:h2:2.2.224")
}
