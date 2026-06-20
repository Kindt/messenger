(function (global) {
  "use strict";

  var liveKitScriptPromise = null;
  var hlsScriptPromise = null;

  function isLiveSessionChangeEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      (o.change === "created" || o.change === "ended" || o.change === "updated") &&
      typeof o.chat_id === "string" &&
      typeof o.live_session_id === "string"
    );
  }

  function liveSessionRowFromEvent(data) {
    return {
      live_session_id: data.live_session_id,
      chat_id: data.chat_id,
      title: data.title,
      status: data.status,
      mode: data.mode,
      room_name: data.room_name,
      provider: data.provider,
      viewer_count: data.viewer_count,
      max_viewers: data.max_viewers,
      dvr_playlist_url: data.dvr_playlist_url,
      moderation_state: data.moderation_state,
    };
  }

  function liveStreamingEnabled(state) {
    if (state.mediaCaps && state.mediaCaps.live_streaming_enabled) return true;
    return !!(state.platformCaps && state.platformCaps.modules
      && state.platformCaps.modules["addon-live"]
      && state.platformCaps.modules["addon-live"].state === "enabled");
  }

  function activeLiveInChat(state, chatId) {
    if (!chatId || !state.activeLiveSessionByChat) return null;
    var s = state.activeLiveSessionByChat[chatId];
    return s && s.status === "active" ? s : null;
  }

  function setActiveLiveForChat(state, chatId, session) {
    if (!state.activeLiveSessionByChat) state.activeLiveSessionByChat = {};
    if (session && session.live_session_id) {
      state.activeLiveSessionByChat[chatId] = session;
    } else {
      delete state.activeLiveSessionByChat[chatId];
    }
    if (state.selectedId === chatId) {
      state.activeLiveSession = session && session.status === "active" ? session : null;
    }
  }

  function ensureLiveKitClient() {
    if (global.LivekitClient) return Promise.resolve(global.LivekitClient);
    if (liveKitScriptPromise) return liveKitScriptPromise;
    liveKitScriptPromise = new Promise(function (resolve, reject) {
      var s = document.createElement("script");
      s.src = "https://cdn.jsdelivr.net/npm/livekit-client@2/dist/livekit-client.umd.min.js";
      s.async = true;
      s.onload = function () {
        if (global.LivekitClient) resolve(global.LivekitClient);
        else reject(new Error("LiveKit client missing"));
      };
      s.onerror = function () {
        reject(new Error("LiveKit script load failed"));
      };
      document.head.appendChild(s);
    });
    return liveKitScriptPromise;
  }

  function ensureHlsJs() {
    if (global.Hls) return Promise.resolve(global.Hls);
    if (hlsScriptPromise) return hlsScriptPromise;
    hlsScriptPromise = new Promise(function (resolve, reject) {
      var s = document.createElement("script");
      s.src = "https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js";
      s.async = true;
      s.onload = function () {
        if (global.Hls) resolve(global.Hls);
        else reject(new Error("hls.js missing"));
      };
      s.onerror = function () {
        reject(new Error("hls.js load failed"));
      };
      document.head.appendChild(s);
    });
    return hlsScriptPromise;
  }

  function attachHlsPlayer(container, playlistUrl) {
    if (!container || !playlistUrl) return;
    container.innerHTML = "";
    var video = document.createElement("video");
    video.className = "call-video call-live-hls";
    video.controls = true;
    video.playsInline = true;
    video.setAttribute("data-testid", "live-hls-player");
    container.appendChild(video);
    if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = playlistUrl;
      return;
    }
    ensureHlsJs()
      .then(function (Hls) {
        if (Hls.isSupported()) {
          var hls = new Hls();
          hls.loadSource(playlistUrl);
          hls.attachMedia(video);
        } else {
          video.src = playlistUrl;
        }
      })
      .catch(function () {
        video.src = playlistUrl;
      });
  }

  function dvrPlaylistForSession(state, live) {
    if (live && live.dvr_playlist_url) return live.dvr_playlist_url;
    if (
      state.activeLiveSession &&
      live &&
      state.activeLiveSession.live_session_id === live.live_session_id &&
      state.activeLiveSession.dvr_playlist_url
    ) {
      return state.activeLiveSession.dvr_playlist_url;
    }
    return null;
  }

  function disconnectLiveKitRoom(state) {
    if (state.liveKitRoom) {
      try {
        state.liveKitRoom.disconnect();
      } catch (e) {}
      state.liveKitRoom = null;
    }
    state.liveKitRole = null;
  }

  function applyLiveSessionChangeEvent(state, data, hooks) {
    if (data.change === "created" || data.change === "updated") {
      setActiveLiveForChat(state, data.chat_id, liveSessionRowFromEvent(data));
      if (data.change === "created" && hooks && hooks.onCreated) {
        hooks.onCreated(data);
      }
    } else if (data.change === "ended") {
      setActiveLiveForChat(state, data.chat_id, null);
      if (
        state.activeLiveSession &&
        state.activeLiveSession.live_session_id === data.live_session_id
      ) {
        disconnectLiveKitRoom(state);
        state.activeLiveSession = null;
      }
      if (hooks && hooks.onEnded) hooks.onEnded(data);
    }
    if (state.chatLiveSessions && state.selectedId === data.chat_id) {
      var found = false;
      state.chatLiveSessions = state.chatLiveSessions.map(function (s) {
        if (s.live_session_id === data.live_session_id) {
          found = true;
          return liveSessionRowFromEvent(data);
        }
        return s;
      });
      if (!found && data.status === "active") {
        state.chatLiveSessions.unshift(liveSessionRowFromEvent(data));
      }
      state.chatLiveSessions = state.chatLiveSessions.filter(function (s) {
        return s.status === "active";
      });
    }
  }

  function loadChatLiveReplays(state, apiJson) {
    if (!state.selectedId || !state.tokens) {
      state.chatLiveReplays = [];
      return Promise.resolve([]);
    }
    return apiJson("/chats/" + state.selectedId + "/live-sessions?active_only=false", {
      method: "GET",
    })
      .then(function (rows) {
        state.chatLiveReplays = (Array.isArray(rows) ? rows : []).filter(function (s) {
          return s && s.dvr_playlist_url && s.status === "ended";
        });
        return state.chatLiveReplays;
      })
      .catch(function () {
        state.chatLiveReplays = [];
        return [];
      });
  }

  function loadChatLiveSessions(state, apiJson) {
    if (!state.selectedId || !state.tokens) {
      state.chatLiveSessions = null;
      state.chatLiveReplays = [];
      return Promise.resolve([]);
    }
    return apiJson("/chats/" + state.selectedId + "/live-sessions?active_only=true", {
      method: "GET",
    })
      .then(function (rows) {
        state.chatLiveSessions = Array.isArray(rows) ? rows : [];
        var live = state.chatLiveSessions.length ? state.chatLiveSessions[0] : null;
        setActiveLiveForChat(state, state.selectedId, live);
        return loadChatLiveReplays(state, apiJson);
      })
      .catch(function () {
        state.chatLiveSessions = [];
        return [];
      });
  }

  function joinLiveSession(state, apiJson, L, session, render) {
    if (!session || !session.live_session_id) return Promise.resolve();
    state.liveSessionBusy = true;
    render();
    return apiJson("/live-sessions/" + session.live_session_id + "/join", { method: "POST" })
      .then(function (join) {
        state.activeLiveSession = liveSessionRowFromEvent(
          Object.assign({}, session, {
            viewer_count: join.viewer_count,
            max_viewers: join.max_viewers,
          })
        );
        state.liveKitRole = join.role;
        return ensureLiveKitClient().then(function (LivekitClient) {
          disconnectLiveKitRoom(state);
          var room = new LivekitClient.Room({ adaptiveStream: true, dynacast: true });
          state.liveKitRoom = room;
          var url = join.livekit_url || (state.mediaCaps && state.mediaCaps.livekit_url);
          return room.connect(url, join.access_token).then(function () {
            if (join.role === "host" || join.role === "cohost") {
              return room.localParticipant
                .setCameraEnabled(true)
                .then(function () {
                  return room.localParticipant.setMicrophoneEnabled(true);
                });
            }
          });
        });
      })
      .catch(function (e) {
        state.error = (e && e.message) || L("live.joinFailed");
        disconnectLiveKitRoom(state);
      })
      .finally(function () {
        state.liveSessionBusy = false;
        render();
      });
  }

  function leaveLiveSession(state, apiJson, L, render) {
    if (!state.activeLiveSession || !state.activeLiveSession.live_session_id) {
      disconnectLiveKitRoom(state);
      state.activeLiveSession = null;
      render();
      return Promise.resolve();
    }
    var id = state.activeLiveSession.live_session_id;
    state.liveSessionBusy = true;
    render();
    return apiJson("/live-sessions/" + id + "/leave", { method: "POST" })
      .catch(function () {})
      .finally(function () {
        disconnectLiveKitRoom(state);
        state.activeLiveSession = null;
        state.liveSessionBusy = false;
        render();
      });
  }

  function endLiveSession(state, apiJson, L, render) {
    if (!state.activeLiveSession || !state.activeLiveSession.live_session_id) return Promise.resolve();
    var id = state.activeLiveSession.live_session_id;
    state.liveSessionBusy = true;
    render();
    return apiJson("/live-sessions/" + id + "/end", { method: "POST" })
      .then(function () {
        disconnectLiveKitRoom(state);
        state.activeLiveSession = null;
        state.statusMessage = L("live.ended");
      })
      .catch(function (e) {
        state.error = (e && e.message) || L("live.endFailed");
      })
      .finally(function () {
        state.liveSessionBusy = false;
        render();
      });
  }

  function createLiveSession(state, apiJson, L, render) {
    if (!state.selectedId) {
      state.error = L("live.needsChat");
      render();
      return Promise.resolve();
    }
    var title = window.prompt(L("live.titlePrompt")) || "";
    state.liveSessionBusy = true;
    render();
    return apiJson("/chats/" + state.selectedId + "/live-sessions", {
      method: "POST",
      jsonBody: { title: title.trim() },
    })
      .then(function (created) {
        setActiveLiveForChat(state, state.selectedId, created);
        state.statusMessage = L("live.created");
        return joinLiveSession(state, apiJson, L, created, render);
      })
      .catch(function (e) {
        state.error = (e && e.message) || L("live.createFailed");
        state.liveSessionBusy = false;
        render();
      });
  }

  function liveDisplayTitle(live) {
    if (!live) return "";
    var t = live.title;
    if (typeof t === "string" && t.trim()) return t.trim();
    return live.room_name ? String(live.room_name) : "";
  }

  function renderLiveSection(panel, state, deps) {
    var el = deps.el;
    var iconBtn = deps.iconBtn;
    var L = deps.L;
    var apiJson = deps.apiJson;
    var render = deps.render;

    if (!state.tokens) return;

    var sec = el("div", "call-live-sessions");
    sec.setAttribute("data-testid", "live-session-section");
    var head = el("div", "call-conferences-head");
    head.appendChild(el("div", "call-conferences-title", L("live.sectionTitle")));
    head.appendChild(
      iconBtn("↻", L("live.refreshList"), {
        disabled: state.liveSessionBusy || state.busy,
        onClick: function () {
          loadChatLiveSessions(state, apiJson).then(render).catch(render);
        },
      })
    );
    sec.appendChild(head);
    sec.appendChild(el("p", "call-hint", L("live.hint")));

    if (!liveStreamingEnabled(state)) {
      sec.appendChild(el("p", "call-conf-empty", L("live.notConfigured")));
      panel.appendChild(sec);
      return;
    }

    var actions = el("div", "call-conferences-actions");
    if (state.selectedId && state.selectedId !== state.savedChatId) {
      actions.appendChild(
        iconBtn("📡", state.liveSessionBusy ? L("live.starting") : L("live.start"), {
          primary: true,
          testId: "live-start-button",
          disabled: state.liveSessionBusy || state.busy,
          onClick: function () {
            createLiveSession(state, apiJson, L, render);
          },
        })
      );
    }
    sec.appendChild(actions);

    var live = activeLiveInChat(state, state.selectedId);
    if (live) {
      var row = el("div", "call-conf-row");
      var label =
        liveDisplayTitle(live) +
        " · " +
        (live.viewer_count != null ? live.viewer_count : 0) +
        "/" +
        (live.max_viewers != null ? live.max_viewers : 200) +
        (state.activeLiveSession &&
        state.activeLiveSession.live_session_id === live.live_session_id
          ? " · " + L("live.live")
          : "");
      row.appendChild(el("span", "call-conf-label", label));
      if (
        !state.activeLiveSession ||
        state.activeLiveSession.live_session_id !== live.live_session_id
      ) {
        row.appendChild(
          iconBtn("▶", L("live.join"), {
            testId: "live-join-button",
            disabled: state.liveSessionBusy,
            onClick: function () {
              joinLiveSession(state, apiJson, L, live, render);
            },
          })
        );
      }
      sec.appendChild(row);
    } else {
      sec.appendChild(el("p", "call-conf-empty", L("live.noneActive")));
    }

    var dvrUrl = state.liveReplayUrl || dvrPlaylistForSession(state, live);
    if (dvrUrl) {
      var hlsStage = el("div", "call-live-stage call-live-hls-stage");
      hlsStage.setAttribute("data-testid", "live-hls-stage");
      hlsStage.appendChild(el("p", "call-hint", L("live.hlsReplay")));
      if (state.liveReplayUrl) {
        hlsStage.appendChild(
          iconBtn("✕", L("live.leave"), {
            testId: "live-replay-close",
            onClick: function () {
              state.liveReplayUrl = null;
              render();
            },
          })
        );
      }
      var hlsVideos = el("div", "call-live-videos");
      hlsStage.appendChild(hlsVideos);
      sec.appendChild(hlsStage);
      setTimeout(function () {
        attachHlsPlayer(hlsVideos, dvrUrl);
      }, 0);
    }

    var replays = state.chatLiveReplays || [];
    if (replays.length && !state.liveReplayUrl) {
      var repBlock = el("div", "call-live-replays");
      repBlock.appendChild(el("div", "call-conferences-title", L("live.replaySection")));
      replays.forEach(function (session) {
        var row = el("div", "call-conf-row");
        row.appendChild(
          el("span", "call-conf-label", liveDisplayTitle(session) || session.live_session_id)
        );
        row.appendChild(
          iconBtn("▶", L("live.watchReplay"), {
            testId: "live-replay-open-" + session.live_session_id,
            onClick: function () {
              state.liveReplayUrl = session.dvr_playlist_url;
              render();
            },
          })
        );
        repBlock.appendChild(row);
      });
      sec.appendChild(repBlock);
    }

    if (state.activeLiveSession && state.liveKitRoom) {
      var stage = el("div", "call-live-stage");
      stage.setAttribute("data-testid", "live-stage");
      var videos = el("div", "call-live-videos");
      videos.id = "liveKitVideos";
      stage.appendChild(videos);
      sec.appendChild(stage);

      var bar = el("div", "call-toolbar");
      bar.appendChild(
        iconBtn("🚪", L("live.leave"), {
          onClick: function () {
            leaveLiveSession(state, apiJson, L, render);
          },
        })
      );
      if (state.liveKitRole === "host") {
        bar.appendChild(
          iconBtn("⏹", L("live.endAll"), {
            disabled: state.liveSessionBusy,
            onClick: function () {
              endLiveSession(state, apiJson, L, render);
            },
          })
        );
      }
      sec.appendChild(bar);

      setTimeout(function () {
        attachLiveKitVideos(state, videos);
      }, 0);
    }

    panel.appendChild(sec);
  }

  function attachLiveKitVideos(state, container) {
    if (!state.liveKitRoom || !container) return;
    container.innerHTML = "";
    var room = state.liveKitRoom;
    function addTrack(track, participant) {
      if (track.kind !== "video" && track.kind !== "audio") return;
      var elNode = track.attach();
      elNode.className = "call-video call-live-track";
      elNode.setAttribute(
        "data-testid",
        "live-track-" + (participant.isLocal ? "local" : "remote")
      );
      if (track.kind === "audio") elNode.autoplay = true;
      container.appendChild(elNode);
    }
    room.remoteParticipants.forEach(function (p) {
      p.trackPublications.forEach(function (pub) {
        if (pub.track) addTrack(pub.track, p);
      });
    });
    room.localParticipant.trackPublications.forEach(function (pub) {
      if (pub.track) addTrack(pub.track, room.localParticipant);
    });
    if (!room.__korusLiveHandlers) {
      room.__korusLiveHandlers = true;
      room.on("trackSubscribed", function (track, _pub, participant) {
        addTrack(track, participant);
      });
      room.on("trackUnsubscribed", function (track) {
        track.detach().forEach(function (node) {
          if (node.parentNode) node.parentNode.removeChild(node);
        });
      });
    }
  }

  global.KorusUiLiveSession = {
    isLiveSessionChangeEvent: isLiveSessionChangeEvent,
    liveSessionRowFromEvent: liveSessionRowFromEvent,
    applyLiveSessionChangeEvent: applyLiveSessionChangeEvent,
    loadChatLiveSessions: loadChatLiveSessions,
    loadChatLiveReplays: loadChatLiveReplays,
    renderLiveSection: renderLiveSection,
    disconnectLiveKitRoom: disconnectLiveKitRoom,
    liveStreamingEnabled: liveStreamingEnabled,
  };
})(typeof window !== "undefined" ? window : globalThis);
