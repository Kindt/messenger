package com.avandocmsg.messenger.mobile.sdk.storage

import com.avandocmsg.messenger.mobile.sdk.model.LocalProfile
import com.avandocmsg.messenger.mobile.sdk.model.ProfileSettings
import com.avandocmsg.messenger.mobile.sdk.model.ServerEntry
import com.avandocmsg.messenger.mobile.sdk.model.ServerRegistryDocument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.avandocmsg.messenger.mobile.sdk.io.KorusFileIo
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class JsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
}

class ProfileStore(
    private val rootDir: Path,
    private val codec: JsonCodec = JsonCodec()
) {
    private val profilesRoot = rootDir.resolve("profiles")

    fun listProfiles(): List<LocalProfile> {
        if (!KorusFileIo.exists(profilesRoot)) return emptyList()
        return Files.newDirectoryStream(profilesRoot).use { entries ->
            entries.asSequence()
                .filter { Files.isDirectory(it) }
                .map { readProfile(it) }
                .toList()
        }
    }

    fun createProfile(displayName: String): LocalProfile {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val profile = LocalProfile(
            profileId = id,
            displayName = displayName.trim(),
            createdAt = now,
            lastUsedAt = now
        )
        writeProfile(profile)
        return profile
    }

    fun readProfile(profileId: String): LocalProfile {
        return readProfile(profileDir(profileId))
    }

    fun touchProfile(profileId: String) {
        val profile = readProfile(profileId)
        writeProfile(profile.copy(lastUsedAt = Instant.now().toString()))
    }

    fun profileRoot(profileId: String): Path = profileDir(profileId)

    fun settingsPath(profileId: String): Path = profileDir(profileId).resolve("settings.json")

    fun stateDir(profileId: String): Path = profileDir(profileId).resolve("state")

    private fun profileDir(profileId: String): Path = profilesRoot.resolve(profileId)

    private fun readProfile(dir: Path): LocalProfile {
        val text = KorusFileIo.readText(dir.resolve("profile.json"))
        return codec.json.decodeFromString(text)
    }

    private fun writeProfile(profile: LocalProfile) {
        val dir = profileDir(profile.profileId)
        KorusFileIo.createDirectories(dir)
        KorusFileIo.createDirectories(dir.resolve("state"))
        KorusFileIo.writeText(dir.resolve("profile.json"), codec.json.encodeToString(profile))
        if (!KorusFileIo.exists(dir.resolve("settings.json"))) {
            KorusFileIo.writeText(dir.resolve("settings.json"), codec.json.encodeToString(profile.settings))
        }
    }
}

class ServerRegistry(
    private val profileDir: Path,
    private val codec: JsonCodec = JsonCodec()
) {
    private val path = profileDir.resolve("servers.json")

    fun load(): ServerRegistryDocument {
        if (!KorusFileIo.exists(path)) {
            return ServerRegistryDocument()
        }
        return codec.json.decodeFromString(KorusFileIo.readText(path))
    }

    fun save(document: ServerRegistryDocument) {
        KorusFileIo.createDirectories(profileDir)
        KorusFileIo.writeText(path, codec.json.encodeToString(document))
    }

    fun upsert(entry: ServerEntry): ServerRegistryDocument {
        val current = load()
        val without = current.servers.filterNot { it.serverId == entry.serverId }
        val updated = ServerRegistryDocument(servers = without + entry)
        save(updated)
        return updated
    }

    fun remove(serverId: String): ServerRegistryDocument {
        val updated = ServerRegistryDocument(servers = load().servers.filterNot { it.serverId == serverId })
        save(updated)
        return updated
    }
}

class ProfileSettingsStore(
    private val profileStore: ProfileStore,
    private val codec: JsonCodec = JsonCodec()
) {
    fun read(profileId: String): ProfileSettings {
        val path = profileStore.settingsPath(profileId)
        if (!KorusFileIo.exists(path)) return ProfileSettings()
        return codec.json.decodeFromString(KorusFileIo.readText(path))
    }

    fun write(profileId: String, settings: ProfileSettings) {
        KorusFileIo.writeText(profileStore.settingsPath(profileId), codec.json.encodeToString(settings))
    }
}
