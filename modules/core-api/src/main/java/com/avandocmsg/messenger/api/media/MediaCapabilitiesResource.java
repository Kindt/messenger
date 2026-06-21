package com.avandocmsg.messenger.api.media;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.media.dto.MediaCapabilitiesResponse;
import com.avandocmsg.messenger.api.platform.PlatformModuleRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;

@Path("/v1/media")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Media", description = "Лимиты файлов и подсказки для клиента (изображения, видео, конференции)")
public class MediaCapabilitiesResource {

    private final AppConfig appConfig;
    private final PlatformModuleRegistry platformModuleRegistry;

    @Inject
    public MediaCapabilitiesResource(AppConfig appConfig, PlatformModuleRegistry platformModuleRegistry) {
        this.appConfig = appConfig;
        this.platformModuleRegistry = platformModuleRegistry;
    }

    @GET
    @Path("capabilities")
    @Operation(summary = "Возможности медиа и WebRTC",
        description = "Загрузка файлов идёт в POST /api/v1/files/upload; в сообщениях используйте type image|video|file и ссылку на file_id.")
    public MediaCapabilitiesResponse capabilities() {
        List<String> stun = Arrays.stream(appConfig.webrtcStunUris().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        var types = List.of("text", "image", "video", "video_note", "audio", "voice", "file");
        var note = "Видеозвонки (mesh): конференция в чате. Прямой эфир (SFU): POST .../chats/{id}/live-sessions — addon-live (spec 021).";
        boolean liveAddon = platformModuleRegistry.isAddonEffective("addon-live");
        boolean liveEnabled = liveAddon && appConfig.liveStreamingEnabled();
        return new MediaCapabilitiesResponse(
            appConfig.mediaMaxUploadBytes(),
            types,
            stun,
            "jitsi",
            appConfig.jitsiMeetBaseUrl(),
            note,
            appConfig.e2eeSchemes(),
            appConfig.mlsStatus(),
            liveEnabled,
            liveEnabled ? appConfig.livekitUrl() : "",
            liveEnabled ? appConfig.livestreamMaxWebrtcViewers() : 0,
            liveEnabled
        );
    }
}
