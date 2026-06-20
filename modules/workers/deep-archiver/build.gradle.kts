plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.deeparchive.DeepArchiverWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.17.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    implementation("io.minio:minio:8.5.17")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")

    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("io.prometheus:simpleclient_common:0.16.0")
}
