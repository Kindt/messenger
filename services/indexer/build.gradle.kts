plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.service.indexer.IndexerServiceApp")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.17.4")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("io.prometheus:simpleclient_common:0.16.0")
}
