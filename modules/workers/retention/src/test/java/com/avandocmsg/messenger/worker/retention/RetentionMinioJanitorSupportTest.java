package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.minio.StatObjectResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionMinioJanitorSupportTest {

    private static final com.avandocmsg.messenger.common.i18n.UserMessageSource MESSAGES =
        WorkerMessageSources.forWorker(
            RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention");

    @Test
    void listObjectKeys_collectsRecursiveListing() throws Exception {
        var client = mock(MinioClient.class);
        var item = mock(Item.class);
        when(item.objectName()).thenReturn("retention/msg-1.json");
        when(client.listObjects(any(ListObjectsArgs.class)))
            .thenReturn(List.of(new Result<>(item)));

        var keys = RetentionMinioJanitorSupport.listObjectKeys(client, "files", "retention/");

        assertEquals(Set.of("retention/msg-1.json"), keys);
    }

    @Test
    void existenceIndex_listedKeySkipsStatObject() throws Exception {
        var client = mock(MinioClient.class);
        var index = new RetentionMinioJanitorSupport.ExistenceIndex(
            client, "files", Set.of("retention/exists.json"), MESSAGES);

        assertTrue(index.objectExists("retention/exists.json"));
        verify(client, never()).statObject(any(StatObjectArgs.class));
    }

    @Test
    void existenceIndex_cacheMissUsesStatOncePerKey() throws Exception {
        var client = mock(MinioClient.class);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        var index = new RetentionMinioJanitorSupport.ExistenceIndex(
            client, "files", Set.of(), MESSAGES);

        assertTrue(index.objectExists("messages/abc.json"));
        assertTrue(index.objectExists("messages/abc.json"));
        verify(client, times(1)).statObject(any(StatObjectArgs.class));
    }

    @Test
    void existenceIndex_noSuchKeyStatReturnsFalse() throws Exception {
        var client = mock(MinioClient.class);
        var errorResponse = mock(io.minio.messages.ErrorResponse.class);
        when(errorResponse.code()).thenReturn("NoSuchKey");
        var statError = mock(ErrorResponseException.class);
        when(statError.errorResponse()).thenReturn(errorResponse);
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(statError);
        var index = new RetentionMinioJanitorSupport.ExistenceIndex(
            client, "files", Set.of(), MESSAGES);

        assertFalse(index.objectExists("messages/missing.json"));
    }

    @Test
    void removeObjectsBatch_usesRemoveObjectsNotSingleRemove() throws Exception {
        var client = mock(MinioClient.class);
        when(client.removeObjects(any(RemoveObjectsArgs.class))).thenReturn(List.of());
        var fileId = UUID.randomUUID();
        var requests = List.of(
            new RetentionMinioJanitorSupport.MinioDeleteRequest(fileId, "a/1.txt"),
            new RetentionMinioJanitorSupport.MinioDeleteRequest(fileId, "b/2.txt"));

        RetentionMinioJanitorSupport.removeObjectsBatch(
            client, true, "files", requests, MESSAGES);

        var captor = ArgumentCaptor.forClass(RemoveObjectsArgs.class);
        verify(client).removeObjects(captor.capture());
        int objectCount = 0;
        for (var ignored : captor.getValue().objects()) {
            objectCount++;
        }
        assertEquals(2, objectCount);
        verify(client, never()).removeObject(any());
    }

    @Test
    void removeObjectsBatch_logsPerObjectDeleteErrors() throws Exception {
        var client = mock(MinioClient.class);
        var deleteError = mock(DeleteError.class);
        when(deleteError.objectName()).thenReturn("bad/key");
        when(deleteError.message()).thenReturn("access denied");
        when(client.removeObjects(any(RemoveObjectsArgs.class)))
            .thenReturn(List.of(new Result<>(deleteError)));
        var fileId = UUID.randomUUID();

        RetentionMinioJanitorSupport.removeObjectsBatch(
            client,
            true,
            "files",
            List.of(new RetentionMinioJanitorSupport.MinioDeleteRequest(fileId, "bad/key")),
            MESSAGES);

        verify(client).removeObjects(any(RemoveObjectsArgs.class));
    }
}
