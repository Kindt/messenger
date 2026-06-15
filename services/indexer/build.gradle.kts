plugins {
    id("application")
}

application {
    mainClass.set("com.avandocmsg.messenger.worker.indexer.IndexerWorker")
    applicationName = "korus-indexer-service"
}

dependencies {
    implementation(project(":modules:workers:indexer"))
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
