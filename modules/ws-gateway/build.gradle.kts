plugins {
    id("application")
    war
}

dependencies {
    implementation(project(":modules:common"))

    implementation(libs.tomcat.embed.core)
    implementation(libs.tomcat.embed.websocket)
    runtimeOnly(libs.tomcat.embed.jasper)

    compileOnly(libs.jakarta.servlet.api)
    compileOnly(libs.jakarta.websocket.api)
    compileOnly(libs.jakarta.websocket.client.api)

    implementation(libs.jnats)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bundles.logging)
    implementation(libs.prometheus.simpleclient)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(libs.h2)
}

application {
    mainClass = "com.avandocmsg.messenger.ws.WsGatewayApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
}

tasks.named<War>("war") {
    notCompatibleWithConfigurationCache("war filters runtimeClasspath with file predicate")
    archiveBaseName.set("ws-gateway")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    setClasspath(
        sourceSets.main.get().runtimeClasspath.filter { file ->
            val name = file.name
            !name.startsWith("tomcat-embed-")
        }
    )
}
