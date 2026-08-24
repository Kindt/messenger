package com.avandocmsg.messenger.mobile.sdk.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdateArtifactDto(
    val platform: String,
    val url: String,
    val sha256: String,
    val signature: String
)

@Serializable
data class UpdateManifestDto(
    val schema_version: Int = 1,
    val channel: String,
    val version: String,
    val published_at: String,
    val artifacts: List<UpdateArtifactDto> = emptyList()
)

class UpdateManifestClient(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(body: String): UpdateManifestDto = json.decodeFromString(body)

    fun verifySha256(hex: String): Boolean = hex.matches(Regex("^[a-fA-F0-9]{64}$"))

    fun findArtifact(manifest: UpdateManifestDto, platform: String): UpdateArtifactDto? =
        manifest.artifacts.firstOrNull { it.platform == platform }
}
