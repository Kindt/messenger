plugins {
    id("application")
}

val tomcatVersion = "11.0.22"

dependencies {
    implementation(project(":modules:common"))

    implementation("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion")
    runtimeOnly("org.apache.tomcat.embed:tomcat-embed-jasper:$tomcatVersion")

    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    implementation("jakarta.websocket:jakarta.websocket-api:2.2.0")
    implementation("jakarta.websocket:jakarta.websocket-client-api:2.2.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("io.nats:jnats:2.17.4")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.mockito:mockito-core:5.23.0")
}

application {
    mainClass = "com.avandocmsg.messenger.ws.WsGatewayApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
}
