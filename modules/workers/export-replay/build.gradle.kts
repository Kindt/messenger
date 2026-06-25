plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.exportreplay.ExportReplayWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.postgresql)
    implementation(libs.bundles.logging)
    implementation(libs.minio) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }
    implementation(libs.solr.solrj) {
        exclude(group = "io.swagger.core.v3", module = "swagger-annotations-jakarta")
        exclude(group = "jakarta.ws.rs", module = "jakarta.ws.rs-api")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "com.fasterxml.jackson.datatype")
        exclude(group = "com.fasterxml.jackson.module")
        exclude(group = "com.fasterxml.jackson.jakarta.rs")
    }
    implementation(libs.bundles.prometheus.server)

    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
}
