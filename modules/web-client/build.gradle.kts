plugins {
    id("application")
}

val tomcatVersion = "10.1.19"

dependencies {
    implementation("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
}

application {
    mainClass = "com.avandocmsg.messenger.web.WebClientApplication"
    applicationDefaultJvmArgs = listOf("-Dapp.home=\$APP_HOME")
}
