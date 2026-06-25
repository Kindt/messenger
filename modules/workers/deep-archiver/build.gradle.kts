plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.deeparchive.DeepArchiverWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.minio) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
}
