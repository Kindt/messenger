package com.avandocmsg.messenger.desktop.sdk.files;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.attachments.AttachmentPathResolver;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.FileUploadResponse;
import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import com.avandocmsg.messenger.desktop.sdk.model.SendMessageRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Upload to API, send file message, cache bytes locally with sidecar meta. */
public final class FileTransferService {

    private final AttachmentPathResolver resolver;

    public FileTransferService(AttachmentPathResolver resolver) {
        this.resolver = resolver;
    }

    public MessageDto uploadAndSend(
        KorusApiClient api,
        String token,
        ChatRef chat,
        Path source,
        String threadId,
        String serverDisplayName
    ) throws IOException {
        var filename = source.getFileName().toString();
        var uploaded = api.uploadFile(token, source, filename);
        cacheLocal(source, uploaded, serverDisplayName);
        return api.sendMessage(
            token,
            chat.chatId(),
            new SendMessageRequest("file", uploaded.id(), null, threadId)
        );
    }

    public Path cacheLocal(Path source, FileUploadResponse uploaded, String serverDisplayName) throws IOException {
        var target = resolver.resolve(serverDisplayName, uploaded.id(), uploaded.filename());
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        var meta = target.resolveSibling(target.getFileName() + ".meta.json");
        Files.writeString(meta, JsonSupport.mapper().writeValueAsString(uploaded));
        return target;
    }

    public byte[] download(KorusApiClient api, String token, String fileId) {
        return api.downloadFileContent(token, fileId);
    }
}
