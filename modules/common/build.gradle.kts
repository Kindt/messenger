dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api(libs.slf4j.api)
    implementation(libs.hikari) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.postgresql)
    implementation(libs.jnats)
    implementation(libs.prometheus.simpleclient)
    implementation(libs.prometheus.simpleclient.httpserver)
    implementation(libs.minio) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }
    implementation(libs.zstd.jni)
    implementation(libs.logstash.logback.encoder) {
        exclude(group = "com.fasterxml.jackson.core")
    }
    implementation(libs.caffeine) {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }

    testImplementation(libs.prometheus.simpleclient.common)
    testRuntimeOnly(libs.logback.classic)
}
