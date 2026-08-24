package com.avandocmsg.messenger.desktop.sdk.call;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.model.CallJoinResponse;
import com.avandocmsg.messenger.desktop.sdk.model.CallSignalResponse;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InProcessCallClient implements AutoCloseable {

    private final KorusApiClient api;
    private final Supplier<CallAudioMedia> mediaFactory;
    private final AtomicBoolean leaving = new AtomicBoolean();
    private volatile Runnable hangupListener = () -> {};
    private CallJoinResponse join;
    private String token;
    private CallAudioMedia media;
    private Thread poller;

    public InProcessCallClient(KorusApiClient api) {
        this(api, NativeCallAudioMedia::new);
    }

    public InProcessCallClient(KorusApiClient api, Supplier<CallAudioMedia> mediaFactory) {
        this.api = Objects.requireNonNull(api, "api");
        this.mediaFactory = Objects.requireNonNull(mediaFactory, "mediaFactory");
    }

    public CallJoinResponse start(String token, String chatId, String kind, String mediaIntent)
        throws Exception {
        this.token = required(token, "token");
        join = api.createCall(this.token, chatId, kind, mediaIntent);
        return connectMedia();
    }

    public CallJoinResponse join(String token, String chatId, String sessionId) throws Exception {
        this.token = required(token, "token");
        join = api.joinCall(this.token, chatId, sessionId);
        return connectMedia();
    }

    public CallJoinResponse join() {
        return join;
    }

    public boolean mediaReady() {
        return media != null && media.mediaReady();
    }

    public void sendPcmu(byte[] payload) {
        if (media != null) {
            media.sendPcmu(payload);
        }
    }

    public void onPcmu(Consumer<byte[]> listener) {
        if (media != null) {
            media.onPcmu(listener);
        }
    }

    public void onHangup(Runnable listener) {
        hangupListener = listener == null ? () -> {} : listener;
    }

    public void leave() {
        if (!leaving.compareAndSet(false, true)) {
            return;
        }
        if (poller != null) {
            poller.interrupt();
        }
        if (media != null) {
            media.close();
        }
        if (join != null && token != null) {
            try {
                api.leaveCall(token, join);
            } catch (RuntimeException ignored) {
                // The local media path is already closed.
            }
        }
        hangupListener.run();
    }

    @Override
    public void close() {
        leave();
    }

    private CallJoinResponse connectMedia() throws Exception {
        media = mediaFactory.get();
        api.sendCallSignal(token, join, "offer", media.createOffer(), null);
        var answer = awaitAnswer();
        media.connect(answer.sdp());
        poller = Thread.ofVirtual().name("korus-desktop-call-signals").start(this::pollUntilHangup);
        return join;
    }

    private CallSignalResponse awaitAnswer() throws InterruptedException {
        var deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline && !leaving.get()) {
            var signals = api.pollCallSignals(token, join);
            for (var signal : signals) {
                if ("answer".equals(signal.type()) && signal.sdp() != null) {
                    return signal;
                }
                if ("error".equals(signal.type())) {
                    throw new IllegalStateException(
                        signal.errorCode() == null ? "call media rejected" : signal.errorCode()
                    );
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("call answer timed out");
    }

    private void pollUntilHangup() {
        while (!leaving.get() && !Thread.currentThread().isInterrupted()) {
            try {
                var signals = api.pollCallSignals(token, join);
                for (var signal : signals) {
                    if ("hangup".equals(signal.type()) || "session_ended".equals(signal.type())) {
                        leave();
                        return;
                    }
                }
                Thread.sleep(400);
            } catch (RuntimeException | InterruptedException stopped) {
                return;
            }
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value;
    }
}
