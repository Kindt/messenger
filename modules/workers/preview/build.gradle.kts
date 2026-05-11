plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.preview.PreviewWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.17.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
