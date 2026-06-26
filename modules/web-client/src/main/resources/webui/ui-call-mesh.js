(function (global) {
  "use strict";

  var SPEAK_THRESHOLD = 0.045;
  var SPEAK_HOLD_MS = 450;

  function memberInitials(displayName, userId) {
    var s = (displayName || userId || "?").trim();
    if (!s) return "?";
    var parts = s.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }
    if (s.length >= 2) return s.slice(0, 2).toUpperCase();
    return s.charAt(0).toUpperCase();
  }

  function createAvatarNode(displayName, userId, extraClass, avatarUrl) {
    if (global.KorusUiAvatar && typeof global.KorusUiAvatar.renderAvatar === "function") {
      var node = global.KorusUiAvatar.renderAvatar({
        url: avatarUrl || "",
        title: displayName,
        userId: userId,
        size: "lg",
      });
      node.className =
        "call-participant-avatar chat-avatar-lg" + (extraClass ? " " + extraClass : "");
      node.setAttribute("aria-hidden", "true");
      return node;
    }
    var div = document.createElement("div");
    div.className = "call-participant-avatar" + (extraClass ? " " + extraClass : "");
    div.textContent = memberInitials(displayName, userId);
    div.setAttribute("aria-hidden", "true");
    return div;
  }

  function peerDisplayName(state, peerId) {
    var meta = state.rtcPeerMeta && state.rtcPeerMeta[peerId];
    return (meta && meta.displayName) || peerId;
  }

  function isVideoTrackActive(track) {
    return track && track.kind === "video" && track.readyState === "live" && track.enabled && !track.muted;
  }

  function streamHasActiveVideo(stream) {
    if (!stream) return false;
    return stream.getVideoTracks().some(isVideoTrackActive);
  }

  function badgeLabel(state, speaking, sharing) {
    var labels = state._callMeshLabels || {};
    if (sharing) return labels.sharing || "Screen";
    if (speaking) return labels.speaking || "Speaking";
    return "";
  }

  function updateSlotDom(peerId, state) {
    var slot = document.getElementById("rtc-remote-" + peerId);
    if (!slot) return;
    var speaking = !!(state.rtcSpeakingPeers && state.rtcSpeakingPeers[peerId]);
    var sharing = !!(state.rtcSharingPeers && state.rtcSharingPeers[peerId]);
    slot.classList.toggle("rtc-slot-speaking", speaking && !sharing);
    slot.classList.toggle("rtc-slot-sharing", sharing);
    var cam = slot.querySelector("video.rtc-remote-cam");
    var showAvatar = true;
    if (cam && cam.srcObject) {
      showAvatar = !streamHasActiveVideo(cam.srcObject);
    }
    var av = slot.querySelector(".call-participant-avatar");
    if (av) av.classList.toggle("is-hidden", !showAvatar);
    if (cam) cam.classList.toggle("is-hidden", showAvatar);
    var badge = slot.querySelector(".rtc-remote-badge");
    if (badge) {
      var text = badgeLabel(state, speaking, sharing);
      badge.textContent = text;
      badge.classList.toggle("is-hidden", !text);
    }
  }

  function syncLocalStage(state) {
    var wrap = document.getElementById("callLocalStage");
    if (!wrap) return;
    var speaking = !!(state.rtcSpeakingPeers && state.rtcSpeakingPeers.local);
    var sharing = !!state.callScreenStream;
    wrap.classList.toggle("rtc-slot-speaking", speaking && !sharing);
    wrap.classList.toggle("rtc-slot-sharing", sharing);
    var showAvatar =
      !state.callStream ||
      !state.callCamOn ||
      !streamHasActiveVideo(state.callStream);
    var av = wrap.querySelector(".call-participant-avatar");
    if (av) av.classList.toggle("is-hidden", !showAvatar);
    var cam = document.getElementById("callLocalVideo");
    if (cam) cam.classList.toggle("is-hidden", showAvatar);
    var badge = wrap.querySelector(".rtc-local-badge");
    if (badge) {
      var text = sharing
        ? badgeLabel(state, false, true)
        : speaking
          ? badgeLabel(state, true, false)
          : "";
      badge.textContent = text;
      badge.classList.toggle("is-hidden", !text);
    }
  }

  function syncAllSlots(state) {
    restoreRemoteMedia(state);
    (state.rtcPeerIds || []).forEach(function (pid) {
      updateSlotDom(pid, state);
    });
    syncLocalStage(state);
  }

  function detachAnalyser(state, key) {
    if (!state._speakerAnalysers || !state._speakerAnalysers[key]) return;
    delete state._speakerAnalysers[key];
  }

  function attachAnalyser(state, key, stream) {
    if (!stream || !state._speakerCtx) return;
    if (!stream.getAudioTracks().length) return;
    state._speakerAnalysers = state._speakerAnalysers || {};
    if (state._speakerAnalysers[key]) return;
    try {
      var src = state._speakerCtx.createMediaStreamSource(stream);
      var an = state._speakerCtx.createAnalyser();
      an.fftSize = 512;
      an.smoothingTimeConstant = 0.55;
      src.connect(an);
      state._speakerAnalysers[key] = { analyser: an, lastSpeak: 0 };
    } catch (e) {}
  }

  function registerPeerStream(state, peerId, stream) {
    if (!stream) return;
    state._remoteStreams = state._remoteStreams || {};
    state._remoteStreams[peerId] = stream;
    attachAnalyser(state, peerId, stream);
  }

  function unregisterPeerStream(state, peerId) {
    if (state._remoteStreams) delete state._remoteStreams[peerId];
    if (state.rtcRemoteScreens) delete state.rtcRemoteScreens[peerId];
    detachAnalyser(state, peerId);
    if (state.rtcSpeakingPeers) delete state.rtcSpeakingPeers[peerId];
    if (state.rtcSharingPeers) delete state.rtcSharingPeers[peerId];
  }

  function refreshSpeakerSources(state) {
    if (!state._speakerMonitorRunning || !state._speakerCtx) return;
    attachAnalyser(state, "local", state.callStream);
    var remote = state._remoteStreams || {};
    Object.keys(remote).forEach(function (pid) {
      attachAnalyser(state, pid, remote[pid]);
    });
  }

  function stopSpeakerMonitor(state) {
    state._speakerMonitorRunning = false;
    if (state._speakerRaf) {
      cancelAnimationFrame(state._speakerRaf);
      state._speakerRaf = null;
    }
    state._speakerAnalysers = {};
    if (state._speakerCtx) {
      try {
        state._speakerCtx.close();
      } catch (e2) {}
      state._speakerCtx = null;
    }
  }

  function ensureSpeakerMonitor(state) {
    if (!state.callPanelOpen || state.callMode !== "mesh") {
      stopSpeakerMonitor(state);
      return;
    }
    if (state._speakerMonitorRunning) {
      refreshSpeakerSources(state);
      return;
    }
    try {
      state._speakerCtx = new (global.AudioContext || global.webkitAudioContext)();
    } catch (e) {
      return;
    }
    state._speakerMonitorRunning = true;
    state.rtcSpeakingPeers = state.rtcSpeakingPeers || {};
    refreshSpeakerSources(state);

    function loop() {
      if (!state._speakerMonitorRunning) return;
      var changed = false;
      var now = Date.now();
      var analysers = state._speakerAnalysers || {};
      Object.keys(analysers).forEach(function (key) {
        var entry = analysers[key];
        if (!entry || !entry.analyser) return;
        var data = new Uint8Array(entry.analyser.frequencyBinCount);
        entry.analyser.getByteFrequencyData(data);
        var sum = 0;
        for (var i = 0; i < data.length; i++) sum += data[i];
        var avg = sum / data.length / 255;
        var peerKey = key === "local" ? "local" : key;
        if (avg > SPEAK_THRESHOLD) entry.lastSpeak = now;
        var micOk = peerKey !== "local" || state.callMicOn !== false;
        var active = micOk && now - entry.lastSpeak < SPEAK_HOLD_MS;
        if (!!state.rtcSpeakingPeers[peerKey] !== active) {
          state.rtcSpeakingPeers[peerKey] = active;
          changed = true;
        }
      });
      if (changed) syncAllSlots(state);
      state._speakerRaf = global.requestAnimationFrame(loop);
    }
    state._speakerRaf = global.requestAnimationFrame(loop);
  }

  function markPeerSharing(state, peerId, sharing) {
    state.rtcSharingPeers = state.rtcSharingPeers || {};
    if (sharing) state.rtcSharingPeers[peerId] = true;
    else delete state.rtcSharingPeers[peerId];
    updateSlotDom(peerId, state);
  }

  function rememberRemoteScreen(state, peerId, stream) {
    state.rtcRemoteScreens = state.rtcRemoteScreens || {};
    state.rtcRemoteScreens[peerId] = stream;
  }

  function restoreRemoteMedia(state) {
    var remote = state._remoteStreams || {};
    Object.keys(remote).forEach(function (peerId) {
      var stream = remote[peerId];
      var wrap = document.getElementById("rtc-remote-" + peerId);
      if (!wrap || !stream) return;
      var cam = wrap.querySelector("video.rtc-remote-cam");
      if (cam) cam.srcObject = stream;
      var screens = state.rtcRemoteScreens || {};
      if (screens[peerId]) {
        var rs = wrap.querySelector("video.rtc-remote-screen");
        if (rs) {
          rs.srcObject = screens[peerId];
          rs.style.display = "block";
        }
      }
      updateSlotDom(peerId, state);
    });
  }

  function handleRemoteTrack(state, peerId, track, stream) {
    if (!track) return;
    if (track.kind === "audio" && stream) {
      registerPeerStream(state, peerId, stream);
      ensureSpeakerMonitor(state);
      return;
    }
    if (track.kind !== "video") return;
    var settings = track.getSettings ? track.getSettings() : {};
    var isDisplay =
      !!settings.displaySurface ||
      (track.label && /screen|window|tab|display/i.test(track.label));
    if (isDisplay) {
      markPeerSharing(state, peerId, true);
      if (stream) rememberRemoteScreen(state, peerId, new MediaStream([track]));
      track.onended = function () {
        markPeerSharing(state, peerId, false);
        if (state.rtcRemoteScreens) delete state.rtcRemoteScreens[peerId];
      };
      return;
    }
    markPeerSharing(state, peerId, false);
    track.onended = function () {
      updateSlotDom(peerId, state);
    };
    track.onmute = function () {
      updateSlotDom(peerId, state);
    };
    track.onunmute = function () {
      updateSlotDom(peerId, state);
    };
    if (stream) registerPeerStream(state, peerId, stream);
    updateSlotDom(peerId, state);
  }

  function resetCallMeshUi(state) {
    stopSpeakerMonitor(state);
    state._remoteStreams = {};
    state.rtcRemoteScreens = {};
    state.rtcSpeakingPeers = {};
    state.rtcSharingPeers = {};
  }

  global.KorusUiCallMesh = {
    memberInitials: memberInitials,
    createAvatarNode: createAvatarNode,
    peerDisplayName: peerDisplayName,
    syncAllSlots: syncAllSlots,
    syncLocalStage: syncLocalStage,
    ensureSpeakerMonitor: ensureSpeakerMonitor,
    stopSpeakerMonitor: stopSpeakerMonitor,
    registerPeerStream: registerPeerStream,
    unregisterPeerStream: unregisterPeerStream,
    handleRemoteTrack: handleRemoteTrack,
    markPeerSharing: markPeerSharing,
    restoreRemoteMedia: restoreRemoteMedia,
    resetCallMeshUi: resetCallMeshUi,
    streamHasActiveVideo: streamHasActiveVideo,
  };
})(typeof window !== "undefined" ? window : globalThis);
