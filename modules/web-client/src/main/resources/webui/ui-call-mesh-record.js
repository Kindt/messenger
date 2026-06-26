/**
 * Mesh call recording: audit (always) + user clip (button start/stop).
 * Audio mix + optional video grid composite from call panel <video> elements.
 */
(function (global) {
  "use strict";

  var auditRecorder = null;
  var auditChunks = [];
  var auditStartedAt = 0;
  var userRecorder = null;
  var userChunks = [];
  var userStartedAt = 0;
  var mixAudioCtx = null;
  var mixDest = null;
  var mixSources = [];
  var mixedTrackIds = Object.create(null);
  var compositeCanvas = null;
  var compositeCtx = null;
  var compositeTimer = null;
  var compositeStream = null;

  function meshPath(ctx, suffix) {
    return "/chats/" + ctx.state.selectedId + "/mesh-calls" + suffix;
  }

  function isVideoCall(state) {
    return state.callMediaMode === "video" || !!state.callCamOn;
  }

  function collectAudioTracks(state) {
    var tracks = [];
    if (state.callStream) {
      state.callStream.getAudioTracks().forEach(function (t) {
        if (t.readyState === "live") tracks.push(t);
      });
    }
    Object.keys(state.rtcPeers || {}).forEach(function (pid) {
      var pc = state.rtcPeers[pid];
      if (!pc || !pc.getReceivers) return;
      pc.getReceivers().forEach(function (rx) {
        if (rx.track && rx.track.kind === "audio" && rx.track.readyState === "live") {
          tracks.push(rx.track);
        }
      });
    });
    return tracks;
  }

  function collectVideoElements(state) {
    var out = [];
    var local = document.getElementById("callLocalVideo");
    if (local && local.srcObject && local.readyState >= 2 && !local.classList.contains("is-hidden")) {
      out.push(local);
    }
    (state.rtcPeerIds || []).forEach(function (pid) {
      var wrap = document.getElementById("rtc-remote-" + pid);
      if (!wrap) return;
      var v = wrap.querySelector("video.rtc-remote-cam");
      if (v && v.srcObject && v.readyState >= 2 && !v.classList.contains("is-hidden")) {
        out.push(v);
      }
    });
    return out;
  }

  function ensureMixGraph(state) {
    if (!mixAudioCtx || mixAudioCtx.state === "closed") {
      mixAudioCtx = new AudioContext();
      mixDest = mixAudioCtx.createMediaStreamDestination();
      mixSources = [];
      mixedTrackIds = Object.create(null);
    }
    collectAudioTracks(state).forEach(function (track) {
      if (mixedTrackIds[track.id]) return;
      mixedTrackIds[track.id] = true;
      try {
        var src = mixAudioCtx.createMediaStreamSource(new MediaStream([track]));
        src.connect(mixDest);
        mixSources.push(src);
      } catch (e) {}
    });
    return mixDest.stream;
  }

  function ensureCompositeVideo(state) {
    if (!isVideoCall(state)) return null;
    var videos = collectVideoElements(state);
    if (!videos.length) return null;
    if (!compositeCanvas) {
      compositeCanvas = document.createElement("canvas");
      compositeCanvas.width = 854;
      compositeCanvas.height = 480;
      compositeCtx = compositeCanvas.getContext("2d");
      compositeStream = compositeCanvas.captureStream(12);
    }
    if (!compositeTimer) {
      compositeTimer = setInterval(function () {
        if (!compositeCtx || !compositeCanvas) return;
        var els = collectVideoElements(state);
        compositeCtx.fillStyle = "#0f1419";
        compositeCtx.fillRect(0, 0, compositeCanvas.width, compositeCanvas.height);
        if (!els.length) return;
        var cols = Math.ceil(Math.sqrt(els.length));
        var rows = Math.ceil(els.length / cols);
        var cw = compositeCanvas.width / cols;
        var ch = compositeCanvas.height / rows;
        els.forEach(function (el, i) {
          var col = i % cols;
          var row = Math.floor(i / cols);
          try {
            compositeCtx.drawImage(el, col * cw + 2, row * ch + 2, cw - 4, ch - 4);
          } catch (e) {}
        });
      }, 84);
    }
    return compositeStream;
  }

  function buildRecordStream(state) {
    var audioStream = ensureMixGraph(state);
    var videoStream = ensureCompositeVideo(state);
    var tracks = [];
    if (audioStream) {
      audioStream.getAudioTracks().forEach(function (t) {
        tracks.push(t);
      });
    }
    if (videoStream) {
      videoStream.getVideoTracks().forEach(function (t) {
        tracks.push(t);
      });
    }
    if (!tracks.length) return null;
    return new MediaStream(tracks);
  }

  function stopMixGraph() {
    mixSources.forEach(function (s) {
      try {
        s.disconnect();
      } catch (e) {}
    });
    mixSources = [];
    mixedTrackIds = Object.create(null);
    mixDest = null;
    if (mixAudioCtx) {
      mixAudioCtx.close().catch(function () {});
      mixAudioCtx = null;
    }
    if (compositeTimer) {
      clearInterval(compositeTimer);
      compositeTimer = null;
    }
    compositeCanvas = null;
    compositeCtx = null;
    compositeStream = null;
  }

  function pickMime(hasVideo) {
    if (hasVideo) {
      if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported("video/webm;codecs=vp8,opus")) {
        return "video/webm;codecs=vp8,opus";
      }
      if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported("video/webm")) {
        return "video/webm";
      }
    }
    if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported("audio/webm;codecs=opus")) {
      return "audio/webm;codecs=opus";
    }
    return "audio/webm";
  }

  function startRecorder(stream) {
    if (!stream || typeof MediaRecorder === "undefined") return null;
    var hasVideo = stream.getVideoTracks().length > 0;
    var mime = pickMime(hasVideo);
    try {
      return new MediaRecorder(stream, { mimeType: mime });
    } catch (e) {
      return new MediaRecorder(stream);
    }
  }

  function stopRecorder(rec, chunks) {
    return new Promise(function (resolve) {
      if (!rec || rec.state === "inactive") {
        resolve(null);
        return;
      }
      rec.onstop = function () {
        var blob = chunks.length ? new Blob(chunks, { type: rec.mimeType || "audio/webm" }) : null;
        resolve(blob);
      };
      try {
        rec.stop();
      } catch (e) {
        resolve(null);
      }
    });
  }

  async function uploadBlob(ctx, blob, prefix) {
    if (!blob || blob.size < 1) return null;
    var ext = (blob.type || "").indexOf("video") >= 0 ? ".webm" : ".webm";
    var file = new File([blob], prefix + "-" + Date.now() + ext, {
      type: blob.type || "audio/webm",
    });
    var parsed = await ctx.uploadChatFile(file);
    return parsed && (parsed.id || parsed.file_id);
  }

  async function completeRecording(ctx, recordingId, fileId, durationMs) {
    if (!ctx.state.meshCallSessionId || !recordingId || !fileId) return;
    await ctx.apiJson(
      meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/recordings/" + recordingId + "/complete"),
      {
        method: "POST",
        jsonBody: { file_id: fileId, duration_ms: durationMs },
      }
    );
  }

  async function finishRecorder(ctx, rec, chunks, recordingId, startedAt, prefix) {
    var blob = await stopRecorder(rec, chunks);
    if (!blob || !recordingId) return;
    try {
      var fileId = await uploadBlob(ctx, blob, prefix);
      if (fileId) {
        await completeRecording(ctx, recordingId, fileId, Date.now() - (startedAt || Date.now()));
      }
    } catch (e) {
      ctx.state.error = (e && e.message) || ctx.L("ui.call.recordFailed");
    }
  }

  async function startAuditRecording(ctx) {
    if (ctx.state.meshCompositeRecording) return;
    if (!ctx.state.meshAuditRecordingId || auditRecorder) return;
    var stream = buildRecordStream(ctx.state);
    if (!stream || !stream.getAudioTracks().length) return;
    auditChunks = [];
    auditStartedAt = Date.now();
    auditRecorder = startRecorder(stream);
    if (!auditRecorder) return;
    auditRecorder.ondataavailable = function (ev) {
      if (ev.data && ev.data.size) auditChunks.push(ev.data);
    };
    auditRecorder.start(1000);
  }

  function refreshMixIfRecording(ctx) {
    if (!auditRecorder && !userRecorder) return;
    ensureMixGraph(ctx.state);
    if (isVideoCall(ctx.state)) {
      ensureCompositeVideo(ctx.state);
    }
  }

  async function startUserRecording(ctx) {
    if (ctx.state.meshUserRecordingActive) return;
    if (!ctx.state.meshCallSessionId) return;
    var data = await ctx.apiJson(
      meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/recordings"),
      { method: "POST", jsonBody: { kind: "user" } }
    );
    ctx.state.meshUserRecordingId = data && (data.recording_id || data.id);
    if (ctx.state.meshCompositeRecording) {
      ctx.state.meshUserRecordingActive = true;
      ctx.state.phase5Toast = ctx.L("ui.call.recordStarted");
      return;
    }
    var stream = buildRecordStream(ctx.state);
    if (!stream) throw new Error("no media");
    userChunks = [];
    userStartedAt = Date.now();
    userRecorder = startRecorder(stream);
    if (!userRecorder) throw new Error("MediaRecorder unsupported");
    userRecorder.ondataavailable = function (ev) {
      if (ev.data && ev.data.size) userChunks.push(ev.data);
    };
    userRecorder.start(1000);
    ctx.state.meshUserRecordingActive = true;
    ctx.state.phase5Toast = ctx.L("ui.call.recordStarted");
  }

  async function stopUserRecording(ctx) {
    if (!ctx.state.meshUserRecordingActive) return;
    ctx.state.meshUserRecordingActive = false;
    var recId = ctx.state.meshUserRecordingId;
    ctx.state.meshUserRecordingId = null;
    if (ctx.state.meshCompositeRecording && recId) {
      await ctx.apiJson(
        meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/recordings/" + recId + "/stop"),
        { method: "POST" }
      );
      ctx.state.phase5Toast = ctx.L("ui.call.recordCompleted");
      return;
    }
    var rec = userRecorder;
    var chunks = userChunks;
    var meshRecId = recId;
    userRecorder = null;
    userChunks = [];
    await finishRecorder(ctx, rec, chunks, meshRecId, userStartedAt, "mesh-user");
    ctx.state.phase5Toast = ctx.L("ui.call.recordCompleted");
  }

  async function finishAll(ctx) {
    if (ctx.state.meshCompositeRecording) {
      if (ctx.state.meshUserRecordingActive) {
        await stopUserRecording(ctx);
      }
      if (ctx.state.meshCallSessionId) {
        try {
          await ctx.apiJson(meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/end"), {
            method: "POST",
          });
        } catch (e) {}
      }
      ctx.state.meshCallSessionId = null;
      ctx.state.meshAuditRecordingId = null;
      ctx.state.meshCompositeRecording = false;
      return;
    }
    var auditRec = auditRecorder;
    var auditCh = auditChunks;
    var auditId = ctx.state.meshAuditRecordingId;
    var auditStart = auditStartedAt;
    auditRecorder = null;
    auditChunks = [];

    if (ctx.state.meshUserRecordingActive) {
      await stopUserRecording(ctx);
    }

    await finishRecorder(ctx, auditRec, auditCh, auditId, auditStart, "mesh-audit");
    stopMixGraph();

    if (ctx.state.meshCallSessionId) {
      try {
        await ctx.apiJson(meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/end"), {
          method: "POST",
        });
      } catch (e) {}
    }
    ctx.state.meshCallSessionId = null;
    ctx.state.meshAuditRecordingId = null;
  }

  async function listUserRecordings(ctx) {
    if (!ctx.state.meshCallSessionId) return [];
    var rows = await ctx.apiJson(
      meshPath(ctx, "/sessions/" + ctx.state.meshCallSessionId + "/recordings"),
      { method: "GET" }
    );
    return (rows || []).filter(function (r) {
      return (r.kind || "") === "user";
    });
  }

  global.KorusUiCallMeshRecord = {
    startAuditRecording: startAuditRecording,
    refreshMixIfRecording: refreshMixIfRecording,
    startUserRecording: startUserRecording,
    stopUserRecording: stopUserRecording,
    finishAll: finishAll,
    listUserRecordings: listUserRecordings,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
