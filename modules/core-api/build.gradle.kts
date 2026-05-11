plugins {
    id("application")
}

val tomcatVersion = "10.1.19"
val jerseyVersion = "3.1.3"
val swaggerVersion = "2.2.49"

dependencies {
    implementation(project(":modules:common"))

    // Tomcat embedded
    implementation("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-jasper:$tomcatVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-el:$tomcatVersion")

    // Jersey (JAX-RS)
    implementation("org.glassfish.jersey.containers:jersey-container-servlet:$jerseyVersion")
    implementation("org.glassfish.jersey.inject:jersey-hk2:$jerseyVersion")
    implementation("org.glassfish.jersey.media:jersey-media-json-jackson:$jerseyVersion")
    implementation("org.glassfish.jersey.media:jersey-media-multipart:$jerseyVersion")
    implementation("org.glassfish.jersey.ext:jersey-bean-validation:$jerseyVersion")

    // Jakarta EE
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // DB
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.8.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.8.1")

    // Redis + NATS
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    implementation("io.nats:jnats:2.17.4")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Auth (JWT)
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // OpenAPI / Swagger
    implementation("io.swagger.core.v3:swagger-jaxrs2-jakarta:$swaggerVersion")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:$swaggerVersion")

    // File storage (MinIO S3-compatible)
    implementation("io.minio:minio:8.5.17")

    // Crypto (E2EE / MLS)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Solr (optional message search)
    implementation("org.apache.solr:solr-solrj:9.4.1")

    testImplementation("com.h2database:h2:2.2.224")

    // Prometheus text exposition (ТЗ п. 22, observability baseline)
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_common:0.16.0")
}

application {
    mainClass = "com.avandocmsg.messenger.api.MessengerApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
    applicationDistribution.into("") {
        from("src/main/resources") {
            into("conf")
        }
    }
}
