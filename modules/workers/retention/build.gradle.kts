plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.retention.RetentionWorker")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version.toString())
    }
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.minio) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.postgresql)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
}
