plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.indexer.IndexerWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.solr.solrj) {
        exclude(group = "io.swagger.core.v3", module = "swagger-annotations-jakarta")
        exclude(group = "jakarta.ws.rs", module = "jakarta.ws.rs-api")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "com.fasterxml.jackson.datatype")
        exclude(group = "com.fasterxml.jackson.module")
        exclude(group = "com.fasterxml.jackson.jakarta.rs")
    }
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
}
