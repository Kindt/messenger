plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.pipeline.MessagePipelineWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.17.4")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")

    testImplementation("com.h2database:h2:2.2.224")
}
