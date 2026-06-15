plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.indexer.IndexerWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.25.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("org.apache.solr:solr-solrj:10.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("io.prometheus:simpleclient_common:0.16.0")
}
