plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.pipeline.MessagePipelineWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.lettuce)
    implementation(libs.jnats)
    implementation(libs.postgresql)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus.server)

    testImplementation(libs.h2)
}
