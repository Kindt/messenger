plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.push.PushWorker")
}

dependencies {
    implementation(project(":modules:common"))
    implementation("io.nats:jnats:2.25.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("nl.martijndwars:web-push:5.1.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
