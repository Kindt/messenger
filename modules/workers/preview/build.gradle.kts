plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.preview.PreviewWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.jnats)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
}
