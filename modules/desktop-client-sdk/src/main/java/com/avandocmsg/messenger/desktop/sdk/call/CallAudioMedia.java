package com.avandocmsg.messenger.desktop.sdk.call;

import java.util.function.Consumer;

public interface CallAudioMedia extends AutoCloseable {

    String createOffer();

    void connect(String answerSdp) throws Exception;

    void sendPcmu(byte[] payload);

    void onPcmu(Consumer<byte[]> listener);

    boolean mediaReady();

    @Override
    void close();
}
