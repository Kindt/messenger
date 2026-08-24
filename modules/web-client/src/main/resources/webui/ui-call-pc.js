/**
 * First-party Korus browser call client.
 *
 * Uses only browser WebRTC primitives and the Korus provider-neutral signaling
 * contract. It deliberately has no dependency on Jitsi, LiveKit, or a CDN.
 */
(function (global) {
  "use strict";

  function createPeerConnection(iceServers, handlers) {
    var pc = new global.RTCPeerConnection({
      iceServers: Array.isArray(iceServers) ? iceServers : [],
      bundlePolicy: "max-bundle",
      rtcpMuxPolicy: "require",
    });
    var callbacks = handlers || {};
    if (typeof callbacks.onTrack === "function") {
      pc.ontrack = callbacks.onTrack;
    }
    if (typeof callbacks.onConnectionState === "function") {
      pc.onconnectionstatechange = function () {
        callbacks.onConnectionState(pc.connectionState);
      };
    }
    return pc;
  }

  function attachLocalStream(pc, stream) {
    if (!pc || !stream || typeof stream.getTracks !== "function") {
      throw new TypeError("peer connection and MediaStream required");
    }
    stream.getTracks().forEach(function (track) {
      pc.addTrack(track, stream);
    });
    return stream;
  }

  function prepareReceiveSlots(pc, counts) {
    if (!pc || typeof pc.addTransceiver !== "function") {
      throw new TypeError("peer connection with transceiver support required");
    }
    var requested = counts || {};
    ["audio", "video"].forEach(function (kind) {
      var count = Number(requested[kind]) || 0;
      count = Math.max(0, Math.min(16, Math.floor(count)));
      for (var i = 0; i < count; i += 1) {
        pc.addTransceiver(kind, { direction: "recvonly" });
      }
    });
  }

  function bindIceSignaling(pc, sendSignal) {
    if (typeof sendSignal !== "function") {
      throw new TypeError("sendSignal function required");
    }
    pc.onicecandidate = function (event) {
      if (!event.candidate) return;
      var value = typeof event.candidate.toJSON === "function"
        ? event.candidate.toJSON()
        : event.candidate;
      sendSignal({
        type: "ice",
        candidate: JSON.stringify(value),
      });
    };
  }

  function preferPcmuInSdp(sdp) {
    if (typeof sdp !== "string" || sdp.indexOf("m=audio") === -1) {
      return sdp;
    }
    return sdp.replace(/m=audio (\S+) (\S+) ([^\r\n]+)/g, function (_match, port, proto, payloads) {
      var points = payloads.split(/\s+/).filter(Boolean);
      if (points.indexOf("0") === -1) {
        return "m=audio " + port + " " + proto + " " + payloads;
      }
      var rest = points.filter(function (pt) { return pt !== "0"; });
      return "m=audio " + port + " " + proto + " 0" + (rest.length ? " " + rest.join(" ") : "");
    });
  }

  function preferPcmuCodecs(pc) {
    if (!pc || typeof pc.getTransceivers !== "function") return;
    var sender = global.RTCRtpSender;
    if (!sender || typeof sender.getCapabilities !== "function") return;
    var caps = sender.getCapabilities("audio");
    if (!caps || !Array.isArray(caps.codecs)) return;
    var pcmu = [];
    var rest = [];
    caps.codecs.forEach(function (codec) {
      var mime = codec && codec.mimeType ? String(codec.mimeType).toLowerCase() : "";
      if (mime === "audio/pcmu") pcmu.push(codec);
      else rest.push(codec);
    });
    if (!pcmu.length) return;
    pc.getTransceivers().forEach(function (transceiver) {
      var kind = transceiver && (
        (transceiver.sender && transceiver.sender.track && transceiver.sender.track.kind)
        || (transceiver.receiver && transceiver.receiver.track && transceiver.receiver.track.kind)
      );
      if (kind !== "audio" || typeof transceiver.setCodecPreferences !== "function") return;
      try {
        transceiver.setCodecPreferences(pcmu.concat(rest));
      } catch (ignored) {}
    });
  }

  async function startAsOfferer(pc, sendSignal, offerOptions) {
    bindIceSignaling(pc, sendSignal);
    preferPcmuCodecs(pc);
    var offer = await pc.createOffer(offerOptions || undefined);
    var rewritten = {
      type: offer.type,
      sdp: preferPcmuInSdp(offer.sdp),
    };
    await pc.setLocalDescription(rewritten);
    var local = pc.localDescription || rewritten;
    await sendSignal({ type: "offer", sdp: local.sdp });
    return local;
  }

  async function answerOffer(pc, offerSignal, sendSignal) {
    bindIceSignaling(pc, sendSignal);
    await applyRemoteSignal(pc, offerSignal);
    var answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    var local = pc.localDescription || answer;
    await sendSignal({ type: "answer", sdp: local.sdp });
    return local;
  }

  async function applyRemoteSignal(pc, signal) {
    if (!pc || !signal || !signal.type) return false;
    if (signal.type === "offer" || signal.type === "answer") {
      await pc.setRemoteDescription({ type: signal.type, sdp: signal.sdp });
      return true;
    }
    if (signal.type === "ice") {
      var candidate = signal.candidate;
      if (typeof candidate === "string") {
        candidate = JSON.parse(candidate);
      }
      await pc.addIceCandidate(candidate);
      return true;
    }
    if (signal.type === "hangup") {
      closePeer(pc);
      return true;
    }
    if (signal.type === "participant_left") {
      return true;
    }
    if (signal.type === "session_ended") {
      closePeer(pc);
      return true;
    }
    return false;
  }

  function mediaSignalError(signal) {
    var code = signal && (signal.error_code || signal.errorCode);
    code = typeof code === "string" && code ? code : "MEDIA_NEGOTIATION_FAILED";
    var error = new Error(code);
    error.name = "MediaNegotiationError";
    error.code = code;
    return error;
  }

  function closePeer(pc) {
    if (pc && typeof pc.close === "function") {
      pc.close();
    }
  }

  function createApi(apiJson) {
    if (typeof apiJson !== "function") {
      throw new TypeError("apiJson function required");
    }
    return {
      create: function (chatId, kind, mediaIntent) {
        return apiJson("/chats/" + encodeURIComponent(chatId) + "/calls", {
          method: "POST",
          jsonBody: {
            kind: kind || "group",
            media_intent: mediaIntent === "video" ? "video" : "audio",
          },
        });
      },
      join: function (chatId, sessionId) {
        return apiJson(
          "/chats/" + encodeURIComponent(chatId)
            + "/calls/" + encodeURIComponent(sessionId) + "/join",
          { method: "POST", jsonBody: {} }
        );
      },
      decline: function (chatId, sessionId) {
        return apiJson(
          "/chats/" + encodeURIComponent(chatId)
            + "/calls/" + encodeURIComponent(sessionId) + "/decline",
          { method: "POST", jsonBody: {} }
        );
      },
      send: function (join, signal) {
        return apiJson(
          "/chats/" + encodeURIComponent(join.chat_id)
            + "/calls/" + encodeURIComponent(join.session_id)
            + "/signals/" + encodeURIComponent(join.participant_id),
          { method: "POST", jsonBody: signal }
        );
      },
      poll: function (join) {
        return apiJson(
          "/chats/" + encodeURIComponent(join.chat_id)
            + "/calls/" + encodeURIComponent(join.session_id)
            + "/signals/" + encodeURIComponent(join.participant_id),
          { method: "GET" }
        );
      },
      leave: function (join) {
        return apiJson(
          "/chats/" + encodeURIComponent(join.chat_id)
            + "/calls/" + encodeURIComponent(join.session_id)
            + "/participants/" + encodeURIComponent(join.participant_id) + "/leave",
          { method: "POST", jsonBody: {} }
        );
      },
      end: function (join) {
        return apiJson(
          "/chats/" + encodeURIComponent(join.chat_id)
            + "/calls/" + encodeURIComponent(join.session_id) + "/end",
          { method: "POST", jsonBody: {} }
        );
      },
    };
  }

  function createSessionController(options) {
    var config = options || {};
    var api = config.api;
    var mediaDevices = config.mediaDevices || (global.navigator && global.navigator.mediaDevices);
    var createPc = config.createPeerConnection || createPeerConnection;
    var setTimer = config.setTimeout || global.setTimeout.bind(global);
    var clearTimer = config.clearTimeout || global.clearTimeout.bind(global);
    var onState = typeof config.onState === "function" ? config.onState : function () {};
    var onRemoteTrack =
      typeof config.onRemoteTrack === "function" ? config.onRemoteTrack : function () {};
    var onParticipantLeft =
      typeof config.onParticipantLeft === "function" ? config.onParticipantLeft : function () {};
    if (!api || typeof api.create !== "function" || typeof api.poll !== "function") {
      throw new TypeError("Korus Calls API required");
    }
    if (!mediaDevices || typeof mediaDevices.getUserMedia !== "function") {
      throw new TypeError("mediaDevices.getUserMedia required");
    }

    var current = {
      status: "idle",
      chatId: null,
      mediaIntent: "audio",
      join: null,
      pc: null,
      localStream: null,
      screenStream: null,
      micOn: true,
      cameraOn: false,
      error: null,
    };
    var generation = 0;
    var pollTimer = null;
    var connectTimer = null;
    var disconnectTimer = null;
    var mediaOperation = Promise.resolve();

    function snapshot() {
      return Object.freeze({
        status: current.status,
        chatId: current.chatId,
        mediaIntent: current.mediaIntent,
        join: current.join,
        pc: current.pc,
        localStream: current.localStream,
        screenStream: current.screenStream,
        micOn: current.micOn,
        cameraOn: current.cameraOn,
        error: current.error,
      });
    }

    function publish(status, error) {
      if (status) current.status = status;
      current.error = error || null;
      onState(snapshot());
    }

    function stopStream(stream) {
      if (!stream || typeof stream.getTracks !== "function") return;
      stream.getTracks().forEach(function (track) {
        if (track && typeof track.stop === "function" && track.readyState !== "ended") {
          track.stop();
        }
      });
    }

    function clearTimers() {
      [pollTimer, connectTimer, disconnectTimer].forEach(function (timer) {
        if (timer != null) clearTimer(timer);
      });
      pollTimer = null;
      connectTimer = null;
      disconnectTimer = null;
    }

    function releaseLocal() {
      stopStream(current.screenStream);
      stopStream(current.localStream);
      current.screenStream = null;
      current.localStream = null;
      current.cameraOn = false;
      current.micOn = true;
    }

    function closeConnection() {
      if (current.pc) closePeer(current.pc);
      current.pc = null;
    }

    async function bestEffortLeave(join) {
      if (!join || typeof api.leave !== "function") return;
      try {
        await api.leave(join);
      } catch (ignored) {}
    }

    function fail(error, run) {
      if (run != null && run !== generation) return;
      generation += 1;
      var failedJoin = current.join;
      current.join = null;
      clearTimers();
      closeConnection();
      releaseLocal();
      publish("error", error || new Error("call connection failed"));
      if (failedJoin) bestEffortLeave(failedJoin);
    }

    async function sendSignal(signal) {
      if (!current.join || generation === 0) return;
      await api.send(current.join, signal);
    }

    function transportIsConnected(pc) {
      return !!pc && (
        pc.connectionState === "connected"
        || pc.iceConnectionState === "connected"
        || pc.iceConnectionState === "completed"
      );
    }

    async function pollSignals(run) {
      if (run !== generation || !current.join || current.status === "leaving") return;
      try {
        var signals = await api.poll(current.join);
        for (var i = 0; i < (Array.isArray(signals) ? signals.length : 0); i += 1) {
          if (run !== generation || !current.pc) return;
          if (signals[i].type === "error") {
            fail(mediaSignalError(signals[i]), run);
            return;
          }
          await applyRemoteSignal(current.pc, signals[i]);
          if (signals[i].type === "participant_left") {
            onParticipantLeft(signals[i], snapshot());
            continue;
          }
          if (signals[i].type === "hangup" || signals[i].type === "session_ended") {
            generation += 1;
            clearTimers();
            closeConnection();
            releaseLocal();
            current.join = null;
            current.chatId = null;
            publish("idle");
            return;
          }
          if (
            current.status === "reconnecting"
            && signals[i].type === "answer"
            && transportIsConnected(current.pc)
          ) {
            publish("connected");
          }
        }
      } catch (error) {
        if (run === generation && current.status === "connected") {
          publish("reconnecting");
        }
      }
      if (run === generation && current.status !== "error" && current.status !== "leaving") {
        pollTimer = setTimer(function () {
          pollSignals(run);
        }, 250);
      }
    }

    function bindConnectionState(pc, run) {
      function restoreConnectedState() {
        if (run !== generation || current.status === "connected") return;
        if (transportIsConnected(pc)) {
          if (disconnectTimer != null) clearTimer(disconnectTimer);
          disconnectTimer = null;
          if (connectTimer != null) clearTimer(connectTimer);
          connectTimer = null;
          publish("connected");
        }
      }
      pc.ontrack = function (event) {
        if (run === generation) onRemoteTrack(event, snapshot());
      };
      pc.onconnectionstatechange = function () {
        if (run !== generation) return;
        if (pc.connectionState === "connected") {
          restoreConnectedState();
          return;
        }
        if (pc.connectionState === "disconnected") {
          if (disconnectTimer != null) return;
          disconnectTimer = setTimer(function () {
            if (run === generation && pc.connectionState === "disconnected") {
              publish("reconnecting");
            }
          }, 3000);
          return;
        }
        if (pc.connectionState === "failed") {
          publish("reconnecting");
        }
      };
      pc.oniceconnectionstatechange = restoreConnectedState;
    }

    async function start(chatId, mediaIntent, sessionId, callKind) {
      if (!chatId) throw new TypeError("chatId required");
      if (current.status !== "idle" && current.status !== "error") {
        return current.join;
      }
      generation += 1;
      var run = generation;
      clearTimers();
      closeConnection();
      releaseLocal();
      current.chatId = chatId;
      current.mediaIntent = mediaIntent === "video" ? "video" : "audio";
      current.join = null;
      publish("acquiring");
      connectTimer = setTimer(function () {
        if (
          run === generation
          && (current.status === "acquiring" || current.status === "connecting")
        ) {
          fail(new Error("call connection timeout"), run);
        }
      }, Number(config.connectTimeoutMs) || 20000);
      try {
        var stream = await mediaDevices.getUserMedia({
          audio: true,
          video: current.mediaIntent === "video" ? { facingMode: "user" } : false,
        });
        if (run !== generation) {
          stopStream(stream);
          return null;
        }
        current.localStream = stream;
        current.cameraOn = stream.getVideoTracks().length > 0;
        current.micOn = stream.getAudioTracks().some(function (track) {
          return track.enabled !== false;
        });
        var joined = sessionId
          ? await api.join(chatId, sessionId)
          : await api.create(
            chatId,
            callKind === "direct" ? "direct" : "group",
            current.mediaIntent
          );
        if (run !== generation) {
          stopStream(stream);
          await bestEffortLeave(joined);
          return null;
        }
        current.join = joined;
        publish("connecting");
        var pc = createPc(current.join.ice_servers || [], {});
        current.pc = pc;
        bindConnectionState(pc, run);
        attachLocalStream(pc, stream);
        prepareReceiveSlots(pc, {
          audio: Math.max(0, Number(config.audioReceiveSlots) || 3),
          video: Math.max(0, Number(config.videoReceiveSlots) || (current.cameraOn ? 7 : 8)),
        });
        await startAsOfferer(pc, sendSignal);
        await pollSignals(run);
        return current.join;
      } catch (error) {
        if (run !== generation) return null;
        fail(error, run);
        throw error;
      }
    }

    async function renegotiate(run) {
      if ((run != null && run !== generation) || !current.pc || !current.join) return false;
      var pc = current.pc;
      publish("reconnecting");
      await startAsOfferer(pc, sendSignal, { iceRestart: true });
      return run == null || (run === generation && current.pc === pc);
    }

    function toggleMicrophone() {
      if (!current.localStream) return false;
      current.micOn = !current.micOn;
      current.localStream.getAudioTracks().forEach(function (track) {
        track.enabled = current.micOn;
      });
      onState(snapshot());
      return current.micOn;
    }

    async function toggleCamera() {
      if (!current.localStream || !current.pc) return false;
      var run = generation;
      var pc = current.pc;
      var localStream = current.localStream;
      var tracks = localStream.getVideoTracks();
      if (!tracks.length) {
        var videoStream = await mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: "user" },
        });
        if (
          run !== generation
          || current.pc !== pc
          || current.localStream !== localStream
          || current.status === "leaving"
          || current.status === "idle"
        ) {
          stopStream(videoStream);
          return false;
        }
        var track = videoStream.getVideoTracks()[0];
        if (!track) {
          stopStream(videoStream);
          return false;
        }
        localStream.addTrack(track);
        var sender = pc.addTrack(track, localStream);
        current.cameraOn = true;
        try {
          await renegotiate(run);
        } catch (error) {
          if (run !== generation) {
            stopStream(videoStream);
            return false;
          }
          if (sender && typeof pc.removeTrack === "function") pc.removeTrack(sender);
          if (typeof localStream.removeTrack === "function") localStream.removeTrack(track);
          stopStream(videoStream);
          current.cameraOn = false;
          throw error;
        }
      } else {
        current.cameraOn = !current.cameraOn;
        tracks.forEach(function (track) {
          track.enabled = current.cameraOn;
        });
        onState(snapshot());
      }
      return current.cameraOn;
    }

    async function toggleScreen() {
      if (!current.pc) return false;
      var run = generation;
      var pc = current.pc;
      if (current.screenStream) {
        var oldTracks = current.screenStream.getTracks();
        var senders = typeof pc.getSenders === "function" ? pc.getSenders() : [];
        senders.forEach(function (sender) {
          if (sender.track && oldTracks.indexOf(sender.track) >= 0) {
            pc.removeTrack(sender);
          }
        });
        stopStream(current.screenStream);
        current.screenStream = null;
        await renegotiate(run);
        return false;
      }
      if (typeof mediaDevices.getDisplayMedia !== "function") {
        throw new Error("screen sharing unsupported");
      }
      var screen = await mediaDevices.getDisplayMedia({ video: true, audio: false });
      if (
        run !== generation
        || current.pc !== pc
        || current.status === "leaving"
        || current.status === "idle"
      ) {
        stopStream(screen);
        return false;
      }
      current.screenStream = screen;
      screen.getTracks().forEach(function (track) {
        pc.addTrack(track, screen);
        track.onended = function () {
          if (current.screenStream === screen) toggleScreen().catch(function () {});
        };
      });
      try {
        await renegotiate(run);
      } catch (error) {
        if (current.screenStream === screen) current.screenStream = null;
        stopStream(screen);
        if (run !== generation) return false;
        throw error;
      }
      return true;
    }

    function enqueueMediaOperation(operation) {
      var result = mediaOperation.catch(function () {}).then(operation);
      mediaOperation = result.catch(function () {});
      return result;
    }

    async function leave() {
      if (current.status === "idle" || current.status === "leaving") return;
      var leavingJoin = current.join;
      generation += 1;
      clearTimers();
      publish("leaving");
      closeConnection();
      releaseLocal();
      try {
        if (leavingJoin) await api.leave(leavingJoin);
      } finally {
        current.chatId = null;
        current.join = null;
        current.mediaIntent = "audio";
        publish("idle");
      }
    }

    function cancel() {
      return leave();
    }

    return Object.freeze({
      start: start,
      leave: leave,
      cancel: cancel,
      retry: function () {
        var chatId = current.chatId;
        var intent = current.mediaIntent;
        var sessionId = current.join && current.join.session_id;
        generation += 1;
        current.status = "idle";
        return start(chatId, intent, sessionId);
      },
      toggleMicrophone: toggleMicrophone,
      toggleCamera: function () {
        return enqueueMediaOperation(toggleCamera);
      },
      toggleScreen: function () {
        return enqueueMediaOperation(toggleScreen);
      },
      snapshot: snapshot,
    });
  }

  global.KorusUiCallPc = Object.freeze({
    createPeerConnection: createPeerConnection,
    attachLocalStream: attachLocalStream,
    prepareReceiveSlots: prepareReceiveSlots,
    bindIceSignaling: bindIceSignaling,
    preferPcmuInSdp: preferPcmuInSdp,
    startAsOfferer: startAsOfferer,
    answerOffer: answerOffer,
    applyRemoteSignal: applyRemoteSignal,
    closePeer: closePeer,
    createApi: createApi,
    createSessionController: createSessionController,
  });
})(typeof window !== "undefined" ? window : globalThis);
