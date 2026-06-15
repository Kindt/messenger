dependencies {
    api(platform("com.fasterxml.jackson:jackson-bom:2.21.3"))
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("com.zaxxer:HikariCP:5.1.0")
    api("org.postgresql:postgresql:42.7.1")
    api("io.nats:jnats:2.17.4")
    api("io.prometheus:simpleclient:0.16.0")
    implementation("io.minio:minio:8.5.17")
    implementation("com.github.luben:zstd-jni:1.5.6-6")

    testImplementation("io.prometheus:simpleclient_common:0.16.0")
}
