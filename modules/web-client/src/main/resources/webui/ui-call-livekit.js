(function (global) {
  "use strict";

  function groupCallSfuEnabled(state) {
    return !!(state.mediaCaps && state.mediaCaps.group_call_sfu_enabled);
  }

  function ensureLiveKitClient() {
    if (global.LivekitClient) return Promise.resolve(global.LivekitClient);
    if (global.KorusUiLiveSession && global.KorusUiLiveSession.ensureLiveKitClient) {
      return global.KorusUiLiveSession.ensureLiveKitClient();
    }
    return new Promise(function (resolve, reject) {
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
  }

  function disconnectRoom(state) {
    if (state.liveKitCallRoom) {
      try {
        state.liveKitCallRoom.disconnect();
      } catch (e) {}
      state.liveKitCallRoom = null;
    }
    state.liveKitCallConnected = false;
  }

  async function joinGroupCall(state, apiJson) {
    if (!state.selectedId || !state.tokens) return;
    disconnectRoom(state);
    var join = await apiJson("/chats/" + state.selectedId + "/calls/livekit/join", { method: "POST" });
    var LK = await ensureLiveKitClient();
    var room = new LK.Room({ adaptiveStream: true, dynacast: true });
    state.liveKitCallRoom = room;
    room.on(LK.RoomEvent.TrackSubscribed, function (track) {
      var el = track.attach();
      el.className = "call-video call-livekit-remote";
      el.setAttribute("data-testid", "livekit-remote-track");
      var grid = document.getElementById("livekitCallVideos");
      if (grid) grid.appendChild(el);
    });
    room.on(LK.RoomEvent.TrackUnsubscribed, function (track) {
      track.detach().forEach(function (el) {
        el.remove();
      });
    });
    await room.connect(join.livekit_url, join.access_token);
    state.liveKitCallConnected = true;
    try {
      await room.localParticipant.setMicrophoneEnabled(!!state.callMicOn);
      await room.localParticipant.setCameraEnabled(!!state.callCamOn);
    } catch (e) {
      /* mic/cam optional */
    }
  }

  function renderLiveKitSection(panel, state, helpers) {
    if (!groupCallSfuEnabled(state)) return;
    var el = helpers.el;
    var iconBtn = helpers.iconBtn;
    var L = helpers.L;
    var sec = el("div", "call-livekit");
    sec.setAttribute("data-testid", "livekit-call-section");
    sec.appendChild(el("p", "call-hint", L("conference.livekitSfuHint")));
    var grid = el("div", "call-livekit-grid");
    grid.id = "livekitCallVideos";
    sec.appendChild(grid);
    var actions = el("div", "call-conferences-actions");
    actions.appendChild(
      iconBtn("▶", L("conference.livekitJoin"), {
        primary: state.callMode === "livekit",
        testId: "livekit-call-join",
        disabled: state.callPanelToggleBusy || !state.selectedId,
        onClick: function () {
          helpers.switchCallMode("livekit");
        },
      })
    );
    actions.appendChild(
      iconBtn("⏹", L("conference.livekitLeave"), {
        disabled: !state.liveKitCallConnected,
        onClick: function () {
          disconnectRoom(state);
          helpers.render();
        },
      })
    );
    sec.appendChild(actions);
    panel.appendChild(sec);
  }

  global.KorusUiCallLivekit = {
    groupCallSfuEnabled: groupCallSfuEnabled,
    joinGroupCall: joinGroupCall,
    disconnectRoom: disconnectRoom,
    renderLiveKitSection: renderLiveKitSection,
  };
})(typeof window !== "undefined" ? window : globalThis);
