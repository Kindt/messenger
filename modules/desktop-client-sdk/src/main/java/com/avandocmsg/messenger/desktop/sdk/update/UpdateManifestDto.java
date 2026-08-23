package com.avandocmsg.messenger.desktop.sdk.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateManifestDto(
    @JsonProperty("schema_version") int schemaVersion,
    String channel,
    String version,
    @JsonProperty("published_at") String publishedAt,
    @JsonProperty("release_notes_url") String releaseNotesUrl,
    @JsonProperty("min_supported_version") String minSupportedVersion,
    UpdateSignatureDto signature,
    List<UpdateArtifactDto> artifacts
) {}
