package com.avandocmsg.messenger.api.files;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioFileDeletionTest {

    @Test
    void delete_delegatesToMinioClient() throws Exception {
        var client = mock(io.minio.MinioClient.class);
        doNothing().when(client).removeObject(any(io.minio.RemoveObjectArgs.class));
        var proxy = new MinioFileProxy(client, "avandocmsg");
        proxy.delete("file-id/sample.txt");
        verify(client).removeObject(any(io.minio.RemoveObjectArgs.class));
    }
}
