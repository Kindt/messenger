plugins {
    id("application")
}

val tomcatVersion = "11.0.22"

dependencies {
    implementation("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.34")
    implementation("org.slf4j:slf4j-api:2.0.12")
}

application {
    mainClass = "com.avandocmsg.messenger.web.WebClientApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
}

val webuiTailwindOut = layout.projectDirectory.file("src/main/resources/webui/tailwind.css")

tasks.register<Exec>("buildTailwindCss") {
    group = "webui"
    description = "Build tailwind.css from webui-build (requires Node/npm)"
    workingDir = layout.projectDirectory.dir("webui-build").asFile
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "build:css")
    dependsOn("buildLocales")
    inputs.file(layout.projectDirectory.file("webui-build/src/input.css"))
    inputs.file(layout.projectDirectory.file("webui-build/package.json"))
    inputs.file(layout.projectDirectory.file("webui-build/package-lock.json"))
    inputs.files(
        layout.projectDirectory.file("src/main/resources/webui/app.js"),
        layout.projectDirectory.file("src/main/resources/webui/index.html"),
    )
    outputs.file(webuiTailwindOut)
}

tasks.register<Exec>("buildLocales") {
    group = "webui"
    description = "Copy locale JSON from webui-build/locales/messages to webui/locales"
    workingDir = layout.projectDirectory.dir("webui-build").asFile
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "build:locales")
    inputs.dir(layout.projectDirectory.dir("webui-build/locales/messages"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/build-locales.mjs"))
    outputs.dir(layout.projectDirectory.dir("src/main/resources/webui/locales"))
}

tasks.named("processResources") {
    dependsOn("buildTailwindCss", "buildLocales")
}
