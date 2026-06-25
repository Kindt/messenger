plugins {
    id("application")
    war
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:core-domain"))
    implementation(project(":modules:core-port"))

    // Tomcat embedded
    implementation(libs.tomcat.embed.core)
    implementation(libs.tomcat.embed.jasper)
    implementation(libs.tomcat.embed.el)

    // Jersey (JAX-RS)
    implementation(libs.jersey.container.servlet)
    implementation(libs.jersey.hk2)
    implementation(libs.jersey.media.json.jackson) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.datatype")
        exclude(group = "com.fasterxml.jackson.module")
        exclude(group = "com.fasterxml.jackson.jakarta.rs")
    }
    implementation(libs.jersey.media.multipart)
    implementation(libs.jersey.bean.validation)

    // Jakarta EE
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jakarta.ws.rs.api)
    implementation(libs.jakarta.validation.api)

    // DB
    implementation(libs.postgresql)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.flyway.core) {
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-toml")
    }
    implementation(libs.flyway.postgresql)

    // Redis + NATS
    implementation(libs.lettuce)
    implementation(libs.jnats)

    // JSON + YAML catalog (databind/jsr310 via :modules:common BOM)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.snakeyaml)

    // Logging
    implementation(libs.bundles.logging)

    // Auth (JWT)
    implementation(libs.nimbus.jose.jwt)

    // OpenAPI / Swagger
    implementation(libs.swagger.jaxrs2.jakarta) {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "org.yaml", module = "snakeyaml")
        exclude(group = "com.fasterxml.jackson.jakarta.rs")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "com.fasterxml.jackson.datatype")
        exclude(group = "com.fasterxml.jackson.module")
    }
    implementation(libs.swagger.annotations.jakarta)

    // File storage (MinIO S3-compatible)
    implementation(libs.minio) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }

    // Crypto (E2EE / MLS)
    implementation(libs.bcprov.jdk18on)

    // Solr (optional message search)
    implementation(libs.solr.solrj) {
        exclude(group = "io.swagger.core.v3", module = "swagger-annotations-jakarta")
        exclude(group = "jakarta.ws.rs", module = "jakarta.ws.rs-api")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "com.fasterxml.jackson.datatype")
        exclude(group = "com.fasterxml.jackson.module")
        exclude(group = "com.fasterxml.jackson.jakarta.rs")
    }

    testImplementation(libs.h2)

    // Prometheus text exposition (ТЗ п. 22, observability baseline)
    implementation(libs.prometheus.simpleclient)
    implementation(libs.prometheus.simpleclient.hotspot)
    implementation(libs.prometheus.simpleclient.common)
}

application {
    mainClass = "com.avandocmsg.messenger.api.MessengerApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
    applicationDistribution.into("") {
        from("src/main/resources") {
            into("conf")
        }
        from(rootProject.file("docs/external-stack-profiles.yaml")) {
            into("conf")
        }
    }
}

tasks.named("startScripts") {
    notCompatibleWithConfigurationCache("startScripts resolves application runtime classpath")
}

tasks.named<War>("war") {
    notCompatibleWithConfigurationCache("war filters runtimeClasspath with file predicate")
    archiveBaseName.set("core-api")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    setClasspath(
        sourceSets.main.get().runtimeClasspath.filter { file ->
            val name = file.name
            !name.startsWith("tomcat-embed")
        }
    )
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("docs/external-stack-profiles.yaml")) {
        into("")
    }
}

tasks.register<Exec>("buildAdminUiAssets") {
    group = "admin-ui"
    description = "Build admin-ui static assets (minified CSS, JS bundle, locale manifest)"
    workingDir = rootProject.file("modules/web-client/webui-build")
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "build:admin")
    inputs.file(rootProject.file("modules/core-api/admin-ui-build/src/styles.css"))
    inputs.file(rootProject.file("modules/core-api/src/main/resources/admin-ui/index.html"))
    inputs.files(
        rootProject.file("modules/core-api/src/main/resources/admin-ui/admin-i18n.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/ui-helpers.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/panels.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/app.js"),
    )
    inputs.dir(rootProject.file("modules/core-api/src/main/resources/admin-ui/locales"))
    outputs.file(rootProject.file("modules/core-api/src/main/resources/admin-ui/styles.css"))
    outputs.file(rootProject.file("modules/core-api/src/main/resources/admin-ui/admin.bundle.js"))
    outputs.file(rootProject.file("modules/core-api/src/main/resources/admin-ui/locales/manifest.json"))
}

tasks.register<Exec>("testAdminUiI18n") {
    group = "admin-ui"
    description = "Node smoke for admin-ui i18n and locale parity"
    dependsOn("buildAdminUiAssets")
    workingDir = rootProject.file("modules/web-client/webui-build")
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "test:admin-i18n")
    inputs.file(rootProject.file("modules/web-client/webui-build/scripts/test-admin-i18n.mjs"))
    inputs.files(
        rootProject.file("modules/core-api/src/main/resources/admin-ui/app.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/panels.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/admin-i18n.js"),
        rootProject.file("modules/core-api/src/main/resources/admin-ui/index.html"),
    )
    inputs.dir(rootProject.file("modules/core-api/src/main/resources/admin-ui/locales"))
}

tasks.register<Test>("benchmark") {
    group = "verification"
    description = "Lightweight CoreApi/MLS timing guards (*BenchmarkTest)"
    notCompatibleWithConfigurationCache("benchmark wires source set classpath directly")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        isFailOnNoMatchingTests = true
        includeTestsMatching("*Benchmark*")
    }
}
