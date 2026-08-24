package com.avandocmsg.messenger.desktop.sdk.call;

import com.avandocmsg.messenger.media.NativeWebRtcAudioClient;
import java.util.function.Consumer;

public final class NativeCallAudioMedia implements CallAudioMedia {

    private final NativeWebRtcAudioClient client = NativeWebRtcAudioClient.create();

    @Override
    public String createOffer() {
        return client.createOffer();
    }

    @Override
    public void connect(String answerSdp) throws Exception {
        client.connect(answerSdp);
    }

    @Override
    public void sendPcmu(byte[] payload) {
        client.sendPcmu(payload);
    }

    @Override
    public void onPcmu(Consumer<byte[]> listener) {
        client.onPcmu(listener);
    }

    @Override
    public boolean mediaReady() {
        return client.mediaReady();
    }

    @Override
    public void close() {
        client.close();
    }
}
