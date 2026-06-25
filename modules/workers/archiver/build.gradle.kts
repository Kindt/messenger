plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.archiver.ArchiverWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.postgresql)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
}
