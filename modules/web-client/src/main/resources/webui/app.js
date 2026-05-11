(function () {
  "use strict";

  const TOKEN_KEY = "korus_web_tokens";

  const state = {
    tokens: null,
    authTab: "login",
    error: null,
    busy: false,
    chats: [],
    selectedId: null,
    messages: [],
    ws: null,
    wsState: "off",
    sidebarSearch: "",
    callPanelOpen: false,
    callStream: null,
    callScreenStream: null,
    callThumbTimer: null,
    callCamOn: true,
    callMicOn: true,
    rtcPeerIds: [],
    rtcPeers: {},
  };

  function apiRoot() {
    return "/api/v1";
  }

  function wsBaseUrl() {
    var cfg = window.__WEB_CLIENT__;
    if (cfg && cfg.wsUrl) return String(cfg.wsUrl).replace(/\/$/, "");
    var p = location.protocol === "https:" ? "wss:" : "ws:";
    return p + "//" + location.host + "/ws";
  }

  /** ICE from /web-client-env.js (WEB_CLIENT_RTC_ICE_SERVERS) or default STUN. */
  function getRtcIceServers() {
    var cfg = window.__WEB_CLIENT__;
    if (cfg && cfg.iceServersJson) {
      try {
        var arr = JSON.parse(cfg.iceServersJson);
        if (Array.isArray(arr) && arr.length > 0) {
          return arr;
        }
      } catch (e) {}
    }
    return [{ urls: "stun:stun.l.google.com:19302" }];
  }

  async function apiJson(path, opts) {
    opts = opts || {};
    var headers = { Accept: "application/json" };
    if (state.tokens && state.tokens.access_token) {
      headers.Authorization = "Bearer " + state.tokens.access_token;
    }
    if (opts.jsonBody !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    var url = apiRoot() + (path.startsWith("/") ? path : "/" + path);
    var init = {
      method: opts.method || "GET",
      headers: headers,
    };
    if (opts.jsonBody !== undefined) {
      init.body = JSON.stringify(opts.jsonBody);
    }
    var res = await fetch(url, init);
    var text = await res.text();
    var parsed = null;
    if (text) {
      try {
        parsed = JSON.parse(text);
      } catch (e) {
        parsed = text;
      }
    }
    if (!res.ok) {
      var msg =
        parsed && typeof parsed === "object" && parsed.message
          ? String(parsed.message)
          : res.statusText;
      throw new Error(msg || "Request failed");
    }
    return parsed;
  }

  function loadTokens() {
    try {
      var raw = localStorage.getItem(TOKEN_KEY);
      if (!raw) return null;
      var v = JSON.parse(raw);
      if (!v.access_token || !v.refresh_token) return null;
      return v;
    } catch (e) {
      return null;
    }
  }

  function saveTokens(t) {
    var row = Object.assign({}, t, { stored_at_ms: Date.now() });
    localStorage.setItem(TOKEN_KEY, JSON.stringify(row));
    state.tokens = row;
  }

  function clearTokens() {
    localStorage.removeItem(TOKEN_KEY);
    state.tokens = null;
  }

  function jwtSub(token) {
    try {
      var part = token.split(".")[1];
      if (!part) return null;
      var b64 = part.replace(/-/g, "+").replace(/_/g, "/");
      var json = JSON.parse(atob(b64));
      return typeof json.sub === "string" ? json.sub : null;
    } catch (e) {
      return null;
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /**
   * Subset of Markdown (bold, italic, inline code, links, fenced code, line breaks).
   * HTML in source is escaped; only safe link schemes.
   */
  function safeMarkdown(src) {
    if (!src) return "";
    var text = String(src);
    var parts = text.split(/```/);
    var out = [];
    for (var i = 0; i < parts.length; i++) {
      var seg = parts[i];
      if (i % 2 === 1) {
        out.push("<pre><code>" + escapeHtml(seg.replace(/^\w*\r?\n/, "")) + "</code></pre>");
      } else {
        out.push(inlineMarkdown(escapeHtml(seg)));
      }
    }
    return out.join("");
  }

  function inlineMarkdown(escaped) {
    var s = escaped.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
    s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    s = s.replace(/(^|\W)\*([^*\n]+)\*(?=\W|$)/g, "$1<em>$2</em>");
    s = s.replace(
      /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    );
    s = s.replace(/\n/g, "<br>");
    return s;
  }

  function sendRtcHangups() {
    try {
      if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.selectedId) return;
      Object.keys(state.rtcPeers).forEach(function (pid) {
        state.ws.send(
          JSON.stringify({
            type: "rtc_signal",
            chatId: state.selectedId,
            payload: { kind: "hangup", targetUserId: pid },
          })
        );
      });
    } catch (e) {}
  }

  function teardownPeer(peerId) {
    var pc = state.rtcPeers[peerId];
    if (pc) {
      try {
        pc.close();
      } catch (e) {}
      delete state.rtcPeers[peerId];
    }
    var wrap = document.getElementById("rtc-remote-" + peerId);
    if (wrap && wrap.parentNode) wrap.parentNode.removeChild(wrap);
  }

  function rtcHangupAll() {
    sendRtcHangups();
    Object.keys(state.rtcPeers).forEach(function (pid) {
      try {
        state.rtcPeers[pid].close();
      } catch (e) {}
      delete state.rtcPeers[pid];
      var wrap = document.getElementById("rtc-remote-" + pid);
      if (wrap && wrap.parentNode) wrap.parentNode.removeChild(wrap);
    });
    state.rtcPeers = {};
    state.rtcPeerIds = [];
  }

  function sendRtcSignal(payload) {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.selectedId) return;
    state.ws.send(
      JSON.stringify({
        type: "rtc_signal",
        chatId: state.selectedId,
        payload: payload,
      })
    );
  }

  function stopCallMedia() {
    rtcHangupAll();
    if (state.callThumbTimer) {
      clearInterval(state.callThumbTimer);
      state.callThumbTimer = null;
    }
    if (state.callStream) {
      state.callStream.getTracks().forEach(function (t) {
        t.stop();
      });
      state.callStream = null;
    }
    if (state.callScreenStream) {
      state.callScreenStream.getTracks().forEach(function (t) {
        t.stop();
      });
      state.callScreenStream = null;
    }
    state.callCamOn = true;
    state.callMicOn = true;
  }

  async function ensureCallStream() {
    if (state.callStream) return;
    try {
      state.callStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user" },
        audio: true,
      });
    } catch (e) {
      state.error = "Камера/микрофон: " + (e.message || "доступ запрещён");
      throw e;
    }
  }

  async function loadRtcPeerIds() {
    state.rtcPeerIds = [];
    if (!state.selectedId || !state.tokens) return;
    try {
      var rows = await apiJson("/chats/" + state.selectedId + "/members", { method: "GET" });
      var me = jwtSub(state.tokens.access_token);
      state.rtcPeerIds = rows
        .filter(function (r) {
          return r.user_id !== me;
        })
        .map(function (r) {
          return r.user_id;
        });
    } catch (e) {
      state.error = (e && e.message) || "Не удалось загрузить участников для звонка";
    }
  }

  function getOrCreatePeerConnection(peerId) {
    if (state.rtcPeers[peerId]) return state.rtcPeers[peerId];
    var pc = new RTCPeerConnection({
      iceServers: getRtcIceServers(),
    });
    state.rtcPeers[peerId] = pc;
    if (state.callStream) {
      state.callStream.getTracks().forEach(function (t) {
        pc.addTrack(t, state.callStream);
      });
    }
    pc.onicecandidate = function (ev) {
      if (ev.candidate) {
        sendRtcSignal({
          kind: "candidate",
          targetUserId: peerId,
          candidate: ev.candidate.toJSON(),
        });
      }
    };
    pc.ontrack = function (ev) {
      var wrap = document.getElementById("rtc-remote-" + peerId);
      if (!wrap) return;
      var video = wrap.querySelector("video");
      if (video && ev.streams[0]) {
        video.srcObject = ev.streams[0];
      }
    };
    return pc;
  }

  async function createOfferTo(peerId) {
    var pc = getOrCreatePeerConnection(peerId);
    var offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendRtcSignal({ kind: "offer", targetUserId: peerId, sdp: offer.sdp });
  }

  function beginRtcMesh() {
    var me = jwtSub(state.tokens && state.tokens.access_token);
    if (!me || !state.callPanelOpen) return;
    state.rtcPeerIds.forEach(function (pid) {
      if (me < pid) {
        createOfferTo(pid).catch(function () {});
      }
    });
  }

  async function handleRtcEnvelope(env) {
    if (!env || env.type !== "rtc_signal" || !env.payload) return;
    if (env.chatId !== state.selectedId) return;
    var from = env.fromUserId;
    var me = jwtSub(state.tokens && state.tokens.access_token);
    if (!from || !me || from === me) return;
    var p = env.payload;
    if (p.targetUserId && p.targetUserId !== me) return;
    try {
      if (p.kind === "hangup") {
        teardownPeer(from);
        render();
        return;
      }
      if (p.kind === "offer" && p.sdp) {
        var pcO = getOrCreatePeerConnection(from);
        await pcO.setRemoteDescription({ type: "offer", sdp: p.sdp });
        var ans = await pcO.createAnswer();
        await pcO.setLocalDescription(ans);
        sendRtcSignal({ kind: "answer", targetUserId: from, sdp: ans.sdp });
        return;
      }
      if (p.kind === "answer" && p.sdp) {
        var pcA = state.rtcPeers[from];
        if (pcA) await pcA.setRemoteDescription({ type: "answer", sdp: p.sdp });
        return;
      }
      if (p.kind === "candidate" && p.candidate) {
        var pcC = state.rtcPeers[from];
        if (pcC) await pcC.addIceCandidate(p.candidate);
      }
    } catch (e) {
      state.error = (e && e.message) || "WebRTC";
      render();
    }
  }

  async function toggleCallPanel() {
    state.callPanelOpen = !state.callPanelOpen;
    if (!state.callPanelOpen) {
      stopCallMedia();
      render();
      return;
    }
    try {
      await ensureCallStream();
      await loadRtcPeerIds();
      startThumbCapture();
      render();
      attachLocalVideo();
      setTimeout(function () {
        beginRtcMesh();
      }, 120);
    } catch (e) {
      state.callPanelOpen = false;
      render();
    }
  }

  function attachLocalVideo() {
    var v = document.getElementById("callLocalVideo");
    if (v && state.callStream) {
      v.srcObject = state.callStream;
    }
  }

  function startThumbCapture() {
    if (state.callThumbTimer) clearInterval(state.callThumbTimer);
    state.callThumbTimer = setInterval(function () {
      var v = document.getElementById("callLocalVideo");
      var canvases = document.querySelectorAll(".call-thumb-canvas");
      if (!v || !v.videoWidth || !canvases.length) return;
      canvases.forEach(function (c) {
        var ctx = c.getContext("2d");
        var w = 96;
        var h = 54;
        c.width = w;
        c.height = h;
        ctx.drawImage(v, 0, 0, w, h);
      });
    }, 2000);
  }

  async function toggleCallMic() {
    if (!state.callStream) return;
    state.callMicOn = !state.callMicOn;
    state.callStream.getAudioTracks().forEach(function (t) {
      t.enabled = state.callMicOn;
    });
    render();
    attachLocalVideo();
  }

  async function toggleCallCam() {
    if (!state.callStream) return;
    state.callCamOn = !state.callCamOn;
    state.callStream.getVideoTracks().forEach(function (t) {
      t.enabled = state.callCamOn;
    });
    render();
    attachLocalVideo();
  }

  async function toggleScreenShare() {
    if (state.callScreenStream) {
      state.callScreenStream.getTracks().forEach(function (t) {
        t.stop();
      });
      state.callScreenStream = null;
      render();
      attachLocalVideo();
      return;
    }
    try {
      state.callScreenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      var sv = document.getElementById("callScreenVideo");
      if (sv) sv.srcObject = state.callScreenStream;
      state.callScreenStream.getVideoTracks()[0].onended = function () {
        state.callScreenStream = null;
        render();
      };
    } catch (e) {
      state.error = "Экран: " + (e.message || "отмена");
    }
    render();
  }

  function isMessageSendEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      typeof o.messageId === "string" &&
      typeof o.chatId === "string"
    );
  }

  function sortMessagesAsc(rows) {
    return rows.slice().sort(function (a, b) {
      return new Date(a.created_at) - new Date(b.created_at);
    });
  }

  async function loadChats() {
    if (!state.tokens) return;
    var list = await apiJson("/chats", { method: "GET" });
    state.chats = list;
  }

  async function loadThread(chatId) {
    if (!state.tokens) return;
    var q = new URLSearchParams({ limit: "80" });
    var rows = await apiJson("/chats/" + chatId + "/messages?" + q, { method: "GET" });
    state.messages = sortMessagesAsc(rows);
  }

  function closeWs() {
    rtcHangupAll();
    if (state.ws) {
      try {
        state.ws.close();
      } catch (e) {}
      state.ws = null;
    }
    state.wsState = "off";
  }

  function openWs() {
    closeWs();
    if (!state.tokens || !state.tokens.access_token) return;
    var url = wsBaseUrl() + "?token=" + encodeURIComponent(state.tokens.access_token);
    state.wsState = "connecting";
    var ws = new WebSocket(url);
    state.ws = ws;
    ws.onopen = function () {
      state.wsState = "open";
      render();
    };
    ws.onerror = function () {
      state.wsState = "error";
      render();
    };
    ws.onclose = function () {
      state.wsState = state.wsState === "open" ? "off" : "error";
      render();
    };
    ws.onmessage = function (ev) {
      try {
        var data = JSON.parse(String(ev.data));
        if (data && data.type === "rtc_signal") {
          handleRtcEnvelope(data);
          return;
        }
        if (!isMessageSendEvent(data)) return;
        if (data.chatId !== state.selectedId) {
          loadChats().then(render).catch(function () {});
          return;
        }
        loadThread(data.chatId).then(render).catch(function () {});
      } catch (e) {}
    };
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }

  function filteredChats() {
    var q = (state.sidebarSearch || "").trim().toLowerCase();
    if (!q) return state.chats;
    return state.chats.filter(function (c) {
      var t = (c.title || c.id || "").toLowerCase();
      return t.indexOf(q) !== -1;
    });
  }

  function chatInitial(title) {
    var t = (title || "?").trim();
    return t.charAt(0).toUpperCase();
  }

  function renderAuth() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var outer = el("div", "auth-layout");
    var brand = el("div", "auth-brand");
    brand.appendChild(el("div", "auth-brand-logo", "K"));
    brand.appendChild(el("h1", null, "Korus Messenger"));
    brand.appendChild(el("p", "auth-brand-tag", "Чаты, Markdown и видеовстречи в одном окне."));
    outer.appendChild(brand);
    var card = el("div", "auth-card");
    card.appendChild(el("h2", null, "Вход"));
    if (state.error) {
      card.appendChild(el("div", "error-banner", state.error));
    }
    var tabs = el("div", "tabs");
    var tLogin = el("button", state.authTab === "login" ? "active" : "", "Вход");
    tLogin.type = "button";
    tLogin.onclick = function () {
      state.authTab = "login";
      state.error = null;
      render();
    };
    var tReg = el("button", state.authTab === "register" ? "active" : "", "Регистрация");
    tReg.type = "button";
    tReg.onclick = function () {
      state.authTab = "register";
      state.error = null;
      render();
    };
    tabs.appendChild(tLogin);
    tabs.appendChild(tReg);
    card.appendChild(tabs);
    var form = el("form");
    form.onsubmit = function (e) {
      e.preventDefault();
      submitAuth();
    };
    form.appendChild(field("u", "Логин", "text", "username", true, 3, 32));
    form.appendChild(field("p", "Пароль", "password", "password", true, 8, null));
    if (state.authTab === "register") {
      form.appendChild(field("d", "Отображаемое имя", "text", null, false, null, null));
    }
    var submit = el("button", "btn btn-primary", state.busy ? "…" : state.authTab === "login" ? "Войти" : "Создать аккаунт");
    submit.type = "submit";
    submit.disabled = state.busy;
    submit.style.width = "100%";
    submit.style.marginTop = "8px";
    form.appendChild(submit);
    card.appendChild(form);
    outer.appendChild(card);
    root.appendChild(outer);
  }

  function field(id, label, type, auto, required, minL, maxL) {
    var wrap = el("div", "field");
    var lab = el("label", null, label);
    lab.htmlFor = id;
    var inp = el("input");
    inp.id = id;
    inp.type = type;
    if (auto) inp.autocomplete = auto;
    if (required) inp.required = true;
    if (minL) inp.minLength = minL;
    if (maxL) inp.maxLength = maxL;
    wrap.appendChild(lab);
    wrap.appendChild(inp);
    return wrap;
  }

  async function submitAuth() {
    state.error = null;
    state.busy = true;
    render();
    try {
      var u = document.getElementById("u").value.trim();
      var p = document.getElementById("p").value;
      if (state.authTab === "register") {
        var d = document.getElementById("d").value.trim();
        await apiJson("/auth/register", {
          method: "POST",
          jsonBody: { username: u, password: p, display_name: d || u },
        });
      }
      var t = await apiJson("/auth/login", {
        method: "POST",
        jsonBody: { username: u, password: p },
      });
      saveTokens({
        access_token: t.access_token,
        refresh_token: t.refresh_token,
        expires_in: t.expires_in,
      });
      document.getElementById("p").value = "";
      await loadChats();
      openWs();
    } catch (err) {
      state.error = err.message || "Ошибка";
    } finally {
      state.busy = false;
      render();
    }
  }

  async function logout() {
    stopCallMedia();
    state.callPanelOpen = false;
    if (state.tokens && state.tokens.refresh_token) {
      try {
        await apiJson("/auth/logout", {
          method: "POST",
          jsonBody: { refresh_token: state.tokens.refresh_token },
        });
      } catch (e) {}
    }
    clearTokens();
    state.selectedId = null;
    state.chats = [];
    state.messages = [];
    closeWs();
    render();
  }

  async function newGroup() {
    var title = window.prompt("Название группы");
    if (!title || !title.trim() || !state.tokens) return;
    state.error = null;
    state.busy = true;
    render();
    try {
      var chat = await apiJson("/chats", {
        method: "POST",
        jsonBody: { type: "group", title: title.trim(), member_ids: [] },
      });
      await loadChats();
      state.selectedId = chat.id;
      await loadThread(chat.id);
    } catch (err) {
      state.error = err.message || "Не удалось создать чат";
    } finally {
      state.busy = false;
      render();
    }
  }

  function wrapComposerSelection(before, after) {
    var ta = document.getElementById("msgdraft");
    if (!ta) return;
    var s = ta.selectionStart;
    var e = ta.selectionEnd;
    var val = ta.value;
    var sel = val.slice(s, e);
    ta.value = val.slice(0, s) + before + sel + after + val.slice(e);
    ta.focus();
    ta.selectionStart = s + before.length;
    ta.selectionEnd = s + before.length + sel.length;
  }

  async function sendMessage() {
    var ta = document.getElementById("msgdraft");
    if (!ta || !state.tokens || !state.selectedId) return;
    var text = ta.value.trim();
    if (!text) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: {
          type: "text",
          content: text,
          reply_to_msg_id: null,
          client_msg_id: null,
          ttl_seconds: null,
        },
      });
      ta.value = "";
      await loadThread(state.selectedId);
    } catch (err) {
      state.error = err.message || "Не отправилось";
    } finally {
      state.busy = false;
      render();
    }
  }

  function renderCallPanel(shell) {
    if (!state.callPanelOpen) return;
    var panel = el("aside", "call-panel");
    var ph = el("div", "call-panel-head");
    ph.appendChild(el("span", "call-panel-title", "Видео / конференция"));
    var cl = el("button", "btn btn-ghost btn-sm", "Свернуть");
    cl.type = "button";
    cl.onclick = function () {
      toggleCallPanel();
    };
    ph.appendChild(cl);
    panel.appendChild(ph);
    panel.appendChild(
      el(
        "p",
        "call-hint",
        "WebRTC mesh через NATS (rtc.signal). ICE: по умолчанию публичный STUN; TURN — переменная WEB_CLIENT_RTC_ICE_SERVERS (JSON-массив в /web-client-env.js)."
      )
    );
    var stage = el("div", "call-stage");
    var mainVid = el("div", "call-main-wrap");
    var lv = document.createElement("video");
    lv.id = "callLocalVideo";
    lv.className = "call-video";
    lv.autoplay = true;
    lv.playsInline = true;
    lv.muted = true;
    mainVid.appendChild(lv);
    stage.appendChild(mainVid);
    if (state.callScreenStream) {
      var sw = el("div", "call-screen-wrap");
      var sv = document.createElement("video");
      sv.id = "callScreenVideo";
      sv.className = "call-video call-screen";
      sv.autoplay = true;
      sv.playsInline = true;
      sv.srcObject = state.callScreenStream;
      sw.appendChild(sv);
      stage.appendChild(sw);
    }
    panel.appendChild(stage);
    var thumbs = el("div", "call-thumbs");
    thumbs.appendChild(el("span", "call-thumbs-label", "Миниатюры (обновление каждые 2 с)"));
    var c1 = document.createElement("canvas");
    c1.className = "call-thumb-canvas";
    var c2 = document.createElement("canvas");
    c2.className = "call-thumb-canvas";
    thumbs.appendChild(c1);
    thumbs.appendChild(c2);
    panel.appendChild(thumbs);
    var remotes = el("div", "call-remotes");
    remotes.appendChild(el("div", "call-remotes-title", "Удалённые потоки (участники чата)"));
    state.rtcPeerIds.forEach(function (pid) {
      var slot = el("div", "rtc-remote-slot");
      slot.id = "rtc-remote-" + pid;
      slot.appendChild(el("div", "rtc-remote-label", pid.slice(0, 8) + "…"));
      var rv = document.createElement("video");
      rv.className = "call-video rtc-remote-video";
      rv.autoplay = true;
      rv.playsInline = true;
      rv.setAttribute("playsinline", "");
      slot.appendChild(rv);
      remotes.appendChild(slot);
    });
    if (!state.rtcPeerIds.length) {
      remotes.appendChild(
        el("div", "call-participant-slot", "В чате только вы, или нет доступа к списку участников.")
      );
    }
    panel.appendChild(remotes);
    var bar = el("div", "call-toolbar");
    var bMic = el("button", "btn " + (state.callMicOn ? "btn-primary" : "btn-ghost"), state.callMicOn ? "Мик: вкл" : "Мик: выкл");
    bMic.type = "button";
    bMic.onclick = function () {
      toggleCallMic();
    };
    var bCam = el("button", "btn " + (state.callCamOn ? "btn-primary" : "btn-ghost"), state.callCamOn ? "Кам: вкл" : "Кам: выкл");
    bCam.type = "button";
    bCam.onclick = function () {
      toggleCallCam();
    };
    var bScr = el("button", "btn btn-ghost", state.callScreenStream ? "Стоп экрана" : "Экран");
    bScr.type = "button";
    bScr.onclick = function () {
      toggleScreenShare();
    };
    bar.appendChild(bMic);
    bar.appendChild(bCam);
    bar.appendChild(bScr);
    panel.appendChild(bar);
    shell.appendChild(panel);
    setTimeout(attachLocalVideo, 0);
  }

  function renderMain() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el("div", "app-shell messenger-shell" + (state.callPanelOpen ? " call-open" : ""));
    var header = el("header", "app-header");
    var hl = el("div", "app-header-left");
    hl.appendChild(el("h1", null, "Korus Messenger"));
    header.appendChild(hl);
    var hdrR = el("div", "app-header-right");
    var callBtn = el("button", "btn btn-ghost", state.callPanelOpen ? "Скрыть видео" : "Видео / конференция");
    callBtn.type = "button";
    callBtn.onclick = function () {
      toggleCallPanel();
    };
    hdrR.appendChild(callBtn);
    var ws = el("span", "ws-status " + (state.wsState === "open" ? "connected" : "disconnected"));
    ws.title = wsBaseUrl();
    ws.textContent =
      "WS " +
      (state.wsState === "open"
        ? "онлайн"
        : state.wsState === "connecting"
          ? "…"
          : state.wsState === "error"
            ? "ошибка"
            : "нет");
    hdrR.appendChild(ws);
    var lo = el("button", "btn btn-ghost", "Выйти");
    lo.type = "button";
    lo.onclick = function () {
      logout();
    };
    hdrR.appendChild(lo);
    header.appendChild(hdrR);
    shell.appendChild(header);
    if (state.error) {
      var wrap = el("div", "banner-wrap");
      wrap.appendChild(el("div", "error-banner", state.error));
      shell.appendChild(wrap);
    }
    var main = el("div", "messenger");
    var side = el("aside", "sidebar");
    var sh = el("div", "sidebar-header");
    var search = el("input");
    search.type = "search";
    search.className = "sidebar-search";
    search.placeholder = "Поиск чатов…";
    search.value = state.sidebarSearch;
    search.oninput = function () {
      state.sidebarSearch = search.value;
      render();
    };
    sh.appendChild(search);
    var ng = el("button", "btn btn-primary btn-block", "Новая группа");
    ng.type = "button";
    ng.disabled = state.busy;
    ng.onclick = function () {
      newGroup();
    };
    sh.appendChild(ng);
    side.appendChild(sh);
    var list = el("div", "chat-list");
    var fc = filteredChats();
    if (fc.length === 0) {
      list.appendChild(el("div", "chat-list-empty", state.chats.length ? "Нет чатов по запросу." : "Нет чатов."));
    }
    fc.forEach(function (c) {
      var b = el("button", "chat-item" + (c.id === state.selectedId ? " active" : ""));
      b.type = "button";
      b.onclick = function () {
        rtcHangupAll();
        state.selectedId = c.id;
        state.error = null;
        loadThread(c.id)
          .then(function () {
            openWs();
            render();
          })
          .catch(function (err) {
            state.error = err.message;
            render();
          });
      };
      var row = el("div", "chat-item-row");
      var av = el("div", "chat-avatar", chatInitial(c.title || c.id));
      row.appendChild(av);
      var txt = el("div", "chat-item-text");
      txt.appendChild(el("div", "chat-item-title", c.title || c.id.slice(0, 8)));
      txt.appendChild(el("div", "chat-meta", c.type + " · " + c.member_count + " уч."));
      row.appendChild(txt);
      b.appendChild(row);
      list.appendChild(b);
    });
    side.appendChild(list);
    main.appendChild(side);
    var thread = el("section", "thread");
    if (!state.selectedId) {
      thread.appendChild(el("div", "empty-thread", "Выберите чат слева или создайте группу."));
    } else {
      var sel = state.chats.find(function (x) {
        return x.id === state.selectedId;
      });
      thread.appendChild(el("div", "thread-header", (sel && sel.title) || state.selectedId));
      var msgs = el("div", "messages");
      var myId = jwtSub(state.tokens.access_token);
      state.messages.forEach(function (m) {
        var art = el("article", "msg" + (myId && m.sender_id === myId ? " own" : ""));
        var meta = el("div", "msg-meta");
        meta.appendChild(document.createTextNode(myId && m.sender_id === myId ? "Вы" : m.sender_id.slice(0, 8)));
        var ts = el("span");
        ts.className = "msg-ts";
        ts.textContent = new Date(m.created_at).toLocaleString();
        meta.appendChild(ts);
        art.appendChild(meta);
        if (m.type !== "text") {
          art.appendChild(el("div", "msg-type", m.type));
        }
        var body = el("div", "msg-body md");
        body.innerHTML = safeMarkdown(m.content);
        art.appendChild(body);
        msgs.appendChild(art);
      });
      thread.appendChild(msgs);
      var comp = el("form", "composer");
      comp.onsubmit = function (e) {
        e.preventDefault();
        sendMessage();
      };
      var fmt = el("div", "composer-format");
      var bBold = el("button", "btn btn-ghost btn-icon", "B");
      bBold.type = "button";
      bBold.title = "Жирный";
      bBold.onclick = function () {
        wrapComposerSelection("**", "**");
      };
      var bIt = el("button", "btn btn-ghost btn-icon", "I");
      bIt.type = "button";
      bIt.title = "Курсив";
      bIt.onclick = function () {
        wrapComposerSelection("*", "*");
      };
      var bCode = el("button", "btn btn-ghost btn-icon", "</>");
      bCode.type = "button";
      bCode.title = "Код";
      bCode.onclick = function () {
        wrapComposerSelection("`", "`");
      };
      fmt.appendChild(bBold);
      fmt.appendChild(bIt);
      fmt.appendChild(bCode);
      fmt.appendChild(el("span", "composer-md-hint", "Markdown: **жирный**, *курсив*, `код`, [ссылка](https://…)"));
      comp.appendChild(fmt);
      var ta = el("textarea");
      ta.id = "msgdraft";
      ta.rows = 3;
      ta.placeholder = "Сообщение… (Shift+Enter — новая строка)";
      ta.onkeydown = function (e) {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          sendMessage();
        }
      };
      comp.appendChild(ta);
      var sb = el("button", "btn btn-primary", "Отправить");
      sb.type = "submit";
      sb.disabled = state.busy;
      comp.appendChild(sb);
      thread.appendChild(comp);
    }
    main.appendChild(thread);
    shell.appendChild(main);
    renderCallPanel(shell);
    root.appendChild(shell);
  }

  function render() {
    if (!state.tokens) {
      renderAuth();
    } else {
      renderMain();
    }
  }

  async function initAfterLogin() {
    try {
      await loadChats();
      if (state.selectedId) {
        await loadThread(state.selectedId);
      }
    } catch (err) {
      state.error = err.message;
    }
    openWs();
    render();
  }

  function boot() {
    state.tokens = loadTokens();
    if (state.tokens) {
      initAfterLogin();
    } else {
      render();
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
