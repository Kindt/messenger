plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.exportreplay.ExportReplayWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.25.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("io.minio:minio:8.5.17")
    implementation("org.apache.solr:solr-solrj:10.0.0")
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("io.prometheus:simpleclient_common:0.16.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.h2database:h2:2.2.224")
}

tasks.test {
    useJUnitPlatform()
}
