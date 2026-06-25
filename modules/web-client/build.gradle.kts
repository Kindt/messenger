plugins {
    id("application")
}

dependencies {
    implementation(libs.tomcat.embed.core)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.bundles.logging)
}

application {
    mainClass = "com.avandocmsg.messenger.web.WebClientApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
}

val webuiTailwindOut = layout.projectDirectory.file("src/main/resources/webui/tailwind.css")

tasks.register<Exec>("buildTailwindCss") {
    group = "webui"
    description = "Build webui static assets (CSS, fonts, locales, JS bundle) via webui-build"
    workingDir = layout.projectDirectory.dir("webui-build").asFile
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "build:assets")
    inputs.file(layout.projectDirectory.file("webui-build/src/input.css"))
    inputs.file(layout.projectDirectory.file("webui-build/src/styles.css"))
    inputs.file(layout.projectDirectory.file("webui-build/package.json"))
    inputs.file(layout.projectDirectory.file("webui-build/package-lock.json"))
    inputs.files(
        layout.projectDirectory.file("src/main/resources/webui/app.js"),
        layout.projectDirectory.file("src/main/resources/webui/index.html"),
    )
    outputs.file(webuiTailwindOut)
    outputs.file(layout.projectDirectory.file("src/main/resources/webui/styles.css"))
    outputs.file(layout.projectDirectory.file("src/main/resources/webui/fonts.css"))
    outputs.file(layout.projectDirectory.file("src/main/resources/webui/app.bundle.js"))
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

tasks.register<Exec>("testMessageListUi") {
    group = "webui"
    description = "Node smoke for ui-message-list virtual window"
    workingDir = layout.projectDirectory.dir("webui-build").asFile
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "test:webui")
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-message-list.mjs"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-format-utils.mjs"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-ws-events.mjs"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-deep-link-utils.mjs"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-markdown-utils.mjs"))
    inputs.file(layout.projectDirectory.file("webui-build/scripts/test-call-mode-render.mjs"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/app.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-format-utils.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-message-article.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-message-list.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-ws-events.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-deep-link-utils.js"))
    inputs.file(layout.projectDirectory.file("src/main/resources/webui/ui-markdown-utils.js"))
}

tasks.named("processResources") {
    dependsOn("buildTailwindCss", "testMessageListUi")
}
