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

  function renderAuth() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el("div", "app-shell");
    var header = el("header", "app-header");
    header.appendChild(el("h1", null, "Korus Messenger"));
    shell.appendChild(header);
    var card = el("div", "auth-card");
    card.appendChild(el("h2", null, "Вход"));
    if (state.error) {
      var eb = el("div", "error-banner", state.error);
      card.appendChild(eb);
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
    shell.appendChild(card);
    root.appendChild(shell);
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

  function renderMain() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el("div", "app-shell");
    var header = el("header", "app-header");
    header.appendChild(el("h1", null, "Korus Messenger"));
    var hdrR = el("div");
    hdrR.style.display = "flex";
    hdrR.style.alignItems = "center";
    hdrR.style.gap = "16px";
    var ws = el("span", "ws-status " + (state.wsState === "open" ? "connected" : "disconnected"));
    ws.title = wsBaseUrl();
    ws.textContent =
      "WS: " +
      (state.wsState === "open"
        ? "онлайн"
        : state.wsState === "connecting"
          ? "подключение…"
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
      var wrap = el("div");
      wrap.style.padding = "8px 16px";
      wrap.appendChild(el("div", "error-banner", state.error));
      shell.appendChild(wrap);
    }
    var main = el("div", "messenger");
    var side = el("aside", "sidebar");
    var sh = el("div", "sidebar-header");
    var ng = el("button", "btn btn-primary", "Новая группа");
    ng.type = "button";
    ng.disabled = state.busy;
    ng.onclick = function () {
      newGroup();
    };
    sh.appendChild(ng);
    side.appendChild(sh);
    var list = el("div", "chat-list");
    state.chats.forEach(function (c) {
      var b = el("button", "chat-item" + (c.id === state.selectedId ? " active" : ""));
      b.type = "button";
      b.onclick = function () {
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
      b.appendChild(el("div", null, c.title || c.id.slice(0, 8)));
      var meta = el("div", "chat-meta", c.type + " · " + c.member_count + " уч.");
      b.appendChild(meta);
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
        ts.style.marginLeft = "8px";
        ts.style.opacity = "0.8";
        ts.textContent = new Date(m.created_at).toLocaleString();
        meta.appendChild(ts);
        art.appendChild(meta);
        if (m.type !== "text") {
          art.appendChild(el("div", "msg-type", m.type));
        }
        var body = el("div");
        body.style.whiteSpace = "pre-wrap";
        body.style.wordBreak = "break-word";
        body.textContent = m.content;
        art.appendChild(body);
        msgs.appendChild(art);
      });
      thread.appendChild(msgs);
      var comp = el("form", "composer");
      comp.onsubmit = function (e) {
        e.preventDefault();
        sendMessage();
      };
      var ta = el("textarea");
      ta.id = "msgdraft";
      ta.rows = 2;
      ta.placeholder = "Сообщение…";
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
