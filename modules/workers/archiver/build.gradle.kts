plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.archiver.ArchiverWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.25.3")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
