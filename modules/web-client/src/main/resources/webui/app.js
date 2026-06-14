(function () {
  "use strict";

  var i18n = window.KorusI18n || {
    t: function (key) {
      return key;
    },
    translateError: function (msg) {
      return msg || L("errors.generic");
    },
    init: function () {},
    getLocale: function () {
      return "ru";
    },
    setLocale: function () {},
    supportedLocales: function () {
      return ["ru"];
    },
  };

  function L(key, params) {
    return i18n.t(key, params);
  }

  function localErr(msg) {
    return i18n.translateError(msg);
  }

  function localMediaErr(msg) {
    return i18n.translateMediaError ? i18n.translateMediaError(msg) : localErr(msg);
  }

  function meshCallChatReady() {
    return !!(state.selectedId && state.selectedId !== state.savedChatId && state.tokens);
  }

  function conferenceIsTracked(conf) {
    return !!(conf && conf.conference_id);
  }

  function listUserActiveConferences() {
    var seen = {};
    var list = [];
    var map = state.activeConferenceByChat || {};
    Object.keys(map).forEach(function (chatId) {
      var c = map[chatId];
      if (c && c.conference_id && c.status === "active" && !seen[c.conference_id]) {
        seen[c.conference_id] = true;
        list.push(c);
      }
    });
    if (state.chatConferences) {
      state.chatConferences.forEach(function (c) {
        if (c && c.conference_id && c.status === "active" && !seen[c.conference_id]) {
          seen[c.conference_id] = true;
          list.push(c);
        }
      });
    }
    return list;
  }

  function ensureCallPanelOpen() {
    state.callPanelOpen = true;
  }

  function parseMemberIdList(raw) {
    return (raw || "")
      .split(/[,;\s]+/)
      .map(function (s) {
        return s.trim();
      })
      .filter(function (s) {
        return /^[0-9a-f-]{36}$/i.test(s);
      });
  }

  function parseConferenceLinkInput(raw) {
    raw = (raw || "").trim();
    if (!raw) return { uuid: null, slug: null, url: null };
    if (/^[0-9a-f-]{36}$/i.test(raw)) return { uuid: raw, slug: null, url: null };
    var cleaned = raw.replace(/#.*$/, "").replace(/\/+$/, "");
    var url = cleaned.indexOf("http") === 0 ? cleaned : null;
    var slug = url ? url.split("/").pop() : cleaned.split("/").pop();
    return { uuid: null, slug: slug || null, url: url };
  }

  function buildGuestJitsiUrl(slug) {
    var base =
      state.mediaCaps && state.mediaCaps.jitsi_base_url
        ? String(state.mediaCaps.jitsi_base_url).replace(/\/+$/, "")
        : "https://meet.jit.si";
    return base + "/" + encodeURIComponent(slug);
  }

  function copyConferenceLinkToClipboard(url, silent) {
    if (!url) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(
        function () {
          state.statusMessage = L("conference.linkCopied");
          render();
        },
        function () {
          if (!silent) {
            state.error = L("conference.linkCopyFailed");
            render();
          }
        }
      );
      return;
    }
    if (!silent) {
      state.error = L("conference.clipboardUnavailable");
      render();
    }
  }

  function meetingAppDeepLink(conf) {
    if (!conf || !conf.room_slug) return "";
    try {
      return (
        window.location.origin +
        window.location.pathname +
        "?meet=" +
        encodeURIComponent(conf.room_slug)
      );
    } catch (e) {
      return "";
    }
  }

  async function postMeetingInviteMessage(chatId, conf) {
    if (!chatId || !conf || !conf.join_url || !state.tokens) return;
    var label = (conf.title && conf.title.trim()) || L("conference.defaultMeetingTitle");
    var appLink = meetingAppDeepLink(conf);
    var text = L("conference.inviteMessage", {
      title: label,
      url: conf.join_url,
      appLink: appLink,
    });
    await apiJson("/chats/" + chatId + "/messages", {
      method: "POST",
      jsonBody: { type: "text", content: text },
    });
  }

  async function inviteMembersToMeetingChat(conf) {
    if (!conferenceIsTracked(conf) || !conf.chat_id || !state.tokens) return;
    var raw = window.prompt(L("conference.inviteMembersPrompt")) || "";
    var ids = parseMemberIdList(raw);
    if (!ids.length) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      for (var i = 0; i < ids.length; i++) {
        await apiJson("/chats/" + conf.chat_id + "/members", {
          method: "POST",
          jsonBody: { user_id: ids[i] },
        });
      }
      state.statusMessage = L("conference.membersInvited");
      await refreshChats();
    } catch (e) {
      state.error = localErr(e.message) || L("conference.inviteMembersFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  const TOKEN_KEY = "korus_web_tokens";
  const THEME_KEY = "korus_web_theme";
  const STYLE_KEY = "korus_web_style";
  const KORUS_PALETTE = "korus";
  const NOTIF_KEY = "korus_web_notify";
  const PENDING_CHAT_KEY = "korus_pending_chat";
  const PENDING_MSG_KEY = "korus_pending_msg";
  const PENDING_MEET_KEY = "korus_pending_meet";
  const PENDING_CONF_KEY = "korus_pending_conf";
  const LAST_PUBLIC_LINK_KEY = "korus_last_public_link";
  const DRAFT_KEY_PREFIX = "korus_draft_";
  const SOUND_NOTIF_KEY = "korus_sound_notify";

  const state = {
    tokens: null,
    authTab: "login",
    error: null,
    statusMessage: null,
    busy: false,
    chats: [],
    selectedId: null,
    messages: [],
    ws: null,
    wsState: "off",
    wsReplacing: false,
    wsManualClose: false,
    wsReconnectTimer: null,
    wsReconnectAttempt: 0,
    sidebarSearch: "",
    callPanelOpen: false,
    callMode: "jitsi",
    activeConference: null,
    activeConferenceByChat: {},
    chatConferences: null,
    conferenceBusy: false,
    conferenceParticipantsList: null,
    conferenceParticipantsConfId: null,
    jitsiIframeEl: null,
    contactImportText: "",
    callStream: null,
    callScreenStream: null,
    callThumbTimer: null,
    callCamOn: true,
    callMicOn: true,
    rtcPeerIds: [],
    rtcPeers: {},
    rtcPendingCandidates: {},
    mediaCaps: null,
    blobUrls: [],
    unreadByChat: {},
    userSearchHits: null,
    userSearchBusy: false,
    chatPreview: {},
    typingExpireByChat: {},
    readReceiptsByMessage: {},
    replyTo: null,
    reactionsByMsg: {},
    shouldScrollThread: false,
    pinnedMessages: [],
    threadHasMore: false,
    threadLoadingMore: false,
    threadSearch: "",
    threadSearchHits: null,
    threadSearchBusy: false,
    composerTtl: "",
    forwardPick: null,
    globalSearch: "",
    globalSearchHits: null,
    globalSearchBusy: false,
    appearance: "dark",
    palette: "korus",
    notifyPref: false,
    e2eeKeyCount: null,
    serverKeyPackages: null,
    settingsOpen: false,
    settingsTab: null,
    soundNotify: false,
    lastPublicLink: null,
    e2eePlaintextCache: {},
    localKeyPackageMeta: null,
    pwaInstallPrompt: null,
    networkOnline: true,
    webPushRegistered: false,
    webPushError: null,
    myDevices: null,
    swUpdateReady: false,
    savedChatId: null,
    myPresence: "online",
    blockedUsers: null,
    sidebarMode: "chats",
    contacts: null,
    contactsBusy: false,
    myDisplayName: "",
    exportBusy: false,
    exportJobId: null,
    exportJobChatId: null,
    exportProgressLabel: null,
    serverVersion: null,
    membersModalOpen: false,
    chatMembers: null,
    chatBans: null,
    chatMembersBusy: false,
    incomingRtcCall: null,
    messageVersionsOpen: false,
    messageVersions: null,
    messageVersionsMsgId: null,
    messageVersionsBusy: false,
    fileLinksOpen: false,
    fileLinksFileId: null,
    fileLinksRows: null,
    fileLinksBusy: false,
    myPublicLinks: null,
    myPublicLinksBusy: false,
  };

  var WEB_DEVICE_NAME = "web-client";
  var pendingSwReload = false;

  var IDB_NAME = "korus_web_client";
  var IDB_STORE = "kv";
  var IDB_KEY_LOCAL_KP = "local_key_package";

  var userSearchTimer = null;
  var deferredInstallPrompt = null;
  var typingNotifyTimer = null;
  var typingSidebarTimer = null;
  var renderScheduled = false;
  var threadSearchTimer = null;
  var globalSearchTimer = null;
  var draftSaveTimer = null;
  var incomingRingTimer = null;
  var exportPollGeneration = 0;
  var chatPreviewHydrateGen = 0;
  var CHAT_PREVIEW_HYDRATE_MAX = 24;
  var CHAT_PREVIEW_HYDRATE_MORE = 12;
  var chatPreviewMoreTimer = null;
  var heartbeatTimer = null;
  var ttlRenderTimer = null;
  var HEARTBEAT_INTERVAL_MS = 60000;
  var HEARTBEAT_WS_MIN_MS = 30000;
  var lastHeartbeatMs = 0;
  var QUICK_REACTIONS = ["👍", "❤️", "😂"];
  var LOCALE_LABEL_KEYS = {
    ru: "settings.localeRu",
    en: "settings.localeEn",
    be: "settings.localeBe",
    kk: "settings.localeKk",
    zh: "settings.localeZh",
    ko: "settings.localeKo",
  };
  var PRESENCE_LABEL_KEYS = {
    online: "ui.settings.presenceOnline",
    away: "ui.settings.presenceAway",
    dnd: "ui.settings.presenceDnd",
    offline: "ui.settings.presenceOffline",
  };
  var SETTINGS_TAB_IDS = ["general", "profile", "notifications", "links", "security"];
  var SETTINGS_TAB_KEY = "korus_settings_tab";
  var SETTINGS_TAB_LABEL_KEYS = {
    general: "ui.settings.tabGeneral",
    profile: "ui.settings.tabProfile",
    notifications: "ui.settings.tabNotifications",
    links: "ui.settings.tabLinks",
    security: "ui.settings.tabSecurity",
  };
  var THREAD_PAGE = 50;
  var THREAD_SOFT_RELOAD = {
    keepScroll: true,
    preserveE2eeCache: true,
    preserveBlobs: true,
  };
  var uiFormatUtils = window.KorusUiFormatUtils || {
    formatInstantLabel: function (iso) {
      if (!iso) return "—";
      try {
        return new Date(iso).toLocaleString();
      } catch (e) {
        return String(iso);
      }
    },
    formatChatListTime: function (ms) {
      return ms ? String(ms) : "";
    },
    formatTtlLabel: function () {
      return "";
    },
    formatTimeLeft: function () {
      return L("time.expired");
    },
  };
  var uiShellUtils = window.KorusUiShellUtils || {
    loadStyleSet: function (styleKey, themeKey, palette) {
      try {
        var raw = localStorage.getItem(styleKey);
        if (raw) {
          var parsed = JSON.parse(raw);
          if (parsed && (parsed.appearance === "light" || parsed.appearance === "dark")) {
            return { appearance: parsed.appearance, palette: palette };
          }
        }
      } catch (e) {}
      var legacyTheme = localStorage.getItem(themeKey);
      return {
        appearance: legacyTheme === "light" ? "light" : "dark",
        palette: palette,
      };
    },
    saveStyleSet: function (styleKey, themeKey, appearance, palette) {
      try {
        localStorage.setItem(
          styleKey,
          JSON.stringify({ appearance: appearance, palette: palette })
        );
        localStorage.setItem(themeKey, appearance);
      } catch (e) {}
    },
    applyStyleSet: function (doc, set, palette) {
      var appearance = set && set.appearance === "light" ? "light" : "dark";
      doc.documentElement.setAttribute("data-appearance", appearance);
      doc.documentElement.setAttribute("data-palette", palette);
      doc.documentElement.removeAttribute("data-theme");
      doc.documentElement.style.colorScheme = appearance;
      var metaTheme = doc.querySelector('meta[name="theme-color"]');
      if (metaTheme) {
        var rootStyle = getComputedStyle(doc.documentElement);
        var fromVar = rootStyle.getPropertyValue("--theme-color-meta").trim();
        metaTheme.setAttribute("content", fromVar || "#7949f4");
      }
      return { appearance: appearance, palette: palette };
    },
    syncNotifyPref: function (notifKey, soundNotifKey) {
      return {
        notifyPref:
          localStorage.getItem(notifKey) === "1" &&
          typeof Notification !== "undefined" &&
          Notification.permission === "granted",
        soundNotify: localStorage.getItem(soundNotifKey) === "1",
      };
    },
    draftStorageKey: function (prefix, chatId) {
      return prefix + chatId;
    },
    loadComposerDraftForChat: function (prefix, chatId) {
      if (!chatId) return "";
      try {
        return localStorage.getItem(prefix + chatId) || "";
      } catch (e) {
        return "";
      }
    },
    saveComposerDraftForChat: function (prefix, chatId, text) {
      if (!chatId) return;
      try {
        var key = prefix + chatId;
        if (text && String(text).trim()) {
          localStorage.setItem(key, text);
        } else {
          localStorage.removeItem(key);
        }
      } catch (e) {}
    },
    clearComposerDraftForChat: function (prefix, chatId) {
      if (!chatId) return;
      try {
        localStorage.removeItem(prefix + chatId);
      } catch (e) {}
    },
    composerDraftPreview: function (prefix, chatId) {
      var draft = this.loadComposerDraftForChat(prefix, chatId);
      if (!draft || !String(draft).trim()) return "";
      var text = String(draft).trim().replace(/\s+/g, " ");
      if (text.length > 48) text = text.slice(0, 48) + "…";
      return text;
    },
    loadLastPublicLink: function (storageKey) {
      try {
        var raw = sessionStorage.getItem(storageKey);
        if (!raw) return null;
        var parsed = JSON.parse(raw);
        if (parsed && parsed.file_id && parsed.link_id) return parsed;
      } catch (e) {}
      return null;
    },
    saveLastPublicLink: function (storageKey, link) {
      try {
        if (link && link.file_id && link.link_id) {
          sessionStorage.setItem(storageKey, JSON.stringify(link));
        } else {
          sessionStorage.removeItem(storageKey);
        }
      } catch (e) {}
    },
    stashPendingDeepLink: function (chatStorageKey, msgStorageKey, chatId, msgId) {
      try {
        if (chatId) sessionStorage.setItem(chatStorageKey, chatId);
        if (msgId) sessionStorage.setItem(msgStorageKey, msgId);
      } catch (e) {}
    },
    readAndClearPendingDeepLink: function (chatStorageKey, msgStorageKey) {
      var chatId = null;
      var msgId = null;
      try {
        chatId = sessionStorage.getItem(chatStorageKey);
        msgId = sessionStorage.getItem(msgStorageKey);
        if (chatId) sessionStorage.removeItem(chatStorageKey);
        if (msgId) sessionStorage.removeItem(msgStorageKey);
      } catch (e) {}
      return { chatId: chatId, msgId: msgId };
    },
  };
  var uiTransportUtils = window.KorusUiTransportUtils || {
    apiRoot: function () {
      return "/api/v1";
    },
    wsBaseUrl: function (win, loc) {
      var cfg = win && win.__WEB_CLIENT__;
      if (cfg && cfg.wsUrl) return String(cfg.wsUrl).replace(/\/$/, "");
      var p = loc && loc.protocol === "https:" ? "wss:" : "ws:";
      var host = loc && loc.host ? loc.host : "127.0.0.1:8081";
      return p + "//" + host + "/ws";
    },
    buildWsUrl: function (baseUrl, accessToken) {
      return baseUrl + "?token=" + encodeURIComponent(accessToken);
    },
    nextWsReconnectDelay: function (attempt) {
      return Math.min(30000, 1000 * Math.pow(2, attempt));
    },
    createApiClient: function (options) {
      var fetchImpl = options.fetchImpl || window.fetch.bind(window);
      var getAccessToken = options.getAccessToken || function () { return null; };
      var getRefreshToken = options.getRefreshToken || function () { return null; };
      var isPublicAuthPath = options.isPublicAuthPath || function () { return false; };
      var tryRefreshTokens = options.tryRefreshTokens || (async function () { return false; });
      var onSessionExpired = options.onSessionExpired || function () {};
      var root = options.apiRoot || "/api/v1";

      async function apiFetch(path, opts) {
        opts = opts || {};
        var headers = Object.assign({}, opts.headers || {});
        if (!(opts.body instanceof FormData) && !headers.Accept) {
          headers.Accept = opts.accept || "application/json";
        }
        var body = opts.body;
        if (opts.jsonBody !== undefined) {
          headers["Content-Type"] = "application/json";
          body = JSON.stringify(opts.jsonBody);
        }
        var accessToken = getAccessToken();
        if (accessToken && !opts.noAuth) {
          headers.Authorization = "Bearer " + accessToken;
        }
        var url = root + (path.startsWith("/") ? path : "/" + path);
        var res = await fetchImpl(url, {
          method: opts.method || "GET",
          headers: headers,
          body: body,
        });
        if (
          res.status === 401 &&
          !opts.noRefresh &&
          getRefreshToken() &&
          !isPublicAuthPath(path)
        ) {
          var refreshed = await tryRefreshTokens();
          if (refreshed) {
            return apiFetch(path, Object.assign({}, opts, { noRefresh: true }));
          }
          onSessionExpired();
          throw new Error(L("errors.sessionExpired"));
        }
        return res;
      }

      async function apiJson(path, opts) {
        var res = await apiFetch(path, opts || {});
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
          throw new Error(localErr(msg));
        }
        return parsed;
      }

      return { apiFetch: apiFetch, apiJson: apiJson };
    },
  };
  var uiMessagesUtils = window.KorusUiMessagesUtils || {
    formatPreviewText: function (type, content, isE2eeTypeFn, e2eePlainTypeFn) {
      var t = type || "text";
      if (isE2eeTypeFn(t)) {
        var base = e2eePlainTypeFn(t);
        if (base === "image") return "🔒 Изображение";
        if (base === "video") return "🔒 Видео";
        if (base === "file") return "🔒 Файл";
        return "🔒 Зашифровано";
      }
      if (t === "image") return L("ui.message.image");
      if (t === "video") return L("ui.message.video");
      if (t === "file") return L("ui.message.file");
      var text = String(content || "")
        .replace(/[*_`#[\]]/g, "")
        .replace(/\s+/g, " ")
        .trim();
      if (text.length > 72) text = text.slice(0, 72) + "…";
      return text || L("ui.message.default");
    },
    formatPreviewForMessage: function (
      message,
      messageAttachmentKindFn,
      messageAttachmentFileIdFn,
      formatPreviewTextFn
    ) {
      if (!message) return L("ui.message.default");
      if (messageAttachmentKindFn(message) && messageAttachmentFileIdFn(message)) {
        return formatPreviewTextFn(message.type, "");
      }
      return formatPreviewTextFn(message.type, message.content);
    },
    sortMessagesAsc: function (rows) {
      return (rows || []).slice().sort(function (a, b) {
        return new Date(a.created_at) - new Date(b.created_at);
      });
    },
    findMessageInThread: function (messages, msgId) {
      if (!msgId || !messages || !messages.length) return null;
      for (var i = 0; i < messages.length; i++) {
        if (messages[i].id === msgId) return messages[i];
      }
      return null;
    },
    mergeMessageIntoThread: function (messages, fullMessage) {
      if (!fullMessage || !fullMessage.id) return messages || [];
      var rows = messages || [];
      if (this.findMessageInThread(rows, fullMessage.id)) {
        return rows.map(function (m) {
          return m.id === fullMessage.id ? fullMessage : m;
        });
      }
      return this.sortMessagesAsc(rows.concat([fullMessage]));
    },
    patchMessageInThread: function (messages, messageId, patch) {
      if (!messageId || !patch) return { messages: messages || [], touched: false };
      var touched = false;
      var nextMessages = (messages || []).map(function (m) {
        if (m.id !== messageId) return m;
        touched = true;
        return Object.assign({}, m, patch);
      });
      return { messages: nextMessages, touched: touched };
    },
    applyReactionChangeEventRows: function (rows, change, userId, reaction) {
      var nextRows = (rows || []).slice();
      if (change === "add") {
        var exists = nextRows.some(function (r) {
          return r.user_id === userId && r.reaction === reaction;
        });
        if (!exists) nextRows.push({ user_id: userId, reaction: reaction });
        return nextRows;
      }
      return nextRows.filter(function (r) {
        return !(r.user_id === userId && r.reaction === reaction);
      });
    },
  };
  var uiRtcUtils = window.KorusUiRtcUtils || {
    sendRtcSignal: function (ws, chatId, payload) {
      if (!ws || ws.readyState !== WebSocket.OPEN || !chatId || !payload) return false;
      ws.send(
        JSON.stringify({
          type: "rtc_signal",
          chatId: chatId,
          payload: payload,
        })
      );
      return true;
    },
    sendRtcHangups: function (ws, chatId, peerIds) {
      if (!ws || ws.readyState !== WebSocket.OPEN || !chatId) return;
      (peerIds || []).forEach(function (peerId) {
        if (!peerId) return;
        uiRtcUtils.sendRtcSignal(ws, chatId, { kind: "hangup", targetUserId: peerId });
      });
    },
  };
  var uiPwaSettingsUtils = window.KorusUiPwaSettingsUtils || {
    getVapidPublicKey: function (win) {
      var cfg = win && win.__WEB_CLIENT__;
      return cfg && cfg.vapidPublicKey ? String(cfg.vapidPublicKey) : null;
    },
    notificationsAllowed: function (notifyPref, notificationApi) {
      return (
        !!notifyPref &&
        typeof notificationApi !== "undefined" &&
        notificationApi.permission === "granted"
      );
    },
    urlBase64ToUint8Array: function (base64String) {
      var padding = "=".repeat((4 - (base64String.length % 4)) % 4);
      var base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
      var raw = window.atob(base64);
      var out = new Uint8Array(raw.length);
      for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
      return out;
    },
    pushTokenFromSubscription: function (subscription) {
      if (subscription && typeof subscription.toJSON === "function") {
        return JSON.stringify(subscription.toJSON());
      }
      return JSON.stringify(subscription);
    },
    canUseServiceWorker: function (nav) {
      return !!(nav && nav.serviceWorker);
    },
    canUseWebPush: function (nav, win) {
      return !!(nav && nav.serviceWorker && win && win.PushManager);
    },
    isServiceWorkerDisabled: function (win) {
      var cfg = win && win.__WEB_CLIENT__;
      return !!(cfg && cfg.disableServiceWorker);
    },
    nextServiceWorkerUpdatePromise: function (nav) {
      if (!nav || !nav.serviceWorker) return Promise.resolve();
      return nav.serviceWorker
        .getRegistration("/")
        .then(function (reg) {
          if (reg) return reg.update();
        })
        .catch(function () {});
    },
    registerServiceWorker: function (nav, win, callbacks) {
      if (!nav || !nav.serviceWorker) return Promise.resolve(false);
      var cfg = win && win.__WEB_CLIENT__;
      if (cfg && cfg.disableServiceWorker) return Promise.resolve(false);
      callbacks = callbacks || {};
      return nav.serviceWorker
        .register("/sw.js", { scope: "/" })
        .then(function (reg) {
          reg.update().catch(function () {});
          if (reg.waiting && nav.serviceWorker.controller && callbacks.onUpdateReady) {
            callbacks.onUpdateReady();
          }
          reg.addEventListener("updatefound", function () {
            var installing = reg.installing;
            if (!installing) return;
            installing.addEventListener("statechange", function () {
              if (
                installing.state === "installed" &&
                nav.serviceWorker.controller &&
                callbacks.onUpdateReady
              ) {
                callbacks.onUpdateReady();
              }
            });
          });
          if (callbacks.onControllerChange) {
            nav.serviceWorker.addEventListener("controllerchange", callbacks.onControllerChange);
          }
          return true;
        })
        .catch(function () {
          return false;
        });
    },
    applyServiceWorkerUpdate: function (nav, onNoWaiting) {
      if (!nav || !nav.serviceWorker) return;
      nav.serviceWorker.getRegistration("/").then(function (reg) {
        if (reg && reg.waiting) {
          reg.waiting.postMessage({ type: "SKIP_WAITING" });
        } else if (onNoWaiting) {
          onNoWaiting();
        }
      });
    },
  };

  function loadStyleSet() {
    return uiShellUtils.loadStyleSet(STYLE_KEY, THEME_KEY, KORUS_PALETTE);
  }

  function saveStyleSet() {
    uiShellUtils.saveStyleSet(
      STYLE_KEY,
      THEME_KEY,
      state.appearance,
      KORUS_PALETTE
    );
  }

  function applyStyleSet(set) {
    var applied = uiShellUtils.applyStyleSet(document, set, KORUS_PALETTE);
    state.appearance = applied.appearance;
    state.palette = applied.palette;
  }

  function toggleAppearance() {
    applyStyleSet({
      appearance: state.appearance === "dark" ? "light" : "dark",
      palette: KORUS_PALETTE,
    });
    saveStyleSet();
    render();
  }

  function syncNotifyPref() {
    var pref = uiShellUtils.syncNotifyPref(NOTIF_KEY, SOUND_NOTIF_KEY);
    state.notifyPref = pref.notifyPref;
    state.soundNotify = pref.soundNotify;
  }

  function draftStorageKey(chatId) {
    return uiShellUtils.draftStorageKey(DRAFT_KEY_PREFIX, chatId);
  }

  function loadComposerDraftForChat(chatId) {
    return uiShellUtils.loadComposerDraftForChat(DRAFT_KEY_PREFIX, chatId);
  }

  function saveComposerDraftForChat(chatId, text) {
    uiShellUtils.saveComposerDraftForChat(DRAFT_KEY_PREFIX, chatId, text);
  }

  function clearComposerDraftForChat(chatId) {
    uiShellUtils.clearComposerDraftForChat(DRAFT_KEY_PREFIX, chatId);
  }

  function composerDraftPreview(chatId) {
    return uiShellUtils.composerDraftPreview(DRAFT_KEY_PREFIX, chatId);
  }

  function loadLastPublicLink() {
    return uiShellUtils.loadLastPublicLink(LAST_PUBLIC_LINK_KEY);
  }

  function saveLastPublicLink(link) {
    uiShellUtils.saveLastPublicLink(LAST_PUBLIC_LINK_KEY, link);
  }

  function persistCurrentComposerDraft() {
    var ta = document.getElementById("msgdraft");
    if (ta && state.selectedId) {
      saveComposerDraftForChat(state.selectedId, ta.value);
    }
  }

  function scheduleSaveComposerDraft() {
    if (draftSaveTimer) clearTimeout(draftSaveTimer);
    draftSaveTimer = setTimeout(function () {
      draftSaveTimer = null;
      var ta = document.getElementById("msgdraft");
      if (ta && state.selectedId) {
        saveComposerDraftForChat(state.selectedId, ta.value);
      }
    }, 400);
  }

  function playTone(ctx, freq, startAt, duration, volume) {
    var osc = ctx.createOscillator();
    var gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = freq;
    gain.gain.value = volume;
    osc.start(startAt);
    osc.stop(startAt + duration);
  }

  function playNotifySound() {
    if (!state.soundNotify) return;
    try {
      var ctx = new (window.AudioContext || window.webkitAudioContext)();
      playTone(ctx, 880, ctx.currentTime, 0.12, 0.08);
    } catch (e) {}
  }

  function playIncomingCallChime() {
    if (!state.soundNotify) return;
    try {
      var ctx = new (window.AudioContext || window.webkitAudioContext)();
      var t = ctx.currentTime;
      playTone(ctx, 660, t, 0.18, 0.1);
      playTone(ctx, 880, t + 0.22, 0.22, 0.1);
    } catch (e) {}
  }

  function stopIncomingCallRing() {
    if (incomingRingTimer) {
      clearInterval(incomingRingTimer);
      incomingRingTimer = null;
    }
  }

  function syncIncomingCallRing() {
    stopIncomingCallRing();
    if (!state.incomingRtcCall || !state.soundNotify) return;
    playIncomingCallChime();
    incomingRingTimer = setInterval(playIncomingCallChime, 2800);
  }

  async function enableNotifications() {
    if (typeof Notification === "undefined") {
      state.error = L("notifications.unsupported");
      render();
      return;
    }
    var perm = await Notification.requestPermission();
    state.notifyPref = perm === "granted";
    localStorage.setItem(NOTIF_KEY, state.notifyPref ? "1" : "0");
    if (!state.notifyPref && perm === "denied") {
      state.error = L("notifications.denied");
    }
    if (state.notifyPref) {
      await registerWebPush();
      if (state.webPushError) {
        state.error = state.webPushError;
      }
    }
    render();
  }

  function vapidPublicKey() {
    return uiPwaSettingsUtils.getVapidPublicKey(window);
  }

  function urlBase64ToUint8Array(base64String) {
    return uiPwaSettingsUtils.urlBase64ToUint8Array(base64String);
  }

  function pushTokenFromSubscription(subscription) {
    return uiPwaSettingsUtils.pushTokenFromSubscription(subscription);
  }

  async function resyncWebPush() {
    if (!state.tokens || !vapidPublicKey()) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await unregisterWebPush();
      if (notificationsAllowed()) {
        await registerWebPush();
      }
      await loadMyDevices();
    } catch (e) {
      state.error = e.message || L("notifications.pushResyncFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function registerWebPush() {
    var vapid = vapidPublicKey();
    if (!vapid || !state.tokens) return;
    if (!uiPwaSettingsUtils.canUseWebPush(navigator, window)) return;
    if (!notificationsAllowed()) return;
    try {
      var reg = await navigator.serviceWorker.ready;
      var sub = await reg.pushManager.getSubscription();
      if (!sub) {
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(vapid),
        });
      }
      await apiJson("/me/devices", {
        method: "POST",
        jsonBody: {
          device_name: WEB_DEVICE_NAME,
          push_provider: "web",
          push_token: pushTokenFromSubscription(sub),
        },
      });
      state.webPushRegistered = true;
      state.webPushError = null;
    } catch (e) {
      state.webPushRegistered = false;
      state.webPushError = e.message || L("notifications.pushRegisterFailed");
    }
  }

  function notificationsAllowed() {
    return uiPwaSettingsUtils.notificationsAllowed(state.notifyPref, window.Notification);
  }

  function totalUnreadCount() {
    var n = 0;
    Object.keys(state.unreadByChat || {}).forEach(function (id) {
      n += state.unreadByChat[id] || 0;
    });
    return n;
  }

  function updateDocumentTitle() {
    var base = L("ui.brand.title");
    var u = totalUnreadCount();
    document.title = u > 0 ? "(" + u + ") " + base : base;
    updateAppBadge(u);
  }

  function updateAppBadge(unread) {
    if (!navigator.setAppBadge && !navigator.clearAppBadge) return;
    var n = typeof unread === "number" ? unread : totalUnreadCount();
    try {
      if (n > 0 && navigator.setAppBadge) {
        navigator.setAppBadge(n).catch(function () {});
      } else if (navigator.clearAppBadge) {
        navigator.clearAppBadge().catch(function () {});
      }
    } catch (e) {}
  }

  function maybeNotifyMessage(data) {
    if (!notificationsAllowed() || !isMessageSendEvent(data)) return;
    var myId = jwtSub(state.tokens.access_token);
    if (myId && data.senderId === myId) return;
    if (data.chatId === state.selectedId && !document.hidden) return;
    playNotifySound();
    var title = chatTitleById(data.chatId);
    var previewMsg = messageFromSendEvent(data);
    var body = formatPreviewForMessage(previewMsg);
    try {
      var note = new Notification(title, {
        body: body,
        tag: "korus-msg-" + data.messageId,
        icon: "/webui/favicon.ico",
      });
      note.onclick = function () {
        window.focus();
        note.close();
        if (data.chatId !== state.selectedId) {
          openChatById(data.chatId).catch(function () {});
        } else if (data.messageId) {
          ingestIncomingMessage(data.chatId, data.messageId, data)
            .then(function () {
              state.shouldScrollThread = true;
              render();
            })
            .catch(function () {
              loadThread(data.chatId, THREAD_SOFT_RELOAD)
                .then(function () {
                  state.shouldScrollThread = true;
                  render();
                })
                .catch(function () {});
            });
        }
      };
    } catch (e) {}
  }

  async function loadE2eeStatus() {
    if (!state.tokens) return;
    try {
      var kps = await apiJson("/e2ee/key-packages", { method: "GET" });
      state.serverKeyPackages = Array.isArray(kps) ? kps : [];
      state.e2eeKeyCount = state.serverKeyPackages.length;
    } catch (e) {
      state.e2eeKeyCount = null;
      state.serverKeyPackages = null;
    }
  }

  async function deleteServerKeyPackage(kpId) {
    if (!kpId || !state.tokens) return;
    if (!window.confirm(L("common.deleteKeyPackage"))) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch("/e2ee/key-packages/" + kpId, { method: "DELETE" });
      await loadE2eeStatus();
      state.statusMessage = L("e2ee.keyPackageDeleted");
    } catch (e) {
      state.error = e.message || L("e2ee.deleteKeyPackageFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function revokeLastPublicLink() {
    var link = state.lastPublicLink;
    if (!link || !link.file_id || !link.link_id) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch(
        "/files/" + link.file_id + "/public-links/" + link.link_id,
        { method: "DELETE" }
      );
      state.lastPublicLink = null;
      saveLastPublicLink(null);
      state.statusMessage = L("files.publicLinkRevoked");
    } catch (e) {
      state.error = e.message || L("files.revokeFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadMyDevices() {
    if (!state.tokens) return;
    try {
      var r = await apiJson("/me/devices", { method: "GET" });
      state.myDevices = r && r.devices ? r.devices : [];
    } catch (e) {
      state.myDevices = null;
    }
  }

  async function loadSavedChatId() {
    if (!state.tokens) return;
    try {
      var r = await apiJson("/users/me/saved-chat", { method: "GET" });
      state.savedChatId = r && r.saved_chat_id ? r.saved_chat_id : null;
    } catch (e) {
      state.savedChatId = null;
    }
  }

  async function loadMyProfile(opts) {
    opts = opts || {};
    if (!state.tokens) return null;
    try {
      var p = await apiJson("/users/me", { method: "GET" });
      if (p) {
        if (p.presence_status) state.myPresence = p.presence_status;
        state.myDisplayName = p.display_name || p.username || "";
        if (opts.applyLocale) {
          await applyProfileLocale(p);
        }
      }
      return p || null;
    } catch (e) {}
    return null;
  }

  async function applyProfileLocale(p) {
    var code = p && p.ui_locale;
    var supported = i18n.supportedLocales ? i18n.supportedLocales() : [];
    if (code && supported.indexOf(code) >= 0) {
      if (i18n.getLocale() !== code) {
        await i18n.setLocale(code);
      }
    }
  }

  async function persistUiLocale(code, opts) {
    opts = opts || {};
    if (!state.tokens || !code) return;
    var supported = i18n.supportedLocales ? i18n.supportedLocales() : [];
    if (supported.indexOf(code) < 0) return;
    try {
      await apiJson("/users/me/locale", {
        method: "PATCH",
        jsonBody: { ui_locale: code },
      });
    } catch (e) {
      if (!opts.silent) {
        state.error = e.message || L("profile.localeSaveFailed");
      }
    }
  }

  async function loadContacts() {
    if (!state.tokens) return;
    state.contactsBusy = true;
    try {
      var rows = await apiJson("/contacts", { method: "GET" });
      state.contacts = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.contacts = null;
    } finally {
      state.contactsBusy = false;
    }
  }

  async function addContact(userId) {
    if (!userId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/contacts", { method: "POST", jsonBody: { user_id: userId } });
      if (state.sidebarMode === "contacts") {
        await loadContacts();
      }
    } catch (e) {
      state.error = e.message || L("contacts.addFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function importContactsByPhoneHashes() {
    if (!state.tokens) return;
    var hashes = state.contactImportText
      .split(/[\r\n,;]+/)
      .map(function (s) {
        return s.trim().toLowerCase();
      })
      .filter(function (s) {
        return s.length > 0;
      });
    if (!hashes.length) {
      state.error = L("contacts.hashRequired");
      render();
      return;
    }
    if (hashes.length > 1000) {
      state.error = L("contacts.hashLimit");
      render();
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      var r = await apiJson("/contacts/import", {
        method: "POST",
        jsonBody: { phone_hashes: hashes },
      });
      var found = r && r.contacts && r.contacts.length ? r.contacts.length : 0;
      state.contactImportText = "";
      await loadContacts();
      state.sidebarMode = "contacts";
      state.statusMessage = L("contacts.importOk", { count: found });
      state.error = null;
    } catch (e) {
      state.error = e.message || L("contacts.importFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function removeContact(contactId) {
    if (!contactId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/contacts/" + contactId, { method: "DELETE" });
      await loadContacts();
    } catch (e) {
      state.error = e.message || L("contacts.deleteFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function isPublicAuthPath(path) {
    if (!path) return false;
    return (
      path.indexOf("/auth/login") === 0 ||
      path.indexOf("/auth/register") === 0 ||
      path.indexOf("/auth/refresh") === 0 ||
      path.indexOf("/auth/logout") === 0
    );
  }

  function maybeRefreshSessionTokens() {
    if (!state.tokens || !state.tokens.refresh_token) {
      return Promise.resolve();
    }
    var stored = state.tokens.stored_at_ms || 0;
    var expiresIn = state.tokens.expires_in || 0;
    if (!expiresIn) return Promise.resolve();
    if (Date.now() + 120000 < stored + expiresIn * 1000) {
      return Promise.resolve();
    }
    return tryRefreshTokens();
  }

  function sendHeartbeat() {
    if (!state.tokens || document.visibilityState !== "visible") return;
    lastHeartbeatMs = Date.now();
    maybeRefreshSessionTokens()
      .then(function () {
        return apiFetch("/users/me/heartbeat", { method: "POST" });
      })
      .catch(function () {});
  }

  async function loadServerVersion() {
    try {
      var h = await apiJson("/health", { method: "GET", noAuth: true, noRefresh: true });
      state.serverVersion = h && h.version ? String(h.version) : null;
    } catch (e) {
      state.serverVersion = null;
    }
  }

  function sendHeartbeatThrottled() {
    if (!state.tokens || document.visibilityState !== "visible") return;
    var now = Date.now();
    if (now - lastHeartbeatMs < HEARTBEAT_WS_MIN_MS) return;
    sendHeartbeat();
  }

  function startHeartbeat() {
    stopHeartbeat();
    sendHeartbeat();
    heartbeatTimer = setInterval(sendHeartbeat, HEARTBEAT_INTERVAL_MS);
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function startTtlRenderTicker() {
    if (ttlRenderTimer) return;
    ttlRenderTimer = setInterval(function () {
      if (!state.tokens || !state.messages || !state.messages.length) return;
      scheduleRender();
    }, 60000);
  }

  async function saveMyProfile() {
    if (!state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var p = await apiJson("/users/me", {
        method: "PATCH",
        jsonBody: { display_name: state.myDisplayName.trim() || null, phone: null },
      });
      if (p && p.display_name) state.myDisplayName = p.display_name;
    } catch (e) {
      state.error = e.message || L("profile.saveFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function currentChat() {
    if (!state.selectedId) return null;
    return (
      state.chats.find(function (c) {
        return c.id === state.selectedId;
      }) || null
    );
  }

  async function toggleChatMute() {
    var chat = currentChat();
    if (!chat || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var next = !chat.muted;
      await apiJson("/chats/" + chat.id + "/mute", {
        method: "POST",
        jsonBody: { muted: next },
      });
      chat.muted = next;
    } catch (e) {
      state.error = e.message || L("profile.notifyModeFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function togglePersonalFilter() {
    var chat = currentChat();
    if (!chat || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var next = !chat.personal_filter_active;
      await apiJson("/chats/" + chat.id + "/personal-filter", {
        method: "PATCH",
        jsonBody: { active: next },
      });
      chat.personal_filter_active = next;
    } catch (e) {
      state.error = e.message || L("profile.filterFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function myChatRole(members) {
    if (!members || !state.tokens) return null;
    var me = jwtSub(state.tokens.access_token);
    var row = members.find(function (m) {
      return m.user_id === me;
    });
    return row ? row.role : null;
  }

  function canManageChatBans(role) {
    return role === "owner" || role === "admin";
  }

  function canManageMembers(role) {
    return role === "owner" || role === "admin";
  }

  async function renameGroupChat() {
    var chat = currentChat();
    if (!chat || chat.type !== "group" || !state.tokens) return;
    var title = window.prompt(L("chat.groupTitlePrompt"), chat.title || "");
    if (title === null) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var updated = await apiJson("/chats/" + chat.id, {
        method: "PATCH",
        jsonBody: { title: title.trim() },
      });
      if (updated && updated.title) chat.title = updated.title;
      await refreshChats();
      state.statusMessage = L("chat.groupTitleUpdated");
    } catch (e) {
      state.error = e.message || L("chat.renameFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function addMemberToChat() {
    if (!state.selectedId || !state.tokens) return;
    var uid = window.prompt(L("chat.addMemberPrompt"));
    if (!uid || !uid.trim()) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/members", {
        method: "POST",
        jsonBody: { user_id: uid.trim() },
      });
      await loadChatMembersModal();
      await refreshChats();
      state.statusMessage = L("chat.memberAdded");
    } catch (e) {
      state.error = e.message || L("chat.addMemberFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function removeMemberFromChat(userId) {
    if (!state.selectedId || !userId || !state.tokens) return;
    var meId = jwtSub(state.tokens.access_token);
    var leavingSelf = userId === meId;
    if (
      !leavingSelf &&
      !window.confirm(L("common.removeMember"))
    ) {
      return;
    }
    if (leavingSelf && !window.confirm(L("common.leaveGroup"))) {
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/members/" + userId, {
        method: "DELETE",
      });
      if (leavingSelf) {
        closeMembersModal();
        state.selectedId = null;
        state.messages = [];
        await refreshChats();
        state.statusMessage = L("chat.leftGroup");
      } else {
        await loadChatMembersModal();
        await refreshChats();
        state.statusMessage = L("chat.memberRemoved");
      }
    } catch (e) {
      state.error = e.message || L("chat.removeMemberFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function setMemberRole(userId, role) {
    if (!state.selectedId || !userId || !role || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/members/" + userId + "/role", {
        method: "PATCH",
        jsonBody: { role: role },
      });
      await loadChatMembersModal();
      state.statusMessage = L("chat.roleUpdated");
    } catch (e) {
      state.error = e.message || L("chat.roleUpdateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function copyConferenceLink() {
    if (!state.activeConference || !state.activeConference.join_url) return;
    copyConferenceLinkToClipboard(state.activeConference.join_url, false);
  }

  async function loadChatMembersModal() {
    if (!state.selectedId || !state.tokens) return;
    state.chatMembersBusy = true;
    state.chatMembers = null;
    state.chatBans = null;
    render();
    try {
      var members = await apiJson("/chats/" + state.selectedId + "/members", { method: "GET" });
      state.chatMembers = Array.isArray(members) ? members : [];
      var role = myChatRole(state.chatMembers);
      if (canManageChatBans(role)) {
        try {
          var bans = await apiJson("/chats/" + state.selectedId + "/bans", { method: "GET" });
          state.chatBans = Array.isArray(bans) ? bans : [];
        } catch (e) {
          state.chatBans = [];
        }
      }
    } catch (e) {
      state.error = e.message || L("chat.loadMembersFailed");
      state.membersModalOpen = false;
    } finally {
      state.chatMembersBusy = false;
      render();
    }
  }

  function openMembersModal() {
    state.membersModalOpen = true;
    loadChatMembersModal();
  }

  function closeMembersModal() {
    state.membersModalOpen = false;
    state.chatMembers = null;
    state.chatBans = null;
    render();
  }

  async function banUserInChat(userId) {
    if (!state.selectedId || !userId || !state.tokens) return;
    var reason = window.prompt(L("chat.banReasonPrompt")) || "";
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/bans", {
        method: "POST",
        jsonBody: { user_id: userId, reason: reason.trim() || null },
      });
      await loadChatMembersModal();
    } catch (e) {
      state.error = e.message || L("chat.banFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function unbanUserInChat(userId) {
    if (!state.selectedId || !userId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/bans/" + userId, { method: "DELETE" });
      await loadChatMembersModal();
    } catch (e) {
      state.error = e.message || L("chat.unbanFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function conferenceRowFromEvent(data) {
    return {
      conference_id: data.conference_id,
      chat_id: data.chat_id,
      title: data.title || "",
      status: data.status || "active",
      room_slug: data.room_slug,
      join_url: data.join_url,
      provider: data.provider || "jitsi",
      participant_count:
        typeof data.participant_count === "number" ? data.participant_count : 0,
    };
  }

  function conferenceParticipantsLabel(count) {
    if (typeof count !== "number" || count < 1) return "";
    if (count === 1) return " · 1 уч.";
    return " · " + count + " уч.";
  }

  function conferenceParticipantLabel(p) {
    if (!p) return "";
    var name = (p.display_name && String(p.display_name).trim()) || p.username;
    if (name) return name;
    return (p.user_id || "").slice(0, 8) + "…";
  }

  function conferenceParticipantNamesSummary(list, max) {
    if (!list || !list.length) return "";
    var cap = typeof max === "number" ? max : 4;
    var names = list.slice(0, cap).map(conferenceParticipantLabel);
    if (list.length > cap) names.push("+" + (list.length - cap));
    return names.join(", ");
  }

  async function loadConferenceParticipants(conferenceId) {
    if (!conferenceId || !state.tokens) {
      state.conferenceParticipantsList = null;
      state.conferenceParticipantsConfId = null;
      return;
    }
    try {
      var rows = await apiJson("/conferences/" + conferenceId + "/participants", {
        method: "GET",
      });
      state.conferenceParticipantsList = Array.isArray(rows) ? rows : [];
      state.conferenceParticipantsConfId = conferenceId;
    } catch (e) {
      state.conferenceParticipantsList = [];
      state.conferenceParticipantsConfId = conferenceId;
    }
  }

  function clearConferenceParticipants() {
    state.conferenceParticipantsList = null;
    state.conferenceParticipantsConfId = null;
  }

  function conferencePreviewLabel(conf) {
    var base = conf.title || L("conference.defaultVideoTitle");
    return "🎥 " + base + conferenceParticipantsLabel(conf.participant_count);
  }

  function getOrCreateJitsiIframe() {
    if (!state.jitsiIframeEl) {
      state.jitsiIframeEl = document.createElement("iframe");
      state.jitsiIframeEl.className = "call-jitsi-frame";
      state.jitsiIframeEl.title = "Jitsi Meet";
      state.jitsiIframeEl.allow =
        "camera; microphone; fullscreen; display-capture; autoplay; clipboard-write";
      state.jitsiIframeEl.referrerPolicy = "no-referrer-when-downgrade";
    }
    return state.jitsiIframeEl;
  }

  function clearJitsiIframe() {
    if (state.jitsiIframeEl) {
      state.jitsiIframeEl.src = "about:blank";
    }
  }

  function reloadJitsiIframe() {
    var url = state.activeConference && state.activeConference.join_url;
    if (!url) return;
    var iframe = getOrCreateJitsiIframe();
    iframe.src = "about:blank";
    setTimeout(function () {
      iframe.src = url;
    }, 80);
  }

  function syncActiveConferenceMapFromList(rows) {
    var map = {};
    (rows || []).forEach(function (c) {
      if (c && c.chat_id && c.status === "active") {
        map[c.chat_id] = c;
      }
    });
    state.activeConferenceByChat = map;
  }

  function setActiveConferenceForChat(chatId, conf) {
    if (!chatId) return;
    if (conf && conf.status === "active") {
      state.activeConferenceByChat[chatId] = conf;
    } else {
      delete state.activeConferenceByChat[chatId];
    }
  }

  function activeConferenceInChat(chatId) {
    return chatId ? state.activeConferenceByChat[chatId] : null;
  }

  async function loadActiveConferences() {
    if (!state.tokens) {
      state.activeConferenceByChat = {};
      return;
    }
    try {
      var rows = await apiJson("/conferences/active", { method: "GET" });
      syncActiveConferenceMapFromList(Array.isArray(rows) ? rows : []);
    } catch (e) {}
  }

  async function loadChatConferences() {
    if (!state.selectedId || !state.tokens) {
      state.chatConferences = null;
      return;
    }
    try {
      var rows = await apiJson(
        "/chats/" + state.selectedId + "/conferences?active_only=true",
        { method: "GET" }
      );
      state.chatConferences = Array.isArray(rows) ? rows : [];
      setActiveConferenceForChat(
        state.selectedId,
        state.chatConferences.length ? state.chatConferences[0] : null
      );
    } catch (e) {
      state.chatConferences = [];
      setActiveConferenceForChat(state.selectedId, null);
    }
  }

  async function joinConferenceFromBanner(conf) {
    if (!conf) return;
    state.callPanelOpen = true;
    await joinJitsiConference(conf);
  }

  function stopMeshCallMedia() {
    Object.keys(state.rtcPeers).forEach(function (pid) {
      var pc = state.rtcPeers[pid];
      if (pc) {
        try {
          pc.close();
        } catch (e) {}
      }
    });
    state.rtcPeers = {};
    state.rtcPendingCandidates = {};
    stopCallMedia();
  }

  async function joinJitsiConference(conf) {
    if (!conf) return;
    ensureCallPanelOpen();
    state.callMode = "jitsi";
    if (conf.conference_id) {
      state.conferenceBusy = true;
      state.error = null;
      render();
      try {
        await apiJson("/conferences/" + conf.conference_id + "/join", { method: "POST" });
        stopMeshCallMedia();
        state.activeConference = conf;
        state.callMode = "jitsi";
        await loadActiveConferences();
        if (state.selectedId) await loadChatConferences();
        await loadConferenceParticipants(conf.conference_id);
      } catch (e) {
        state.error = localErr(e.message) || L("conference.joinFailed");
      } finally {
        state.conferenceBusy = false;
        render();
      }
      return;
    }
    if (conf.join_url) {
      stopMeshCallMedia();
      state.activeConference = conf;
      state.callMode = "jitsi";
      render();
    }
  }

  async function joinConferenceByLink() {
    if (!state.tokens) return;
    var raw = window.prompt(L("conference.pasteLinkPrompt")) || "";
    var parsed = parseConferenceLinkInput(raw);
    if (!parsed.uuid && !parsed.slug) return;
    state.error = null;
    ensureCallPanelOpen();
    state.conferenceBusy = true;
    render();
    try {
      if (parsed.uuid) {
        var byId = await apiJson("/conferences/" + parsed.uuid, { method: "GET" });
        await joinJitsiConference(byId);
        return;
      }
      try {
        var byRoom = await apiJson(
          "/conferences/by-room/" + encodeURIComponent(parsed.slug),
          { method: "GET" }
        );
        await joinJitsiConference(byRoom);
        return;
      } catch (lookupErr) {
        var known = null;
        listUserActiveConferences().forEach(function (c) {
          if (known) return;
          if (parsed.url && c.join_url === parsed.url) known = c;
          else if (c.room_slug === parsed.slug) known = c;
        });
        if (known) {
          await joinJitsiConference(known);
          return;
        }
      }
      await joinJitsiConference({
        join_url: parsed.url || buildGuestJitsiUrl(parsed.slug),
        title: L("conference.guestMeeting"),
        status: "active",
        room_slug: parsed.slug,
      });
    } catch (e) {
      state.error = localErr(e.message) || L("conference.joinFailed");
      render();
    } finally {
      state.conferenceBusy = false;
      render();
    }
  }

  async function leaveActiveConference() {
    if (!state.activeConference || !state.tokens) return;
    if (!conferenceIsTracked(state.activeConference)) {
      state.activeConference = null;
      clearConferenceParticipants();
      clearJitsiIframe();
      render();
      return;
    }
    var id = state.activeConference.conference_id;
    state.activeConference = null;
    clearConferenceParticipants();
    clearJitsiIframe();
    try {
      await apiJson("/conferences/" + id + "/leave", { method: "POST" });
    } catch (e) {}
    if (state.callMode === "jitsi") {
      state.callMode = "jitsi";
    }
    await loadChatConferences();
    await loadActiveConferences();
    render();
  }

  async function endActiveConference() {
    if (!state.activeConference || !state.tokens) return;
    if (!conferenceIsTracked(state.activeConference)) {
      await leaveActiveConference();
      return;
    }
    var id = state.activeConference.conference_id;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/conferences/" + id + "/end", { method: "POST" });
      state.activeConference = null;
      state.callMode = "jitsi";
      clearConferenceParticipants();
      clearJitsiIframe();
      if (state.selectedId) setActiveConferenceForChat(state.selectedId, null);
      await loadChatConferences();
      await loadActiveConferences();
    } catch (e) {
      state.error = localErr(e.message) || L("conference.endFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function createConferenceInChat() {
    if (!state.tokens || !state.selectedId) return;
    var title = window.prompt(L("conference.titlePrompt")) || "";
    state.conferenceBusy = true;
    state.error = null;
    ensureCallPanelOpen();
    render();
    try {
      var conf = await apiJson("/chats/" + state.selectedId + "/conferences", {
        method: "POST",
        jsonBody: { title: title.trim() || null },
      });
      await loadActiveConferences();
      await loadChatConferences();
      if (conf) {
        await joinJitsiConference(conf);
        copyConferenceLinkToClipboard(conf.join_url, true);
        state.statusMessage = L("conference.created");
      }
    } catch (e) {
      state.error = localErr(e.message) || L("conference.createFailed");
    } finally {
      state.conferenceBusy = false;
      render();
    }
  }

  async function createConference() {
    if (!state.tokens) return;
    var title = window.prompt(L("conference.titlePrompt")) || "";
    var memberRaw = window.prompt(L("conference.inviteMembersPrompt")) || "";
    var memberIds = parseMemberIdList(memberRaw);
    state.conferenceBusy = true;
    state.error = null;
    ensureCallPanelOpen();
    render();
    try {
      var conf = await apiJson("/conferences", {
        method: "POST",
        jsonBody: {
          title: title.trim() || null,
          member_ids: memberIds.length ? memberIds : null,
        },
      });
      if (conf && conf.chat_id) {
        try {
          await postMeetingInviteMessage(conf.chat_id, conf);
        } catch (postErr) {}
        if (conf.chat_id !== state.selectedId) {
          try {
            await openChatById(conf.chat_id);
          } catch (openErr) {}
        }
      }
      await loadActiveConferences();
      await loadChatConferences();
      if (conf) {
        await joinJitsiConference(conf);
        copyConferenceLinkToClipboard(conf.join_url, true);
        state.statusMessage = L("conference.created");
        render();
      }
    } catch (e) {
      state.error = localErr(e.message) || L("conference.createFailed");
    } finally {
      state.conferenceBusy = false;
      render();
    }
  }

  async function switchCallMode(mode) {
    if (mode === state.callMode) return;
    if (mode === "jitsi") {
      stopMeshCallMedia();
      state.callMode = "jitsi";
      render();
      return;
    }
    if (!meshCallChatReady()) {
      state.error = L("conference.meshNeedsChat");
      render();
      return;
    }
    if (state.activeConference) {
      await leaveActiveConference();
    }
    state.callMode = "mesh";
    try {
      await ensureCallStream();
      await loadRtcPeerIds();
      startThumbCapture();
      attachLocalVideo();
      setTimeout(function () {
        beginRtcMesh();
      }, 120);
    } catch (e) {
      state.error = localErr(e.message) || L("conference.meshUnavailable");
    }
    render();
  }

  async function saveMessageToVault(m) {
    if (!m || !state.selectedId) return;
    if (state.selectedId === state.savedChatId) {
      state.error = L("saved.alreadyInVault");
      render();
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      if (!state.savedChatId) await loadSavedChatId();
      if (!state.savedChatId) throw new Error(L("chat.savedNotFound"));
      var sent = await apiJson(
        "/chats/" + state.selectedId + "/messages/" + m.id + "/forward",
        { method: "POST", jsonBody: { target_chat_id: state.savedChatId } }
      );
      if (sent && sent.chat_id) setChatPreviewFromMessage(sent.chat_id, sent);
      if (state.selectedId === state.savedChatId && sent) {
        await afterLocalSend(state.savedChatId, sent);
      }
      state.statusMessage = L("saved.savedToVault");
    } catch (e) {
      state.error = e.message || L("saved.saveFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadBlockedUsers() {
    if (!state.tokens) return;
    try {
      var rows = await apiJson("/blocks", { method: "GET" });
      state.blockedUsers = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.blockedUsers = null;
    }
  }

  async function updatePresence(status) {
    if (!state.tokens || !status) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var p = await apiJson("/users/me/presence", {
        method: "PATCH",
        jsonBody: { presence_status: status },
      });
      if (p && p.presence_status) {
        state.myPresence = p.presence_status;
      } else {
        state.myPresence = status;
      }
    } catch (e) {
      state.error = e.message || L("profile.presenceUpdateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function blockUser(userId) {
    if (!userId || !state.tokens) return;
    if (!window.confirm(L("common.blockUser"))) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/blocks", { method: "POST", jsonBody: { user_id: userId } });
      state.userSearchHits = null;
      state.sidebarSearch = "";
      await loadBlockedUsers();
      await refreshChats();
    } catch (e) {
      state.error = e.message || L("profile.blockFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function unblockUser(userId) {
    if (!userId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch("/blocks/" + encodeURIComponent(userId), { method: "DELETE" });
      await loadBlockedUsers();
      state.statusMessage = L("profile.userUnblocked");
    } catch (e) {
      state.error = e.message || L("profile.unblockFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function unregisterDevice(deviceName) {
    if (!deviceName || !state.tokens) return;
    if (
      deviceName === WEB_DEVICE_NAME &&
      !window.confirm(L("common.disablePush"))
    ) {
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch("/me/devices/" + encodeURIComponent(deviceName), { method: "DELETE" });
      if (deviceName === WEB_DEVICE_NAME) {
        state.webPushRegistered = false;
      }
      await loadMyDevices();
      state.statusMessage = L("profile.deviceDisconnected", { name: deviceName });
    } catch (e) {
      state.error = e.message || L("profile.disconnectFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function deleteOwnFile(fileId) {
    if (!fileId || !state.tokens) return;
    if (!window.confirm(L("common.deleteFile"))) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch("/files/" + fileId, { method: "DELETE" });
      state.statusMessage = L("files.deleted");
      render();
    } catch (e) {
      state.error = e.message || L("files.deleteFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function copyMessageText(m) {
    if (!m) return;
    var chatId = m.chat_id || state.selectedId;
    function doCopy(text) {
      if (!text || !String(text).trim()) {
        state.error = L("files.nothingToCopy");
        render();
        return;
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(String(text)).then(
          function () {
            state.statusMessage = L("files.textCopied");
            render();
          },
          function () {
            state.error = L("files.copyFailed");
            render();
          }
        );
      } else {
        state.error = L("conference.clipboardUnavailable");
        render();
      }
    }
    var attachId = messageAttachmentFileId(m);
    if (attachId) {
      doCopy(attachId);
      return;
    }
    if (isE2eeType(m.type) && chatId) {
      loadE2eePlaintext(chatId, m.id).then(function (text) {
        doCopy(text || m.content || "");
      });
      return;
    }
    var text = "";
    if (m.type === "text") {
      text = m.content || "";
    } else if (isUuidString((m.content || "").trim())) {
      text = m.content.trim();
    } else {
      text = m.content || "";
    }
    doCopy(text);
  }

  async function refreshChatMeta(chatId) {
    if (!chatId || !state.tokens) return;
    try {
      var chat = await apiJson("/chats/" + chatId, { method: "GET" });
      var i = state.chats.findIndex(function (c) {
        return c.id === chatId;
      });
      if (i >= 0) state.chats[i] = chat;
      else state.chats.push(chat);
    } catch (e) {}
  }

  async function refreshCurrentThread() {
    if (!state.selectedId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await refreshChatMeta(state.selectedId);
      await loadThread(state.selectedId, { keepScroll: true });
      await markChatRead(state.selectedId);
      await loadUnreadCounts();
      state.statusMessage = L("chat.updated");
    } catch (e) {
      state.error = e.message || L("chat.updateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function markAllChatsRead() {
    if (!state.tokens || !state.chats.length) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await Promise.all(
        state.chats.map(function (c) {
          if ((state.unreadByChat[c.id] || 0) <= 0) return Promise.resolve();
          return markChatRead(c.id);
        })
      );
      await loadUnreadCounts();
      state.statusMessage = L("chat.markAllRead");
    } catch (e) {
      state.error = e.message || L("chat.markAllReadFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function openMessageVersions(m) {
    if (!m || !m.id || !state.selectedId || !m.edited_at) return;
    state.messageVersionsOpen = true;
    state.messageVersionsMsgId = m.id;
    state.messageVersions = null;
    state.messageVersionsBusy = true;
    render();
    try {
      var rows = await apiJson(
        "/chats/" + state.selectedId + "/messages/" + m.id + "/versions",
        { method: "GET" }
      );
      state.messageVersions = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.error = e.message || L("chat.historyLoadFailed");
      state.messageVersionsOpen = false;
    } finally {
      state.messageVersionsBusy = false;
      render();
    }
  }

  function closeMessageVersionsModal() {
    state.messageVersionsOpen = false;
    state.messageVersions = null;
    state.messageVersionsMsgId = null;
    render();
  }

  function focusComposer() {
    var ta = document.getElementById("msgdraft");
    if (!ta) return;
    ta.focus();
    var len = ta.value.length;
    ta.selectionStart = len;
    ta.selectionEnd = len;
  }

  function navigateFilteredChat(delta) {
    var fc = filteredChats();
    if (!fc.length) return;
    var idx = -1;
    for (var i = 0; i < fc.length; i++) {
      if (fc[i].id === state.selectedId) {
        idx = i;
        break;
      }
    }
    if (idx < 0) {
      openChatById(fc[delta > 0 ? 0 : fc.length - 1].id);
      return;
    }
    var next = fc[idx + delta];
    if (next) openChatById(next.id);
  }

  function focusGlobalSearch() {
    var inp = document.querySelector(".global-search-input");
    if (inp) {
      inp.focus();
      inp.select();
    }
  }

  function setupKeyboardShortcuts() {
    document.addEventListener("keydown", function (e) {
      if (!state.tokens) return;
      var tag = (e.target && e.target.tagName) || "";
      var inField =
        tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || e.target.isContentEditable;
      if (e.ctrlKey && e.key === "k") {
        e.preventDefault();
        focusGlobalSearch();
        return;
      }
      if (e.ctrlKey && e.key === ",") {
        e.preventDefault();
        toggleSettings();
        return;
      }
      if (!inField && e.key === "/") {
        e.preventDefault();
        focusGlobalSearch();
        return;
      }
      if (!inField && e.altKey && e.key === "ArrowUp") {
        e.preventDefault();
        navigateFilteredChat(-1);
        return;
      }
      if (!inField && e.altKey && e.key === "ArrowDown") {
        e.preventDefault();
        navigateFilteredChat(1);
        return;
      }
      if (!inField && e.key === "m" && state.selectedId) {
        e.preventDefault();
        focusComposer();
      }
    });
  }

  function setupEscapeHandler() {
    document.addEventListener("keydown", function (e) {
      if (e.key !== "Escape") return;
      if (state.forwardPick) {
        closeForwardPicker();
        return;
      }
      if (state.membersModalOpen) {
        closeMembersModal();
        return;
      }
      if (state.messageVersionsOpen) {
        closeMessageVersionsModal();
        return;
      }
      if (state.fileLinksOpen) {
        closeFilePublicLinksModal();
        return;
      }
      if (state.settingsOpen) {
        state.settingsOpen = false;
        render();
        return;
      }
      if (state.incomingRtcCall) {
        declineIncomingRtcCall();
      }
    });
  }

  function testLocalNotification() {
    if (!notificationsAllowed()) {
      state.error = L("notifications.enableFirst");
      render();
      return;
    }
    try {
      new Notification("Korus Messenger", {
        body: L("notifications.testLocalBody"),
        icon: "/icon.svg",
        tag: "korus-test-local",
      });
    } catch (e) {
      state.error = e.message || L("notifications.showFailed");
      render();
    }
  }

  function openChatById(chatId, options) {
    options = options || {};
    if (!chatId) return Promise.resolve();
    persistCurrentComposerDraft();
    if (
      state.activeConference &&
      state.activeConference.chat_id &&
      state.activeConference.chat_id !== chatId
    ) {
      leaveActiveConference().catch(function () {});
    }
    rtcHangupAll();
    var sameChat = state.selectedId === chatId && state.messages.length > 0;
    if (!sameChat) clearConferenceParticipants();
    state.selectedId = chatId;
    state.error = null;
    clearReplyTo();
    state.threadSearch = "";
    state.threadSearchHits = null;
    if (sameChat && !options.forceReload) {
      return markChatRead(chatId)
        .then(function () {
          return refreshChatMeta(chatId);
        })
        .then(function () {
          if (state.callPanelOpen) return loadChatConferences();
        })
        .then(function () {
          openWs();
          render();
        })
        .catch(function (err) {
          state.error = err.message;
          render();
        });
    }
    return loadThread(chatId)
      .then(function () {
        return markChatRead(chatId);
      })
      .then(function () {
        return refreshChatMeta(chatId);
      })
      .then(function () {
        if (state.callPanelOpen) {
          return loadChatConferences();
        }
      })
      .then(function () {
        openWs();
        render();
      })
      .catch(function (err) {
        state.error = err.message;
        render();
      });
  }

  function openSavedVault() {
    if (state.savedChatId) {
      openChatById(state.savedChatId);
      return;
    }
    loadSavedChatId().then(function () {
      if (state.savedChatId) {
        openChatById(state.savedChatId);
      } else {
        state.error = L("chat.savedNotFound");
        render();
      }
    });
  }

  async function unregisterWebPush() {
    if ("serviceWorker" in navigator) {
      try {
        var reg = await navigator.serviceWorker.ready;
        var sub = await reg.pushManager.getSubscription();
        if (sub) await sub.unsubscribe();
      } catch (e) {}
    }
    if (state.tokens) {
      try {
        await apiFetch("/me/devices/" + encodeURIComponent(WEB_DEVICE_NAME), {
          method: "DELETE",
        });
      } catch (e) {}
    }
    state.webPushRegistered = false;
    state.webPushError = null;
  }

  function checkForServiceWorkerUpdate() {
    uiPwaSettingsUtils.nextServiceWorkerUpdatePromise(navigator);
  }

  async function resetAppUiCache() {
    if (!window.confirm(L("common.resetUiCache"))) {
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      if ("caches" in window) {
        var keys = await caches.keys();
        await Promise.all(
          keys.map(function (k) {
            return caches.delete(k);
          })
        );
      }
      if ("serviceWorker" in navigator) {
        var reg = await navigator.serviceWorker.getRegistration("/");
        if (reg) await reg.unregister();
      }
      window.location.reload();
    } catch (e) {
      state.error = (e && e.message) || L("e2ee.cacheResetFailed");
      state.busy = false;
      render();
    }
  }

  function navigateFromPushUrl(url) {
    if (!url || !state.tokens) return;
    try {
      var u = new URL(url, window.location.origin);
      var chatId = u.searchParams.get("chat");
      if (!chatId) return;
      var msgId = u.searchParams.get("msg");
      if (chatId === state.selectedId && !msgId && !document.hidden) return;
      rtcHangupAll();
      openChatById(chatId)
        .then(function () {
          if (!msgId) return;
          return scrollToMessageId(msgId);
        })
        .catch(function (e) {
          if (e && e.message) state.error = e.message;
          render();
        });
    } catch (e) {}
  }

  function setupServiceWorkerMessages() {
    if (!("serviceWorker" in navigator)) return;
    navigator.serviceWorker.addEventListener("message", function (ev) {
      var data = ev.data;
      if (!data || !data.type) return;
      if (data.type === "korus-push" && data.payload && data.payload.url) {
        navigateFromPushUrl(data.payload.url);
      }
      if (data.type === "korus-navigate" && data.url) {
        navigateFromPushUrl(data.url);
      }
    });
  }

  function toggleSettings() {
    state.settingsOpen = !state.settingsOpen;
    if (state.settingsOpen && state.tokens) {
      Promise.all([
        loadLocalKeyPackageMeta(),
        loadE2eeStatus(),
        loadMyDevices(),
        loadMyProfile(),
        loadBlockedUsers(),
        loadMyPublicLinks(),
      ])
        .then(render)
        .catch(function () {
          render();
        });
      return;
    }
    render();
  }

  function markSwUpdateReady() {
    if (!state.swUpdateReady) {
      state.swUpdateReady = true;
      render();
    }
  }

  function applyServiceWorkerUpdate() {
    if (!uiPwaSettingsUtils.canUseServiceWorker(navigator)) return;
    pendingSwReload = true;
    uiPwaSettingsUtils.applyServiceWorkerUpdate(navigator, function () {
      pendingSwReload = false;
      state.swUpdateReady = false;
      render();
    });
  }

  function registerServiceWorker() {
    uiPwaSettingsUtils.registerServiceWorker(navigator, window, {
      onUpdateReady: markSwUpdateReady,
      onControllerChange: function () {
        if (!pendingSwReload) return;
        pendingSwReload = false;
        window.location.reload();
      },
    });
  }

  function openIdb() {
    return new Promise(function (resolve, reject) {
      if (!window.indexedDB) {
        reject(new Error("IndexedDB недоступен"));
        return;
      }
      var req = indexedDB.open(IDB_NAME, 1);
      req.onupgradeneeded = function () {
        req.result.createObjectStore(IDB_STORE);
      };
      req.onsuccess = function () {
        resolve(req.result);
      };
      req.onerror = function () {
        reject(req.error);
      };
    });
  }

  function idbGet(key) {
    return openIdb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(IDB_STORE, "readonly");
        var req = tx.objectStore(IDB_STORE).get(key);
        req.onsuccess = function () {
          db.close();
          resolve(req.result);
        };
        req.onerror = function () {
          reject(req.error);
        };
      });
    });
  }

  function idbSet(key, value) {
    return openIdb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(IDB_STORE, "readwrite");
        tx.objectStore(IDB_STORE).put(value, key);
        tx.oncomplete = function () {
          db.close();
          resolve();
        };
        tx.onerror = function () {
          reject(tx.error);
        };
      });
    });
  }

  function idbDelete(key) {
    return openIdb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(IDB_STORE, "readwrite");
        tx.objectStore(IDB_STORE).delete(key);
        tx.oncomplete = function () {
          db.close();
          resolve();
        };
        tx.onerror = function () {
          reject(tx.error);
        };
      });
    });
  }

  async function loadLocalKeyPackageMeta() {
    try {
      var row = await idbGet(IDB_KEY_LOCAL_KP);
      if (row && row.private_key) {
        state.localKeyPackageMeta = {
          created_at: row.created_at || null,
          public_key_prefix: row.public_key
            ? String(row.public_key).slice(0, 12) + "…"
            : null,
        };
      } else {
        state.localKeyPackageMeta = null;
      }
    } catch (e) {
      state.localKeyPackageMeta = null;
    }
  }

  async function wipeLocalKeyPackage() {
    if (!window.confirm(L("common.deleteLocalKey"))) return;
    try {
      await idbDelete(IDB_KEY_LOCAL_KP);
      state.localKeyPackageMeta = null;
    } catch (e) {
      state.error = L("e2ee.deleteKeyFailed");
    }
    render();
  }

  async function exportLocalKeyPackage() {
    try {
      var row = await idbGet(IDB_KEY_LOCAL_KP);
      if (!row || !row.private_key) {
        state.error = L("e2ee.noLocalKey");
        render();
        return;
      }
      var blob = new Blob([JSON.stringify(row, null, 2)], {
        type: "application/json;charset=utf-8",
      });
      var url = URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      a.download =
        "korus-key-package-" + new Date().toISOString().slice(0, 10) + ".json";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      state.error = L("e2ee.exportKeyFailed");
      render();
    }
  }

  function importLocalKeyPackage() {
    var inp = document.createElement("input");
    inp.type = "file";
    inp.accept = "application/json,.json";
    inp.style.display = "none";
    inp.onchange = function () {
      var file = inp.files && inp.files[0];
      inp.remove();
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () {
        (async function () {
          try {
            var data = JSON.parse(String(reader.result || ""));
            if (!data || !data.private_key) {
              throw new Error(L("e2ee.noPrivateKeyInFile"));
            }
            await idbSet(IDB_KEY_LOCAL_KP, data);
            await loadLocalKeyPackageMeta();
            state.error = null;
          } catch (e) {
            state.error = e.message || L("e2ee.invalidKeyFile");
          }
          render();
        })();
      };
      reader.readAsText(file);
    };
    document.body.appendChild(inp);
    inp.click();
  }

  function setupConnectivityHandlers() {
    function setNetworkOnline(on) {
      if (state.networkOnline === on) return;
      state.networkOnline = on;
      render();
      if (!on || !state.tokens) return;
      refreshChats().catch(function () {});
      if (state.selectedId) {
        loadThread(state.selectedId, THREAD_SOFT_RELOAD).catch(function () {});
      }
      openWs();
    }
    window.addEventListener("online", function () {
      setNetworkOnline(true);
    });
    window.addEventListener("offline", function () {
      setNetworkOnline(false);
    });
    document.addEventListener("visibilitychange", function () {
      if (document.visibilityState !== "visible" || !state.tokens) return;
      sendHeartbeat();
      checkForServiceWorkerUpdate();
      softRefreshChats().catch(function () {});
      if (state.selectedId) {
        loadThread(state.selectedId, THREAD_SOFT_RELOAD).catch(function () {});
      }
      openWs();
      if (notificationsAllowed() && vapidPublicKey() && !state.webPushRegistered) {
        registerWebPush()
          .then(function () {
            if (state.settingsOpen) render();
          })
          .catch(function () {});
      }
    });
  }

  async function createAndUploadKeyPackage() {
    if (!state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var keys = await apiJson("/e2ee/generate", { method: "POST", jsonBody: {} });
      if (!keys || !keys.public_key || !keys.signature_key) {
        throw new Error(L("e2ee.serverNoKeys"));
      }
      await apiJson("/e2ee/key-packages", {
        method: "POST",
        jsonBody: {
          public_key: keys.public_key,
          signature_key: keys.signature_key,
          cipher_suite: "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
          protocol_version: "mls10",
        },
      });
      if (keys.private_key && window.indexedDB) {
        await idbSet(IDB_KEY_LOCAL_KP, {
          created_at: Date.now(),
          private_key: keys.private_key,
          public_key: keys.public_key,
        });
        state.localKeyPackageMeta = {
          created_at: Date.now(),
          public_key_prefix: String(keys.public_key).slice(0, 12) + "…",
        };
      }
      await loadE2eeStatus();
    } catch (err) {
      state.error = err.message || L("e2ee.keyPackageCreateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function promptInstallPwa() {
    if (!deferredInstallPrompt) return;
    deferredInstallPrompt.prompt();
    try {
      await deferredInstallPrompt.userChoice;
    } catch (e) {}
    deferredInstallPrompt = null;
    state.pwaInstallPrompt = null;
    render();
  }

  function setupPwaInstallCapture() {
    window.addEventListener("beforeinstallprompt", function (e) {
      e.preventDefault();
      deferredInstallPrompt = e;
      state.pwaInstallPrompt = true;
      if (state.tokens) render();
    });
    window.addEventListener("appinstalled", function () {
      deferredInstallPrompt = null;
      state.pwaInstallPrompt = null;
    });
  }

  function apiRoot() {
    return uiTransportUtils.apiRoot();
  }

  function wsBaseUrl() {
    return uiTransportUtils.wsBaseUrl(window, location);
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

  var refreshTokensPromise = null;
  var apiClient = null;

  function ensureApiClient() {
    if (!apiClient) {
      apiClient = uiTransportUtils.createApiClient({
        fetchImpl: fetch.bind(window),
        apiRoot: apiRoot(),
        getAccessToken: function () {
          return state.tokens && state.tokens.access_token
            ? state.tokens.access_token
            : null;
        },
        getRefreshToken: function () {
          return state.tokens && state.tokens.refresh_token
            ? state.tokens.refresh_token
            : null;
        },
        isPublicAuthPath: isPublicAuthPath,
        tryRefreshTokens: tryRefreshTokens,
        onSessionExpired: sessionExpired,
      });
    }
    return apiClient;
  }

  async function tryRefreshTokens() {
    if (!state.tokens || !state.tokens.refresh_token) return false;
    if (refreshTokensPromise) return refreshTokensPromise;
    refreshTokensPromise = (async function () {
      try {
        var t = await apiJson("/auth/refresh", {
          method: "POST",
          jsonBody: { refresh_token: state.tokens.refresh_token },
          noRefresh: true,
          noAuth: true,
        });
        saveTokens({
          access_token: t.access_token,
          refresh_token: t.refresh_token || state.tokens.refresh_token,
          expires_in: t.expires_in,
        });
        if (state.ws && state.ws.readyState === WebSocket.OPEN) {
          openWs();
        }
        return true;
      } catch (e) {
        return false;
      } finally {
        refreshTokensPromise = null;
      }
    })();
    return refreshTokensPromise;
  }

  function sessionExpired() {
    clearTokens();
    closeWs();
    state.selectedId = null;
    state.chats = [];
    state.messages = [];
    state.error = L("errors.sessionExpired");
    render();
  }

  function isUuidString(s) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      String(s).trim()
    );
  }

  function revokeBlobUrls() {
    state.blobUrls.forEach(function (u) {
      try {
        URL.revokeObjectURL(u);
      } catch (e) {}
    });
    state.blobUrls = [];
  }

  function messageTypeForMime(mime) {
    if (mime && mime.indexOf("image/") === 0) return "image";
    if (mime && mime.indexOf("video/") === 0) return "video";
    return "file";
  }

  async function apiFetch(path, opts) {
    return ensureApiClient().apiFetch(path, opts || {});
  }

  async function apiJson(path, opts) {
    return ensureApiClient().apiJson(path, opts || {});
  }

  var exportPollGenerationRef = { value: exportPollGeneration };
  var korusMlsWasmInstance = null;
  function getKorusMlsWasm() {
    if (!korusMlsWasmInstance && window.KorusMlsWasmFactory) {
      korusMlsWasmInstance = window.KorusMlsWasmFactory(apiJson);
      window.KorusMlsWasm = korusMlsWasmInstance;
    }
    return korusMlsWasmInstance;
  }
  var uiExportUtils = window.KorusUiExportUtils({
    getState: function () {
      return state;
    },
    setState: function (patch) {
      Object.assign(state, patch);
    },
    apiJson: apiJson,
    apiFetch: apiFetch,
    scheduleRender: scheduleRender,
    exportPollGenerationRef: exportPollGenerationRef,
  });
  var uiE2eeMls = window.KorusUiE2eeMls({
    apiJson: apiJson,
    getState: function () {
      return state;
    },
    getMlsWasm: getKorusMlsWasm,
  });
  var uiE2eeUtils = window.KorusUiE2eeUtils({
    getState: function () {
      return state;
    },
    setStateField: function (cacheKey, msgId, text) {
      if (cacheKey === "e2eePlaintextCache") state.e2eePlaintextCache[msgId] = text;
    },
    apiJson: apiJson,
    isMlsActive: isMlsCapabilitiesActive,
    mlsClientDecrypt: function (content, chatId) {
      return uiE2eeMls.mlsClientDecrypt(content, chatId);
    },
    findCachedMessage: findCachedMessage,
  });

  async function loadE2eePlaintext(chatId, msgId) {
    return uiE2eeUtils.loadE2eePlaintext(chatId, msgId);
  }

  function startChatExport() {
    return uiExportUtils.startChatExport();
  }
  function cancelChatExport() {
    return uiExportUtils.cancelChatExport();
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

  function pushPendingIceCandidate(peerId, candidateInit) {
    if (!state.rtcPendingCandidates[peerId]) {
      state.rtcPendingCandidates[peerId] = [];
    }
    state.rtcPendingCandidates[peerId].push(candidateInit);
  }

  async function flushPendingIceCandidates(peerId) {
    var pc = state.rtcPeers[peerId];
    var q = state.rtcPendingCandidates[peerId];
    if (!pc || !q || !q.length) return;
    delete state.rtcPendingCandidates[peerId];
    for (var i = 0; i < q.length; i++) {
      try {
        await pc.addIceCandidate(q[i]);
      } catch (e) {
        /* устаревшие или пришедшие до готовности SDP */
      }
    }
    var more = state.rtcPendingCandidates[peerId];
    if (more && more.length) {
      await flushPendingIceCandidates(peerId);
    }
  }

  async function addRemoteIceCandidate(peerId, candidateInit) {
    var pc = state.rtcPeers[peerId];
    if (!pc || !pc.remoteDescription) {
      pushPendingIceCandidate(peerId, candidateInit);
      return;
    }
    await pc.addIceCandidate(candidateInit);
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
      uiRtcUtils.sendRtcHangups(state.ws, state.selectedId, Object.keys(state.rtcPeers));
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
    delete state.rtcPendingCandidates[peerId];
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
    state.rtcPendingCandidates = {};
  }

  function sendRtcSignal(payload, chatIdOpt) {
    var chatId = chatIdOpt || state.selectedId;
    uiRtcUtils.sendRtcSignal(state.ws, chatId, payload);
  }

  async function acceptIncomingRtcCall() {
    var inc = state.incomingRtcCall;
    if (!inc || !state.tokens) return;
    state.incomingRtcCall = null;
    stopIncomingCallRing();
    state.busy = true;
    state.error = null;
    render();
    try {
      if (state.selectedId !== inc.chatId) {
        await openChatById(inc.chatId);
      }
      if (state.activeConference) {
        await leaveActiveConference();
      }
      state.callPanelOpen = true;
      state.callMode = "mesh";
      await loadChatConferences();
      await ensureCallStream();
      await loadRtcPeerIds();
      startThumbCapture();
      var from = inc.fromUserId;
      var pc = getOrCreatePeerConnection(from);
      await pc.setRemoteDescription({ type: "offer", sdp: inc.sdp });
      await flushPendingIceCandidates(from);
      var ans = await pc.createAnswer();
      await pc.setLocalDescription(ans);
      await flushPendingIceCandidates(from);
      sendRtcSignal({ kind: "answer", targetUserId: from, sdp: ans.sdp }, inc.chatId);
      attachLocalVideo();
      state.statusMessage = L("rtc.callAccepted");
    } catch (e) {
      state.error = e.message || L("rtc.acceptFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function declineIncomingRtcCall() {
    var inc = state.incomingRtcCall;
    if (!inc) return;
    sendRtcSignal({ kind: "hangup", targetUserId: inc.fromUserId }, inc.chatId);
    state.incomingRtcCall = null;
    stopIncomingCallRing();
    render();
  }

  function formatInstantLabel(iso) {
    return uiFormatUtils.formatInstantLabel(iso);
  }

  function formatChatListTime(ms) {
    return uiFormatUtils.formatChatListTime(ms);
  }

  function chatListTimeMs(c) {
    var prev = state.chatPreview[c.id];
    if (prev && prev.at) return prev.at;
    if (c.created_at) return new Date(c.created_at).getTime();
    return 0;
  }

  function closeFilePublicLinksModal() {
    state.fileLinksOpen = false;
    state.fileLinksFileId = null;
    state.fileLinksRows = null;
    state.fileLinksBusy = false;
    render();
  }

  async function openFilePublicLinksModal(fileId) {
    if (!fileId || !state.tokens) return;
    state.fileLinksOpen = true;
    state.fileLinksFileId = fileId;
    state.fileLinksRows = null;
    state.fileLinksBusy = true;
    state.error = null;
    render();
    try {
      var rows = await apiJson("/files/" + fileId + "/public-links", { method: "GET" });
      state.fileLinksRows = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.error = e.message || L("files.loadLinksFailed");
      state.fileLinksOpen = false;
    } finally {
      state.fileLinksBusy = false;
      render();
    }
  }

  async function revokeFilePublicLink(fileId, linkId) {
    if (!fileId || !linkId || !state.tokens) return;
    if (!window.confirm(L("common.revokePublicLink"))) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiFetch("/files/" + fileId + "/public-links/" + linkId, { method: "DELETE" });
      if (
        state.lastPublicLink &&
        state.lastPublicLink.file_id === fileId &&
        state.lastPublicLink.link_id === linkId
      ) {
        state.lastPublicLink = null;
        saveLastPublicLink(null);
      }
      state.statusMessage = L("files.linkRevoked");
      if (state.fileLinksOpen && state.fileLinksFileId === fileId) {
        var rows = await apiJson("/files/" + fileId + "/public-links", { method: "GET" });
        state.fileLinksRows = Array.isArray(rows) ? rows : [];
      }
      if (state.myPublicLinks !== null) {
        await loadMyPublicLinks();
      }
    } catch (e) {
      state.error = e.message || L("files.revokeFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function copyTextToClipboardOrShow(text, okMessage) {
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard
        .writeText(text)
        .then(function () {
          state.statusMessage = okMessage;
          render();
        })
        .catch(function () {
          state.statusMessage = okMessage + ": " + text;
          render();
        });
    } else {
      state.statusMessage = okMessage + ": " + text;
      render();
    }
  }

  function copyChatDeepLink() {
    if (!state.selectedId) return;
    var url =
      window.location.origin +
      window.location.pathname +
      "?chat=" +
      encodeURIComponent(state.selectedId);
    copyTextToClipboardOrShow(url, L("chat.chatLinkCopied"));
  }

  function copyMessageDeepLink(m) {
    if (!m || !m.id || !state.selectedId) return;
    var url =
      window.location.origin +
      window.location.pathname +
      "?chat=" +
      encodeURIComponent(state.selectedId) +
      "&msg=" +
      encodeURIComponent(m.id);
    copyTextToClipboardOrShow(url, L("chat.messageLinkCopied"));
  }

  async function createPublicLinkForFile(fileId) {
    if (!fileId || !state.tokens) return;
    var kindRaw = window.prompt(L("files.linkTypePrompt"), "A");
    if (!kindRaw) return;
    var kind = kindRaw.trim().charAt(0).toUpperCase();
    if (kind !== "A" && kind !== "B" && kind !== "C") {
      state.error = L("files.linkTypeInvalid");
      render();
      return;
    }
    var password = null;
    if (kind === "C") {
      password = window.prompt(L("files.linkPasswordPrompt"));
      if (!password) return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      var r = await apiJson("/files/" + fileId + "/public-links", {
        method: "POST",
        jsonBody: {
          link_kind: kind,
          password: password,
          ttl_seconds: null,
        },
      });
      var token = r && r.access_token;
      if (!token) throw new Error(L("chat.noAccessToken"));
      var pubUrl =
        kind === "B"
          ? window.location.origin +
            "/api/v1/files/auth-link/" +
            encodeURIComponent(token)
          : window.location.origin + "/api/v1/files/pub/" + encodeURIComponent(token);
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(pubUrl);
        state.statusMessage = L("files.linkCopied", { kind: kind });
      } else {
        state.statusMessage = L("files.linkUrl", { kind: kind, url: pubUrl });
      }
      if (r.public_url_hint) {
        state.statusMessage += " · " + r.public_url_hint;
      }
      if (r.link_id) {
        state.lastPublicLink = { file_id: fileId, link_id: r.link_id };
        saveLastPublicLink(state.lastPublicLink);
        state.statusMessage += L("files.linkRevokeHint");
      }
      if (state.fileLinksOpen && state.fileLinksFileId === fileId) {
        var links = await apiJson("/files/" + fileId + "/public-links", { method: "GET" });
        state.fileLinksRows = Array.isArray(links) ? links : [];
      }
      if (state.myPublicLinks !== null) {
        await loadMyPublicLinks();
      }
    } catch (e) {
      state.error = e.message || L("files.createLinkFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function addMemberToChatById(userId) {
    if (!userId) return;
    if (state.membersModalOpen && currentChat() && currentChat().type === "group") {
      state.busy = true;
      state.error = null;
      render();
      try {
        await apiJson("/chats/" + state.selectedId + "/members", {
          method: "POST",
          jsonBody: { user_id: userId },
        });
        await loadChatMembersModal();
        await refreshChats();
        state.statusMessage = L("chat.memberAdded");
      } catch (e) {
        state.error = e.message || L("chat.addMemberFailed");
      } finally {
        state.busy = false;
        render();
      }
      return;
    }
    await addMemberToChat();
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
      state.error = localMediaErr(e.message);
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
      state.error = (e && e.message) || L("chat.loadCallMembersFailed");
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
    if (state.callScreenStream) {
      var sv = state.callScreenStream.getVideoTracks()[0];
      if (sv) {
        var tSend = typeof sv.clone === "function" ? sv.clone() : sv;
        try {
          pc.addTrack(tSend, new MediaStream([tSend]));
        } catch (e) {}
      }
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
      if (!wrap || !ev.track) return;
      var settings = ev.track.getSettings ? ev.track.getSettings() : {};
      var isDisplay =
        ev.track.kind === "video" &&
        (!!settings.displaySurface ||
          (ev.track.label && /screen|window|tab|display/i.test(ev.track.label)));
      if (isDisplay) {
        var vs = wrap.querySelector("video.rtc-remote-screen");
        if (vs) {
          vs.srcObject = new MediaStream([ev.track]);
          vs.style.display = "block";
        }
        return;
      }
      var v = wrap.querySelector("video.rtc-remote-cam");
      if (v && ev.streams[0]) {
        v.srcObject = ev.streams[0];
      }
    };
    pc.onconnectionstatechange = function () {
      if (pc.connectionState !== "failed") return;
      try {
        sendRtcSignal({ kind: "hangup", targetUserId: peerId });
      } catch (e1) {}
      teardownPeer(peerId);
      if (state.callPanelOpen) {
        state.error = L("rtc.iceFailed");
        render();
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

  async function rtcRenegotiateMesh() {
    var ids = Object.keys(state.rtcPeers);
    for (var i = 0; i < ids.length; i++) {
      var pid = ids[i];
      var pc = state.rtcPeers[pid];
      if (!pc) continue;
      try {
        var offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        sendRtcSignal({ kind: "offer", targetUserId: pid, sdp: offer.sdp });
      } catch (e) {}
    }
  }

  function addScreenTracksToMesh() {
    if (!state.callScreenStream) return;
    var vt = state.callScreenStream.getVideoTracks()[0];
    if (!vt) return;
    Object.keys(state.rtcPeers).forEach(function (pid) {
      var pc = state.rtcPeers[pid];
      var tSend = typeof vt.clone === "function" ? vt.clone() : vt;
      try {
        pc.addTrack(tSend, new MediaStream([tSend]));
      } catch (e) {}
    });
  }

  async function removeScreenTracksFromMesh() {
    Object.keys(state.rtcPeers).forEach(function (pid) {
      var pc = state.rtcPeers[pid];
      pc.getSenders().forEach(function (sender) {
        var tr = sender.track;
        if (!tr || tr.kind !== "video") return;
        var st = tr.getSettings ? tr.getSettings() : {};
        if (st.displaySurface || (tr.label && /screen|window|tab|display/i.test(tr.label))) {
          try {
            pc.removeTrack(sender);
          } catch (e) {}
        }
      });
    });
  }

  async function stopScreenShareInternal() {
    if (!state.callScreenStream) return;
    try {
      await removeScreenTracksFromMesh();
    } catch (e) {}
    state.callScreenStream.getTracks().forEach(function (t) {
      t.stop();
    });
    state.callScreenStream = null;
    await rtcRenegotiateMesh();
    render();
    attachLocalVideo();
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
    var from = env.fromUserId;
    var me = jwtSub(state.tokens && state.tokens.access_token);
    if (!from || !me || from === me) return;
    var p = env.payload;
    if (p.targetUserId && p.targetUserId !== me) return;
    try {
      if (p.kind === "hangup") {
        if (
          state.incomingRtcCall &&
          state.incomingRtcCall.fromUserId === from &&
          state.incomingRtcCall.chatId === env.chatId
        ) {
          state.incomingRtcCall = null;
          stopIncomingCallRing();
          render();
          return;
        }
        if (env.chatId !== state.selectedId) return;
        teardownPeer(from);
        render();
        return;
      }
      if (p.kind === "offer" && p.sdp) {
        if (!state.callPanelOpen || env.chatId !== state.selectedId) {
          state.incomingRtcCall = {
            chatId: env.chatId,
            fromUserId: from,
            sdp: p.sdp,
          };
          syncIncomingCallRing();
          render();
          return;
        }
        var pcO = getOrCreatePeerConnection(from);
        await pcO.setRemoteDescription({ type: "offer", sdp: p.sdp });
        await flushPendingIceCandidates(from);
        var ans = await pcO.createAnswer();
        await pcO.setLocalDescription(ans);
        await flushPendingIceCandidates(from);
        sendRtcSignal({ kind: "answer", targetUserId: from, sdp: ans.sdp }, env.chatId);
        return;
      }
      if (env.chatId !== state.selectedId) return;
      if (p.kind === "answer" && p.sdp) {
        var pcA = state.rtcPeers[from];
        if (pcA) {
          await pcA.setRemoteDescription({ type: "answer", sdp: p.sdp });
          await flushPendingIceCandidates(from);
        }
        return;
      }
      if (p.kind === "candidate") {
        if (p.candidate == null) {
          var pcNull = state.rtcPeers[from];
          if (pcNull && pcNull.remoteDescription) {
            try {
              await pcNull.addIceCandidate(null);
            } catch (e) {}
          }
          return;
        }
        await addRemoteIceCandidate(from, p.candidate);
        return;
      }
    } catch (e) {
      state.error = (e && e.message) || "WebRTC";
      render();
    }
  }

  async function toggleCallPanel() {
    state.callPanelOpen = !state.callPanelOpen;
    if (!state.callPanelOpen) {
      if (state.activeConference && conferenceIsTracked(state.activeConference)) {
        await leaveActiveConference();
      } else if (state.activeConference) {
        state.activeConference = null;
        clearJitsiIframe();
      }
      stopMeshCallMedia();
      render();
      return;
    }
    try {
      await loadActiveConferences();
      await loadChatConferences();
      if (!meshCallChatReady() && state.callMode === "mesh") {
        state.callMode = "jitsi";
      }
      if (state.callMode === "jitsi") {
        render();
        return;
      }
      if (!meshCallChatReady()) {
        state.callMode = "jitsi";
        render();
        return;
      }
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
      await stopScreenShareInternal();
      return;
    }
    try {
      state.callScreenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      render();
      var sv = document.getElementById("callScreenVideo");
      if (sv) sv.srcObject = state.callScreenStream;
      var v0 = state.callScreenStream.getVideoTracks()[0];
      if (v0) {
        v0.onended = function () {
          stopScreenShareInternal().catch(function () {});
        };
      }
      addScreenTracksToMesh();
      await rtcRenegotiateMesh();
    } catch (e) {
      state.callScreenStream = null;
      state.error = L("rtc.screenShareFailed", {
        detail: e.message || L("rtc.screenShareCancelled"),
      });
    }
    render();
    attachLocalVideo();
  }

  function isMessageSendEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      typeof o.messageId === "string" &&
      typeof o.chatId === "string" &&
      !o.change
    );
  }

  function isMessageChangeEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      (o.change === "update" || o.change === "delete") &&
      typeof o.messageId === "string" &&
      typeof o.chatId === "string" &&
      !o.reaction
    );
  }

  function isReactionChangeEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      (o.change === "add" || o.change === "remove") &&
      typeof o.messageId === "string" &&
      typeof o.chatId === "string" &&
      typeof o.reaction === "string"
    );
  }

  function isPinChangeEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      (o.change === "pin" || o.change === "unpin") &&
      typeof o.chat_id === "string" &&
      typeof o.message_id === "string"
    );
  }

  function isConferenceChangeEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      (o.change === "created" || o.change === "ended" || o.change === "updated") &&
      typeof o.chat_id === "string" &&
      typeof o.conference_id === "string"
    );
  }

  function applyPinChangeEvent(data) {
    if (data.chat_id !== state.selectedId) return;
    loadPinnedMessages(data.chat_id)
      .then(scheduleRender)
      .catch(function () {
        scheduleRender();
      });
  }

  function maybeNotifyConference(data) {
    if (!notificationsAllowed() || !isConferenceChangeEvent(data)) return;
    var myId = jwtSub(state.tokens.access_token);
    if (myId && data.actor_id === myId) return;
    if (data.chat_id === state.selectedId && !document.hidden) return;
    if (data.change !== "created") return;
    playNotifySound();
    var title = chatTitleById(data.chat_id);
    var body =
      L("chat.confStarted") + (data.title ? ": " + data.title : "");
    try {
      var note = new Notification(title, {
        body: body,
        tag: "korus-conf-" + data.conference_id,
        icon: "/webui/favicon.ico",
      });
      note.onclick = function () {
        window.focus();
        note.close();
        if (data.chat_id !== state.selectedId) {
          openChatById(data.chat_id).then(function () {
            state.callPanelOpen = true;
            return loadChatConferences();
          }).then(render);
        } else {
          state.callPanelOpen = true;
          loadChatConferences().then(render);
        }
      };
    } catch (e) {}
  }

  function patchConferenceParticipantCount(data) {
    if (typeof data.participant_count !== "number") return;
    var conf = activeConferenceInChat(data.chat_id);
    if (conf && conf.conference_id === data.conference_id) {
      conf.participant_count = data.participant_count;
      setActiveConferenceForChat(data.chat_id, conf);
    }
    if (
      state.activeConference &&
      state.activeConference.conference_id === data.conference_id
    ) {
      state.activeConference.participant_count = data.participant_count;
    }
    if (state.chatConferences) {
      state.chatConferences.forEach(function (c) {
        if (c.conference_id === data.conference_id) {
          c.participant_count = data.participant_count;
        }
      });
    }
  }

  function applyConferenceChangeEvent(data) {
    if (data.change === "created" || data.change === "updated") {
      setActiveConferenceForChat(data.chat_id, conferenceRowFromEvent(data));
      patchConferenceParticipantCount(data);
      if (data.change === "created") {
        state.statusMessage = data.title
          ? L("chat.confCreatedNamed", { title: data.title })
          : L("chat.confCreatedDefault");
        maybeNotifyConference(data);
      }
    } else if (data.change === "ended") {
      setActiveConferenceForChat(data.chat_id, null);
      if (
        state.activeConference &&
        state.activeConference.conference_id === data.conference_id
      ) {
        state.activeConference = null;
        clearJitsiIframe();
      }
      state.statusMessage = L("conference.ended");
    }
    if (data.chat_id === state.selectedId) {
      loadChatConferences().catch(function () {});
      if (data.change === "updated" || data.change === "created") {
        loadConferenceParticipants(data.conference_id).then(render).catch(function () {});
      }
    }
    if (
      data.change === "updated" &&
      state.activeConference &&
      state.activeConference.conference_id === data.conference_id
    ) {
      loadConferenceParticipants(data.conference_id).then(render).catch(function () {});
    }
  }

  function applyReactionChangeEvent(data) {
    if (data.chatId !== state.selectedId) return;
    var rows = state.reactionsByMsg[data.messageId] || [];
    state.reactionsByMsg[data.messageId] = uiMessagesUtils.applyReactionChangeEventRows(
      rows,
      data.change,
      data.userId,
      data.reaction
    );
  }

  function applyMessageChangeEvent(data) {
    var chatId = data.chatId;
    var atMs = data.createdAt || Date.now();
    if (chatId === state.selectedId && state.messages.length) {
      var touched = false;
      state.messages = state.messages.map(function (m) {
        if (m.id !== data.messageId) return m;
        touched = true;
        if (data.change === "delete") {
          return Object.assign({}, m, { deleted: true, content: "" });
        }
        var patch = { content: data.content != null ? data.content : m.content };
        if (data.editedAt) patch.edited_at = new Date(data.editedAt).toISOString();
        var next = Object.assign({}, m, patch);
        if (isE2eeType(next.type) && state.e2eePlaintextCache) {
          delete state.e2eePlaintextCache[data.messageId];
        }
        return next;
      });
      if (touched) {
        syncPreviewIfLastMessage(chatId, data.messageId);
      } else if (data.change === "update") {
        ingestIncomingMessage(chatId, data.messageId, null)
          .then(function () {
            syncPreviewIfLastMessage(chatId, data.messageId);
            render();
          })
          .catch(function () {});
      }
    } else if (data.change === "update") {
      var previewContent = data.content;
      if (isE2eeType(data.type)) previewContent = "";
      setChatPreview(
        chatId,
        data.type,
        previewContent,
        data.senderId,
        atMs,
        data.messageId
      );
      var myId = jwtSub(state.tokens.access_token);
      if (chatId !== state.selectedId && data.senderId && data.senderId !== myId) {
        bumpUnread(chatId);
      }
    } else if (data.change === "delete") {
      var prevDel = state.chatPreview[chatId];
      if (!prevDel || !prevDel.messageId || prevDel.messageId === data.messageId) {
        refreshChatPreviewFromServer(chatId).then(scheduleRender).catch(function () {
          scheduleRender();
        });
      }
    }
  }

  function isTypingEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      typeof o.chat_id === "string" &&
      typeof o.user_id === "string" &&
      typeof o.ts === "number" &&
      !o.messageId &&
      o.type !== "read_receipt"
    );
  }

  function isReadReceiptEvent(o) {
    return o && o.type === "read_receipt" && o.chat_id && o.user_id;
  }

  function applyReadReceiptEvent(ev) {
    function bump(messageId, userId) {
      if (!messageId || !userId) return;
      if (!state.readReceiptsByMessage[messageId]) {
        state.readReceiptsByMessage[messageId] = {};
      }
      state.readReceiptsByMessage[messageId][userId] = ev.read_at || Date.now();
    }
    if (ev.batch_message_ids && ev.batch_message_ids.length) {
      ev.batch_message_ids.forEach(function (mid) {
        bump(mid, ev.user_id);
      });
    } else if (ev.message_id) {
      bump(ev.message_id, ev.user_id);
    }
  }

  function isE2eeType(type) {
    return !!(type && String(type).indexOf("e2ee-") === 0);
  }

  function isMlsCapabilitiesActive() {
    return uiE2eeMls.isMlsCapabilitiesActive();
  }

  function preferredE2eeScheme() {
    if (isMlsCapabilitiesActive()) return "mls";
    return null;
  }

  async function mlsEnsureKeyPackage() {
    return uiE2eeMls.mlsEnsureKeyPackage();
  }

  async function mlsClientEncrypt(plaintext, chatId) {
    return uiE2eeMls.mlsClientEncrypt(plaintext, chatId);
  }

  async function mlsClientDecrypt(contentBase64, chatId) {
    return uiE2eeMls.mlsClientDecrypt(contentBase64, chatId);
  }

  function findCachedMessage(chatId, msgId) {
    var rows = state.messagesByChat[chatId] || state.messages || [];
    for (var i = 0; i < rows.length; i++) {
      if (rows[i] && rows[i].id === msgId) return rows[i];
    }
    return null;
  }

  function e2eePlainType(type) {
    if (!isE2eeType(type)) return type || "text";
    return String(type).slice(5) || "text";
  }

  function messageAttachmentKind(m) {
    if (!m || !m.type) return null;
    var base = e2eePlainType(m.type);
    if (base === "image" || base === "video" || base === "file") return base;
    return null;
  }

  function messageAttachmentFileId(m) {
    if (!m) return null;
    var aid = (m.attachment_file_id || "").trim();
    if (isUuidString(aid)) return aid;
    if (isE2eeType(m.type)) return null;
    var c = (m.content || "").trim();
    if (messageAttachmentKind(m) && isUuidString(c)) return c;
    return null;
  }

  function appendMessageAttachment(bodyEl, kind, fileId) {
    if (kind === "image") {
      var img = document.createElement("img");
      img.className = "msg-attachment-image";
      img.alt = L("ui.message.image");
      bodyEl.appendChild(img);
      attachAuthenticatedImage(fileId, img);
      return;
    }
    var label = kind === "video" ? L("ui.message.video") : L("ui.message.file");
    var btn = iconBtn("⬇", label, {
      cls: "msg-attachment-dl",
      testId: "message-file-download",
      onClick: function () {
        downloadChatFile(fileId).catch(function (err) {
          state.error = err.message || L("files.downloadFailedShort");
          render();
        });
      },
    });
    bodyEl.appendChild(btn);
  }

  function chatTitleById(chatId) {
    var c = state.chats.find(function (x) {
      return x.id === chatId;
    });
    return (c && c.title) || (chatId ? chatId.slice(0, 8) + "…" : L("ui.message.chatFallback"));
  }

  function formatPreviewText(type, content) {
    return uiMessagesUtils.formatPreviewText(
      type,
      content,
      isE2eeType,
      e2eePlainType
    );
  }

  function setChatPreview(chatId, type, content, senderId, atMs, messageId) {
    if (!chatId) return;
    state.chatPreview[chatId] = {
      text: formatPreviewText(type, content),
      senderId: senderId || null,
      at: atMs || Date.now(),
      messageId: messageId || null,
    };
  }

  function setChatPreviewFromMessage(chatId, m) {
    if (!m) return;
    var previewContent = m.content;
    if (messageAttachmentKind(m) && messageAttachmentFileId(m)) previewContent = "";
    setChatPreview(
      chatId,
      m.type,
      previewContent,
      m.sender_id,
      new Date(m.created_at).getTime(),
      m.id
    );
  }

  function setChatPreviewFromSendEvent(data) {
    if (!data || !data.chatId) return;
    var stub = messageFromSendEvent(data);
    var previewContent =
      messageAttachmentKind(stub) && messageAttachmentFileId(stub) ? "" : stub.content;
    if (isE2eeType(stub.type) && !messageAttachmentFileId(stub)) previewContent = "";
    setChatPreview(
      data.chatId,
      stub.type,
      previewContent,
      data.senderId,
      data.createdAt || Date.now(),
      data.messageId
    );
  }

  async function refreshChatPreviewFromServer(chatId) {
    if (!chatId || !state.tokens) return;
    try {
      var rows = await apiJson("/chats/" + chatId + "/messages?limit=1", { method: "GET" });
      if (rows && rows.length) {
        setChatPreviewFromMessage(chatId, rows[0]);
      } else {
        delete state.chatPreview[chatId];
      }
    } catch (e) {}
  }

  function syncPreviewFromThread(chatId) {
    if (!state.messages.length) return;
    setChatPreviewFromMessage(chatId, state.messages[state.messages.length - 1]);
  }

  function noteTyping(chatId, userId) {
    if (!chatId || !userId) return;
    if (!state.typingExpireByChat[chatId]) {
      state.typingExpireByChat[chatId] = {};
    }
    state.typingExpireByChat[chatId][userId] = Date.now() + 4500;
  }

  function scheduleTypingSidebarRefresh() {
    scheduleRender();
    if (typingSidebarTimer) clearTimeout(typingSidebarTimer);
    typingSidebarTimer = setTimeout(function () {
      typingSidebarTimer = null;
      scheduleRender();
    }, 4600);
  }

  function getTypingLabel(chatId) {
    if (!chatId || !state.tokens) return "";
    var myId = jwtSub(state.tokens.access_token);
    var map = state.typingExpireByChat[chatId];
    if (!map) return "";
    var now = Date.now();
    var others = 0;
    Object.keys(map).forEach(function (uid) {
      if (map[uid] < now) {
        delete map[uid];
        return;
      }
      if (uid !== myId) others += 1;
    });
    if (others === 1) return L("ui.message.typingOne");
    if (others > 1) return L("ui.message.typingMany");
    return "";
  }

  function scheduleTypingNotify() {
    if (!state.selectedId || !state.tokens) return;
    if (typingNotifyTimer) return;
    typingNotifyTimer = setTimeout(function () {
      typingNotifyTimer = null;
      var chatId = state.selectedId;
      apiFetch("/chats/" + chatId + "/typing", { method: "POST" }).catch(function () {});
    }, 450);
  }

  function bindComposerDrop(comp) {
    comp.addEventListener("dragover", function (e) {
      e.preventDefault();
      comp.classList.add("composer-dragover");
    });
    comp.addEventListener("dragleave", function (e) {
      if (!comp.contains(e.relatedTarget)) {
        comp.classList.remove("composer-dragover");
      }
    });
    comp.addEventListener("drop", function (e) {
      e.preventDefault();
      comp.classList.remove("composer-dragover");
      var f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
      if (f && state.selectedId && !state.busy) {
        sendFileMessage(f);
      }
    });
  }

  function sortMessagesAsc(rows) {
    return uiMessagesUtils.sortMessagesAsc(rows);
  }

  async function loadMediaCaps() {
    try {
      state.mediaCaps = await apiJson("/media/capabilities", { method: "GET", noAuth: true });
    } catch (e) {
      state.mediaCaps = null;
    }
  }

  async function loadChats() {
    if (!state.tokens) return;
    var list = await apiJson("/chats", { method: "GET" });
    state.chats = list;
  }

  async function loadUnreadCounts() {
    if (!state.tokens || !state.chats.length) {
      state.unreadByChat = {};
      return;
    }
    var map = {};
    await Promise.all(
      state.chats.map(function (c) {
        return apiJson("/chats/" + c.id + "/unread-count", { method: "GET" })
          .then(function (r) {
            map[c.id] = r && typeof r.unread_count === "number" ? r.unread_count : 0;
          })
          .catch(function () {
            map[c.id] = 0;
          });
      })
    );
    state.unreadByChat = map;
    updateDocumentTitle();
  }

  function chatsWithoutPreview() {
    return state.chats
      .filter(function (c) {
        return c.type !== "saved" && !state.chatPreview[c.id];
      })
      .sort(compareChatsForSidebar);
  }

  async function hydrateChatPreviewsBatch(gen, limit) {
    var need = chatsWithoutPreview().slice(0, limit);
    if (!need.length) return false;
    await Promise.all(
      need.map(function (c) {
        return apiJson("/chats/" + c.id + "/messages?limit=1", { method: "GET" })
          .then(function (rows) {
            if (gen !== chatPreviewHydrateGen) return;
            if (!rows || !rows.length || state.chatPreview[c.id]) return;
            setChatPreviewFromMessage(c.id, rows[0]);
          })
          .catch(function () {});
      })
    );
    return gen === chatPreviewHydrateGen;
  }

  function scheduleChatPreviewHydrateMore() {
    if (chatPreviewMoreTimer || !state.tokens) return;
    chatPreviewMoreTimer = setTimeout(function () {
      chatPreviewMoreTimer = null;
      if (!chatsWithoutPreview().length) return;
      var gen = chatPreviewHydrateGen;
      hydrateChatPreviewsBatch(gen, CHAT_PREVIEW_HYDRATE_MORE).then(function (changed) {
        if (changed) render();
      });
    }, 180);
  }

  async function hydrateChatPreviews() {
    if (!state.tokens || !state.chats.length) return;
    var gen = ++chatPreviewHydrateGen;
    var changed = await hydrateChatPreviewsBatch(gen, CHAT_PREVIEW_HYDRATE_MAX);
    if (changed) render();
  }

  async function loadMyPublicLinks() {
    if (!state.tokens) {
      state.myPublicLinks = null;
      return;
    }
    state.myPublicLinksBusy = true;
    try {
      var rows = await apiJson("/files/public-links?limit=100", { method: "GET" });
      state.myPublicLinks = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.myPublicLinks = [];
    } finally {
      state.myPublicLinksBusy = false;
    }
  }

  async function refreshChats() {
    await loadChats();
    await loadUnreadCounts();
    await hydrateChatPreviews();
    await loadActiveConferences();
  }

  /** Лёгкое обновление списка чатов без GET /chats (вкладка снова в фокусе). */
  async function softRefreshChats() {
    await loadUnreadCounts();
    await loadActiveConferences();
  }

  async function markChatRead(chatId, upToMessageId) {
    if (!state.tokens || !chatId) return;
    try {
      var body = null;
      if (upToMessageId) {
        body = JSON.stringify({ up_to_message_id: upToMessageId });
      } else if (state.messages && state.messages.length) {
        var last = state.messages[state.messages.length - 1];
        if (last && last.id) {
          body = JSON.stringify({ up_to_message_id: last.id });
          upToMessageId = last.id;
        }
      }
      var res = await apiFetch("/chats/" + chatId + "/read", {
        method: "POST",
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body || undefined,
      });
      if (res.ok) {
        state.unreadByChat[chatId] = 0;
        updateDocumentTitle();
        var ids = (state.messages || [])
          .slice(-50)
          .map(function (m) {
            return m.id;
          })
          .filter(Boolean);
        if (ids.length) {
          apiFetch("/chats/" + chatId + "/read-batch", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ message_ids: ids }),
          }).catch(function () {});
        }
        if (upToMessageId) {
          markMessageRead(chatId, upToMessageId).catch(function () {});
        }
      }
    } catch (e) {}
  }

  async function markMessageRead(chatId, messageId) {
    if (!state.tokens || !chatId || !messageId) return;
    try {
      await apiFetch("/chats/" + chatId + "/messages/" + messageId + "/read", {
        method: "POST",
      });
    } catch (e) {}
  }

  async function hydrateReadReceiptsForThread(chatId) {
    if (!state.tokens || !chatId || !state.messages || !state.messages.length) return;
    var myId = jwtSub(state.tokens.access_token);
    var own = state.messages.filter(function (m) {
      return m && m.id && m.sender_id === myId && !m.deleted;
    });
    await Promise.all(
      own.slice(-30).map(function (m) {
        return apiJson(
          "/chats/" + chatId + "/read-receipts?message_id=" + encodeURIComponent(m.id),
          { method: "GET" }
        )
          .then(function (row) {
            if (!row || !row.read_by) return;
            if (!state.readReceiptsByMessage[m.id]) {
              state.readReceiptsByMessage[m.id] = {};
            }
            row.read_by.forEach(function (u) {
              if (u && u.user_id) {
                state.readReceiptsByMessage[m.id][u.user_id] =
                  u.read_at || Date.now();
              }
            });
          })
          .catch(function () {});
      })
    );
  }

  function showReadReceiptPopup(messageId) {
    var rr = state.readReceiptsByMessage[messageId];
    if (!rr) {
      window.alert(L("readReceipts.none"));
      return;
    }
    var ids = Object.keys(rr);
    if (!ids.length) {
      window.alert(L("readReceipts.none"));
      return;
    }
    window.alert(L("readReceipts.title") + ":\n" + ids.join("\n"));
  }

  function scheduleUserSearch() {
    if (userSearchTimer) clearTimeout(userSearchTimer);
    var q = state.sidebarSearch.trim();
    if (q.length < 2) {
      state.userSearchHits = null;
      state.userSearchBusy = false;
      return;
    }
    state.userSearchBusy = true;
    userSearchTimer = setTimeout(function () {
      userSearchTimer = null;
      var query = state.sidebarSearch.trim();
      if (query.length < 2) {
        state.userSearchHits = null;
        state.userSearchBusy = false;
        render();
        return;
      }
      apiJson("/search/users?q=" + encodeURIComponent(query), { method: "GET" })
        .then(function (hits) {
          if (state.sidebarSearch.trim() !== query) return;
          state.userSearchHits = Array.isArray(hits) ? hits : [];
          state.userSearchBusy = false;
          render();
        })
        .catch(function () {
          if (state.sidebarSearch.trim() !== query) return;
          state.userSearchHits = [];
          state.userSearchBusy = false;
          render();
        });
    }, 350);
  }

  async function loadThread(chatId, options) {
    options = options || {};
    if (!state.tokens) return;
    if (!options.preserveBlobs) revokeBlobUrls();
    if (!options.preserveE2eeCache) state.e2eePlaintextCache = {};
    var q = new URLSearchParams({ limit: String(THREAD_PAGE) });
    var rows = await apiJson("/chats/" + chatId + "/messages?" + q, { method: "GET" });
    state.messages = sortMessagesAsc(rows);
    state.threadHasMore = rows.length >= THREAD_PAGE;
    syncPreviewFromThread(chatId);
    await loadReactionsForThread(chatId);
    await loadPinnedMessages(chatId);
    await hydrateReadReceiptsForThread(chatId);
    var liveConf = activeConferenceInChat(chatId);
    if (liveConf && liveConf.conference_id) {
      loadConferenceParticipants(liveConf.conference_id).catch(function () {});
    } else if (state.conferenceParticipantsConfId) {
      clearConferenceParticipants();
    }
    if (!options.keepScroll) {
      state.shouldScrollThread = true;
    }
  }

  async function loadPinnedMessages(chatId) {
    try {
      var rows = await apiJson("/chats/" + chatId + "/messages/pins", { method: "GET" });
      state.pinnedMessages = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.pinnedMessages = [];
    }
  }

  function isMessagePinned(msgId) {
    return (state.pinnedMessages || []).some(function (p) {
      return p.message_id === msgId;
    });
  }

  async function togglePinMessage(m) {
    if (!state.selectedId || !m || !m.id) return;
    var chatId = state.selectedId;
    var path = "/chats/" + chatId + "/messages/" + m.id + "/pin";
    var wasPinned = isMessagePinned(m.id);
    var myId = jwtSub(state.tokens.access_token);
    if (wasPinned) {
      var res = await apiFetch(path, { method: "DELETE" });
      if (!res.ok) throw new Error(L("messages.unpinFailed"));
      applyPinChangeEvent({
        change: "unpin",
        chat_id: chatId,
        message_id: m.id,
      });
    } else {
      var resPost = await apiFetch(path, { method: "POST" });
      if (!resPost.ok) throw new Error(L("messages.pinApiFailed"));
      applyPinChangeEvent({
        change: "pin",
        chat_id: chatId,
        message_id: m.id,
        pinned_by: myId || m.sender_id,
        created_at: Date.now(),
      });
    }
    render();
  }

  async function editMessagePrompt(m) {
    if (!state.selectedId || !m || m.deleted || m.type !== "text") return;
    var text = window.prompt(L("messages.editPrompt"), m.content || "");
    if (text === null) return;
    text = text.trim();
    if (!text || text === (m.content || "").trim()) return;
    var updated = await apiJson("/chats/" + state.selectedId + "/messages/" + m.id, {
      method: "PATCH",
      jsonBody: { content: text },
    });
    if (updated && updated.id) {
      mergeMessageIntoThread(updated);
      if (isE2eeType(updated.type) && state.e2eePlaintextCache) {
        delete state.e2eePlaintextCache[updated.id];
      }
      syncPreviewIfLastMessage(state.selectedId, updated.id);
    } else {
      await loadThread(state.selectedId, THREAD_SOFT_RELOAD);
    }
    render();
  }

  async function deleteMessageConfirm(m) {
    if (!state.selectedId || !m || m.deleted) return;
    if (!window.confirm(L("common.deleteMessage"))) return;
    var res = await apiFetch("/chats/" + state.selectedId + "/messages/" + m.id, {
      method: "DELETE",
    });
    if (!res.ok) throw new Error(L("messages.deleteFailed"));
    if (!patchMessageInThread(m.id, { deleted: true, content: "" })) {
      await loadThread(state.selectedId, THREAD_SOFT_RELOAD);
    } else {
      syncPreviewIfLastMessage(state.selectedId, m.id);
    }
    render();
  }

  async function loadOlderMessages() {
    if (
      !state.selectedId ||
      !state.threadHasMore ||
      state.threadLoadingMore ||
      !state.messages.length
    ) {
      return;
    }
    var oldest = state.messages[0];
    var box = document.querySelector(".messages");
    var prevHeight = box ? box.scrollHeight : 0;
    var prevTop = box ? box.scrollTop : 0;
    state.threadLoadingMore = true;
    render();
    try {
      var q = new URLSearchParams({ limit: String(THREAD_PAGE), before: oldest.id });
      var rows = await apiJson("/chats/" + state.selectedId + "/messages?" + q, {
        method: "GET",
      });
      state.threadHasMore = rows.length >= THREAD_PAGE;
      var older = sortMessagesAsc(rows);
      if (older.length) {
        var seen = {};
        state.messages.forEach(function (m) {
          seen[m.id] = true;
        });
        var added = [];
        older.forEach(function (m) {
          if (!seen[m.id]) {
            seen[m.id] = true;
            added.push(m);
          }
        });
        if (added.length) {
          state.messages = added.concat(state.messages);
          await loadReactionsForMessageIds(state.selectedId, added);
        }
      }
    } catch (err) {
      state.error = err.message || L("chat.historyLoadFailed");
    } finally {
      state.threadLoadingMore = false;
      render();
      if (box) {
        requestAnimationFrame(function () {
          box.scrollTop = box.scrollHeight - prevHeight + prevTop;
        });
      }
    }
  }

  async function loadReactionsForMessageIds(chatId, messages) {
    if (!messages || !messages.length) return;
    await Promise.all(
      messages.map(function (m) {
        return apiJson("/chats/" + chatId + "/messages/" + m.id + "/reactions", {
          method: "GET",
        })
          .then(function (rows) {
            state.reactionsByMsg[m.id] = Array.isArray(rows) ? rows : [];
          })
          .catch(function () {
            state.reactionsByMsg[m.id] = [];
          });
      })
    );
  }

  async function loadReactionsForThread(chatId) {
    state.reactionsByMsg = {};
    if (!state.messages.length) return;
    await loadReactionsForMessageIds(chatId, state.messages);
  }

  async function reloadMessageReactions(chatId, msgId) {
    try {
      var rows = await apiJson("/chats/" + chatId + "/messages/" + msgId + "/reactions", {
        method: "GET",
      });
      state.reactionsByMsg[msgId] = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.reactionsByMsg[msgId] = [];
    }
  }

  function findMessageInThread(msgId) {
    return uiMessagesUtils.findMessageInThread(state.messages, msgId);
  }

  function messageFromSendEvent(data) {
    var aid = data.attachment_file_id || data.attachmentFileId || null;
    var replyTo = data.reply_to_msg_id || data.replyToMsgId || null;
    return {
      id: data.messageId,
      chat_id: data.chatId,
      sender_id: data.senderId,
      type: data.type || "text",
      content: data.content || "",
      reply_to_msg_id: replyTo,
      deleted: false,
      created_at: data.createdAt
        ? new Date(data.createdAt).toISOString()
        : new Date().toISOString(),
      edited_at: null,
      visibility_ttl_seconds:
        data.visibility_ttl_seconds != null
          ? data.visibility_ttl_seconds
          : data.visibilityTtlSeconds != null
            ? data.visibilityTtlSeconds
            : data.ttl_seconds != null
              ? data.ttl_seconds
              : data.ttlSeconds != null
                ? data.ttlSeconds
                : null,
      attachment_file_id: aid,
    };
  }

  function appendMessageFromSendEvent(data) {
    if (!data || !data.messageId || findMessageInThread(data.messageId)) return false;
    state.messages = sortMessagesAsc(state.messages.concat([messageFromSendEvent(data)]));
    return true;
  }

  function mergeMessageIntoThread(full) {
    if (!full || !full.id) return;
    state.messages = uiMessagesUtils.mergeMessageIntoThread(state.messages, full);
  }

  function patchMessageInThread(messageId, patch) {
    var result = uiMessagesUtils.patchMessageInThread(
      state.messages,
      messageId,
      patch
    );
    state.messages = result.messages;
    return result.touched;
  }

  function syncPreviewIfLastMessage(chatId, messageId) {
    if (!chatId || !state.messages.length) return;
    var last = state.messages[state.messages.length - 1];
    if (!last || last.id !== messageId) return;
    if (last.deleted) {
      setChatPreview(
        chatId,
        last.type || "text",
        L("ui.message.deleted"),
        last.sender_id,
        new Date(last.created_at).getTime(),
        last.id
      );
    } else {
      setChatPreviewFromMessage(chatId, last);
    }
  }

  async function ingestIncomingMessage(chatId, messageId, previewData) {
    if (!chatId || !messageId || findMessageInThread(messageId)) return;
    try {
      var full = await apiJson("/chats/" + chatId + "/messages/" + messageId, {
        method: "GET",
      });
      mergeMessageIntoThread(full);
      await reloadMessageReactions(chatId, messageId);
    } catch (e) {
      if (previewData) appendMessageFromSendEvent(previewData);
      if (!findMessageInThread(messageId)) {
        await loadThread(chatId, THREAD_SOFT_RELOAD);
      }
    }
  }

  async function afterLocalSend(chatId, sent) {
    if (sent && sent.id) {
      mergeMessageIntoThread(sent);
      await reloadMessageReactions(chatId, sent.id);
      syncPreviewFromThread(chatId);
      state.shouldScrollThread = true;
      await markChatRead(chatId);
      return;
    }
    await loadThread(chatId, THREAD_SOFT_RELOAD);
    await markChatRead(chatId);
    state.shouldScrollThread = true;
  }

  function bumpUnread(chatId) {
    if (!chatId || chatId === state.selectedId) return;
    state.unreadByChat[chatId] = (state.unreadByChat[chatId] || 0) + 1;
    updateDocumentTitle();
  }

  function formatPreviewForMessage(m) {
    return uiMessagesUtils.formatPreviewForMessage(
      m,
      messageAttachmentKind,
      messageAttachmentFileId,
      formatPreviewText
    );
  }

  function replySnippetForId(msgId) {
    var p = findMessageInThread(msgId);
    if (!p) return L("ui.message.default");
    return formatPreviewForMessage(p);
  }

  function highlightMessageElement(msgId) {
    if (!msgId) return;
    requestAnimationFrame(function () {
      var target = document.getElementById("msg-" + msgId);
      if (!target) return;
      target.scrollIntoView({ behavior: "smooth", block: "center" });
      target.classList.add("msg-highlight");
      setTimeout(function () {
        target.classList.remove("msg-highlight");
      }, 1200);
    });
  }

  async function ensureMessageInThread(msgId) {
    if (!msgId || !state.selectedId) return false;
    if (findMessageInThread(msgId)) return true;
    try {
      var full = await apiJson("/chats/" + state.selectedId + "/messages/" + msgId, {
        method: "GET",
      });
      mergeMessageIntoThread(full);
      await reloadMessageReactions(state.selectedId, msgId);
      return true;
    } catch (e) {
      return false;
    }
  }

  async function scrollToMessageId(msgId) {
    if (!msgId || !state.selectedId) return;
    if (findMessageInThread(msgId)) {
      render();
      highlightMessageElement(msgId);
      return;
    }
    var pages = 0;
    while (state.threadHasMore && pages < 20) {
      pages++;
      await loadOlderMessages();
      if (findMessageInThread(msgId)) {
        render();
        highlightMessageElement(msgId);
        return;
      }
    }
    if (await ensureMessageInThread(msgId)) {
      render();
      highlightMessageElement(msgId);
      return;
    }
    throw new Error(L("messages.notFoundInHistory"));
  }

  function setReplyTo(m) {
    if (!m || !m.id) return;
    state.replyTo = {
      id: m.id,
      snippet: formatPreviewForMessage(m),
    };
    render();
    var ta = document.getElementById("msgdraft");
    if (ta) ta.focus();
  }

  function clearReplyTo() {
    state.replyTo = null;
  }

  function currentReplyToId() {
    return state.replyTo && state.replyTo.id ? state.replyTo.id : null;
  }

  function aggregateReactions(msgId, myId) {
    var rows = state.reactionsByMsg[msgId] || [];
    var map = {};
    rows.forEach(function (r) {
      var em = r.reaction;
      if (!map[em]) map[em] = { count: 0, mine: false };
      map[em].count += 1;
      if (myId && r.user_id === myId) map[em].mine = true;
    });
    return map;
  }

  async function toggleReaction(msgId, emoji) {
    if (!state.selectedId || !state.tokens || !msgId || !emoji) return;
    var myId = jwtSub(state.tokens.access_token);
    var rows = state.reactionsByMsg[msgId] || [];
    var mine = rows.some(function (r) {
      return r.user_id === myId && r.reaction === emoji;
    });
    var path = "/chats/" + state.selectedId + "/messages/" + msgId + "/reactions";
    if (mine) {
      await apiFetch(path, { method: "DELETE", jsonBody: { reaction: emoji } });
      applyReactionChangeEvent({
        change: "remove",
        messageId: msgId,
        chatId: state.selectedId,
        userId: myId,
        reaction: emoji,
      });
    } else {
      await apiFetch(path, { method: "POST", jsonBody: { reaction: emoji } });
      applyReactionChangeEvent({
        change: "add",
        messageId: msgId,
        chatId: state.selectedId,
        userId: myId,
        reaction: emoji,
      });
    }
    render();
  }

  function scrollMessagesToBottom() {
    var box = document.querySelector(".messages");
    if (!box) return;
    box.scrollTop = box.scrollHeight;
  }

  function scheduleScrollMessages() {
    requestAnimationFrame(function () {
      scrollMessagesToBottom();
      requestAnimationFrame(scrollMessagesToBottom);
    });
  }

  function formatTtlLabel(seconds) {
    return uiFormatUtils.formatTtlLabel(seconds);
  }

  function formatTimeLeft(secondsLeft) {
    return uiFormatUtils.formatTimeLeft(secondsLeft);
  }

  function messageVisibilityTtlSeconds(m) {
    if (!m) return null;
    var raw =
      m.visibility_ttl_seconds != null
        ? m.visibility_ttl_seconds
        : m.ttl_seconds != null
          ? m.ttl_seconds
          : m.ttlSeconds != null
            ? m.ttlSeconds
            : null;
    if (raw == null) return null;
    var parsed = parseInt(raw, 10);
    return parsed > 0 ? parsed : null;
  }

  function messageExpiryEpochMs(m) {
    var ttl = messageVisibilityTtlSeconds(m);
    if (!ttl || !m || !m.created_at) return null;
    var created = Date.parse(m.created_at);
    if (!created || isNaN(created)) return null;
    return created + ttl * 1000;
  }

  function getComposerTtlSeconds() {
    var v = state.composerTtl;
    if (!v) return null;
    var n = parseInt(v, 10);
    return n > 0 ? n : null;
  }

  function scheduleThreadSearch() {
    if (threadSearchTimer) clearTimeout(threadSearchTimer);
    var q = (state.threadSearch || "").trim();
    if (q.length < 2) {
      state.threadSearchHits = null;
      state.threadSearchBusy = false;
      return;
    }
    state.threadSearchBusy = true;
    threadSearchTimer = setTimeout(function () {
      threadSearchTimer = null;
      var query = state.threadSearch.trim();
      if (query.length < 2) {
        state.threadSearchHits = null;
        state.threadSearchBusy = false;
        render();
        return;
      }
      apiJson("/search/messages?q=" + encodeURIComponent(query) + "&limit=40", {
        method: "GET",
      })
        .then(function (hits) {
          if (state.threadSearch.trim() !== query) return;
          var chatId = state.selectedId;
          state.threadSearchHits = (Array.isArray(hits) ? hits : []).filter(function (h) {
            return h.chat_id === chatId;
          });
          state.threadSearchBusy = false;
          render();
        })
        .catch(function () {
          if (state.threadSearch.trim() !== query) return;
          state.threadSearchHits = [];
          state.threadSearchBusy = false;
          render();
        });
    }, 350);
  }

  async function openSearchHit(hit) {
    if (!hit || !hit.id || !state.selectedId) return;
    if (hit.chat_id !== state.selectedId) return;
    state.threadSearch = "";
    state.threadSearchHits = null;
    try {
      await scrollToMessageId(hit.id);
    } catch (e) {
      state.error = e.message || L("messages.notFound");
      render();
    }
  }

  function openForwardPicker(m) {
    if (!state.selectedId || !m || !m.id || m.deleted) return;
    var others = state.chats.filter(function (c) {
      return c.id !== state.selectedId;
    });
    if (!others.length) {
      state.error = L("messages.forwardNoChats");
      render();
      return;
    }
    state.forwardPick = {
      messageId: m.id,
      snippet: formatPreviewForMessage(m),
    };
    render();
  }

  function closeForwardPicker() {
    state.forwardPick = null;
    render();
  }

  async function forwardMessageTo(targetChatId) {
    if (!state.forwardPick || !state.selectedId || !targetChatId) return;
    var msgId = state.forwardPick.messageId;
    state.busy = true;
    state.error = null;
    render();
    try {
      var sent = await apiJson("/chats/" + state.selectedId + "/messages/" + msgId + "/forward", {
        method: "POST",
        jsonBody: { target_chat_id: targetChatId },
      });
      closeForwardPicker();
      if (sent && sent.id && sent.chat_id) {
        setChatPreviewFromMessage(sent.chat_id, sent);
      }
      if (targetChatId === state.selectedId && sent) {
        await afterLocalSend(targetChatId, sent);
        render();
        return;
      }
      var title = chatTitleById(targetChatId);
      if (window.confirm(L("messages.forwardOpenConfirm", { title: title }))) {
        await openChatById(targetChatId, { forceReload: !sent });
        if (sent) await afterLocalSend(targetChatId, sent);
      } else {
        render();
      }
    } catch (err) {
      state.error = err.message || L("messages.forwardFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function scheduleGlobalSearch() {
    if (globalSearchTimer) clearTimeout(globalSearchTimer);
    var q = (state.globalSearch || "").trim();
    if (q.length < 2) {
      state.globalSearchHits = null;
      state.globalSearchBusy = false;
      return;
    }
    state.globalSearchBusy = true;
    globalSearchTimer = setTimeout(function () {
      globalSearchTimer = null;
      var query = state.globalSearch.trim();
      if (query.length < 2) {
        state.globalSearchHits = null;
        state.globalSearchBusy = false;
        render();
        return;
      }
      apiJson("/search/messages?q=" + encodeURIComponent(query) + "&limit=30", {
        method: "GET",
      })
        .then(function (hits) {
          if (state.globalSearch.trim() !== query) return;
          state.globalSearchHits = Array.isArray(hits) ? hits : [];
          state.globalSearchBusy = false;
          render();
        })
        .catch(function () {
          if (state.globalSearch.trim() !== query) return;
          state.globalSearchHits = [];
          state.globalSearchBusy = false;
          render();
        });
    }, 350);
  }

  async function openGlobalSearchHit(hit) {
    if (!hit || !hit.id || !hit.chat_id) return;
    state.globalSearch = "";
    state.globalSearchHits = null;
    closeForwardPicker();
    await openChatById(hit.chat_id);
    await openSearchHit(hit);
  }

  async function uploadChatFile(file) {
    var max = state.mediaCaps && state.mediaCaps.max_upload_bytes;
    if (max && file.size > max) {
      throw new Error(L("files.tooLarge", { mb: Math.round(max / (1024 * 1024)) }));
    }
    var fd = new FormData();
    fd.append("file", file, file.name || "file");
    var res = await apiFetch("/files/upload", { method: "POST", body: fd });
    var text = await res.text();
    var parsed = null;
    if (text) {
      try {
        parsed = JSON.parse(text);
      } catch (e) {
        parsed = null;
      }
    }
    if (!res.ok) {
      var msg =
        parsed && typeof parsed === "object" && parsed.message
          ? String(parsed.message)
          : res.statusText;
      throw new Error(msg || L("files.uploadFailed"));
    }
    return parsed;
  }

  async function fetchFileMetadata(fileId) {
    if (!fileId || !state.tokens) return null;
    try {
      return await apiJson("/files/" + fileId, { method: "GET" });
    } catch (e) {
      return null;
    }
  }

  async function attachAuthenticatedImage(fileId, imgEl) {
    try {
      var meta = await fetchFileMetadata(fileId);
      if (meta && meta.filename) {
        imgEl.alt = meta.filename;
        imgEl.title = meta.filename;
      }
      var res = await apiFetch("/files/" + fileId + "/download", {
        method: "GET",
        headers: { Accept: "*/*" },
      });
      if (!res.ok) return;
      var blob = await res.blob();
      var u = URL.createObjectURL(blob);
      state.blobUrls.push(u);
      imgEl.src = u;
      imgEl.style.cursor = "pointer";
      imgEl.title = L("files.openInNewTab");
      imgEl.onclick = function () {
        if (imgEl.src) window.open(imgEl.src, "_blank", "noopener,noreferrer");
      };
      imgEl.onerror = function () {
        imgEl.alt = L("files.imageLoadFailed");
        imgEl.classList.add("msg-attachment-image-error");
      };
    } catch (e) {}
  }

  async function openChatMessageForFile(fileId) {
    if (!fileId || !state.tokens) return;
    state.busy = true;
    state.error = null;
    state.settingsOpen = false;
    render();
    try {
      var ref = await apiJson("/files/" + fileId + "/message-ref", { method: "GET" });
      if (!ref || !ref.chat_id || !ref.message_id) {
        throw new Error(L("files.messageForFileNotFound"));
      }
      await openChatById(ref.chat_id);
      await scrollToMessageId(ref.message_id);
    } catch (e) {
      state.error = e.message || L("messages.jumpFailed");
      state.settingsOpen = true;
    } finally {
      state.busy = false;
      render();
    }
  }

  async function downloadChatFile(fileId) {
    var res = await apiFetch("/files/" + fileId + "/download", {
      method: "GET",
      headers: { Accept: "*/*" },
    });
    if (!res.ok) {
      throw new Error(L("files.downloadFailed"));
    }
    var cd = res.headers.get("Content-Disposition") || "";
    var filename = "file";
    var m = /filename="([^"]+)"/i.exec(cd);
    if (m) filename = m[1];
    var blob = await res.blob();
    var u = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = u;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(u);
  }

  function renderMessageContent(bodyEl, m) {
    var t = m.type;
    var attachKind = messageAttachmentKind(m);
    var fileId = messageAttachmentFileId(m);
    if (attachKind && fileId) {
      appendMessageAttachment(bodyEl, attachKind, fileId);
      return;
    }
    if (isE2eeType(t)) {
      var chatId = m.chat_id || state.selectedId;
      var p = el(
        "p",
        "msg-e2ee-body",
        isMlsCapabilitiesActive() ? L("e2ee.decryptingMls") : L("e2ee.decrypting")
      );
      bodyEl.appendChild(p);
      loadE2eePlaintext(chatId, m.id).then(function (text) {
        if (text) {
          p.textContent = text;
          p.className = "msg-e2ee-body msg-e2ee-decrypted";
        } else if (isMlsCapabilitiesActive()) {
          p.textContent =
            L("e2ee.encryptedMlsPreview");
        } else {
          p.textContent =
            L("e2ee.encryptedE2eePreview");
        }
      });
      return;
    }
    bodyEl.innerHTML = safeMarkdown(m.content || "");
  }

  function clearWsReconnect() {
    if (state.wsReconnectTimer) {
      clearTimeout(state.wsReconnectTimer);
      state.wsReconnectTimer = null;
    }
  }

  function scheduleWsReconnect() {
    clearWsReconnect();
    if (state.wsManualClose || !state.tokens || !state.tokens.access_token) return;
    var delay = uiTransportUtils.nextWsReconnectDelay(state.wsReconnectAttempt);
    state.wsReconnectAttempt += 1;
    state.wsReconnectTimer = setTimeout(function () {
      state.wsReconnectTimer = null;
      if (state.wsManualClose || !state.tokens) return;
      openWs();
    }, delay);
  }

  function closeWs() {
    state.wsManualClose = true;
    clearWsReconnect();
    state.wsReconnectAttempt = 0;
    rtcHangupAll();
    if (state.ws) {
      state.wsReplacing = true;
      try {
        state.ws.close();
      } catch (e) {}
      state.ws = null;
      state.wsReplacing = false;
    }
    state.wsState = "off";
  }

  function openWs() {
    clearWsReconnect();
    if (state.ws) {
      state.wsReplacing = true;
      try {
        state.ws.close();
      } catch (e) {}
      state.ws = null;
      state.wsReplacing = false;
    }
    if (!state.tokens || !state.tokens.access_token) return;
    state.wsManualClose = false;
    var url = uiTransportUtils.buildWsUrl(wsBaseUrl(), state.tokens.access_token);
    state.wsState = "connecting";
    var ws = new WebSocket(url);
    state.ws = ws;
    ws.onopen = function () {
      state.wsReconnectAttempt = 0;
      state.wsState = "open";
      sendHeartbeat();
      render();
    };
    ws.onerror = function () {
      state.wsState = "error";
      render();
    };
    ws.onclose = function () {
      if (state.wsReplacing) return;
      state.ws = null;
      if (state.wsManualClose || !state.tokens) {
        state.wsState = "off";
      } else {
        state.wsState = "reconnecting";
        scheduleWsReconnect();
      }
      render();
    };
    ws.onmessage = function (ev) {
      try {
        var data = JSON.parse(String(ev.data));
        if (data && data.type === "rtc_signal") {
          sendHeartbeatThrottled();
          handleRtcEnvelope(data);
          return;
        }
        if (isTypingEvent(data)) {
          sendHeartbeatThrottled();
          noteTyping(data.chat_id, data.user_id);
          scheduleTypingSidebarRefresh();
          return;
        }
        if (isReadReceiptEvent(data)) {
          sendHeartbeatThrottled();
          applyReadReceiptEvent(data);
          scheduleRender();
          return;
        }
        if (isMessageChangeEvent(data)) {
          sendHeartbeatThrottled();
          applyMessageChangeEvent(data);
          scheduleRender();
          return;
        }
        if (isReactionChangeEvent(data)) {
          sendHeartbeatThrottled();
          applyReactionChangeEvent(data);
          scheduleRender();
          return;
        }
        if (isPinChangeEvent(data)) {
          sendHeartbeatThrottled();
          applyPinChangeEvent(data);
          return;
        }
        if (isConferenceChangeEvent(data)) {
          sendHeartbeatThrottled();
          applyConferenceChangeEvent(data);
          scheduleRender();
          return;
        }
        if (!isMessageSendEvent(data)) return;
        sendHeartbeatThrottled();
        setChatPreviewFromSendEvent(data);
        if (data.chatId !== state.selectedId) {
          maybeNotifyMessage(data);
          var myId = jwtSub(state.tokens.access_token);
          if (!myId || data.senderId !== myId) bumpUnread(data.chatId);
          scheduleRender();
          return;
        }
        if (document.hidden) {
          maybeNotifyMessage(data);
        }
        ingestIncomingMessage(data.chatId, data.messageId, data)
          .then(function () {
            return markChatRead(data.chatId);
          })
          .then(function () {
            state.shouldScrollThread = true;
            scheduleRender();
          })
          .catch(function () {
            loadThread(data.chatId, THREAD_SOFT_RELOAD)
              .then(function () {
                return markChatRead(data.chatId);
              })
              .then(function () {
                state.shouldScrollThread = true;
                scheduleRender();
              })
              .catch(function () {});
          });
      } catch (e) {}
    };
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }

  function iconBtn(icon, tip, opts) {
    var ib = window.KorusIconButtons && window.KorusIconButtons.iconButton;
    if (!ib) {
      var btn = el("button", "btn btn-ghost btn-icon btn-sm", icon);
      btn.type = "button";
      if (tip) {
        btn.title = tip;
        btn.setAttribute("aria-label", tip);
      }
      if (opts && opts.testId) btn.setAttribute("data-testid", opts.testId);
      if (opts && opts.disabled) btn.disabled = true;
      if (opts && opts.onClick) btn.onclick = opts.onClick;
      if (opts && opts.submit) btn.type = "submit";
      return btn;
    }
    return ib(
      Object.assign(
        { icon: icon, tip: tip, sm: opts && opts.sm !== false },
        opts || {}
      )
    );
  }

  function chatActivityMs(c) {
    var prev = state.chatPreview[c.id];
    if (prev && prev.at) return prev.at;
    if (c.created_at) return new Date(c.created_at).getTime();
    return 0;
  }

  function compareChatsForSidebar(a, b) {
    var ua = (state.unreadByChat[a.id] || 0) > 0 ? 1 : 0;
    var ub = (state.unreadByChat[b.id] || 0) > 0 ? 1 : 0;
    if (ua !== ub) return ub - ua;
    var ta = chatActivityMs(a);
    var tb = chatActivityMs(b);
    if (ta !== tb) return tb - ta;
    var na = (a.title || a.id || "").toLowerCase();
    var nb = (b.title || b.id || "").toLowerCase();
    if (na < nb) return -1;
    if (na > nb) return 1;
    return 0;
  }

  function filteredChats() {
    var base = state.chats.filter(function (c) {
      return c.type !== "saved";
    });
    var q = (state.sidebarSearch || "").trim().toLowerCase();
    var list = q
      ? base.filter(function (c) {
          var t = (c.title || c.id || "").toLowerCase();
          return t.indexOf(q) !== -1;
        })
      : base;
    return list.slice().sort(compareChatsForSidebar);
  }

  function chatInitial(title) {
    var t = (title || "?").trim();
    return t.charAt(0).toUpperCase();
  }

  function markBrandNoTranslate(node) {
    if (node) node.setAttribute("translate", "no");
  }

  function brandTagline() {
    return L("ui.brand.tagline");
  }

  function appendAppTitle(parent) {
    var row = el("div", "app-title-row");
    row.appendChild(el("div", "app-title-logo", "K"));
    var h1 = el("h1", null, L("ui.brand.title"));
    markBrandNoTranslate(h1);
    row.appendChild(h1);
    parent.appendChild(row);
  }

  function renderAuth() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var outer = el(
      "div",
      "auth-layout tw:flex tw:min-h-screen tw:flex-col tw:md:flex-row tw:items-stretch tw:justify-center tw:gap-6 tw:md:gap-12 tw:p-4 tw:md:p-8"
    );
    var brand = el(
      "div",
      "auth-brand tw:flex tw:flex-col tw:justify-center tw:flex-1 tw:max-w-lg tw:gap-3"
    );
    var brandLogo = el("div", "auth-brand-logo tw:flex tw:h-14 tw:w-14 tw:items-center tw:justify-center tw:rounded-2xl tw:text-2xl tw:font-bold", "K");
    markBrandNoTranslate(brandLogo);
    brand.appendChild(brandLogo);
    var brandTitle = el("h1", "tw:text-3xl tw:font-semibold tw:tracking-tight", L("ui.brand.title"));
    markBrandNoTranslate(brandTitle);
    brand.appendChild(brandTitle);
    brand.appendChild(el("p", "auth-brand-tag tw:text-base tw:opacity-80", brandTagline()));
    outer.appendChild(brand);
    var card = el(
      "div",
      "auth-card tw:w-full tw:max-w-md tw:rounded-2xl tw:border tw:border-[color-mix(in_srgb,var(--border)_80%,transparent)] tw:bg-[var(--panel)] tw:p-6 tw:shadow-xl tw:self-center"
    );
    card.appendChild(
      el("h2", null, state.authTab === "register" ? L("auth.register") : L("auth.login"))
    );
    card.appendChild(el("p", "auth-hint", L("auth.hint")));
    if (state.error) {
      card.appendChild(
        el(
          "div",
          "error-banner tw:mb-4 tw:rounded-lg tw:border tw:border-red-500/40 tw:bg-red-500/10 tw:px-3 tw:py-2 tw:text-sm",
          state.error
        )
      );
    }
    var tabs = el("div", "tabs");
    var tLogin = el("button", state.authTab === "login" ? "active" : "", L("auth.login"));
    tLogin.type = "button";
    tLogin.onclick = function () {
      state.authTab = "login";
      state.error = null;
      render();
    };
    var tReg = el("button", state.authTab === "register" ? "active" : "", L("auth.register"));
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
    form.appendChild(field("u", L("ui.auth.username"), "text", "username", true, 3, 32));
    var pwdMinLen = state.authTab === "register" ? 8 : null;
    form.appendChild(field("p", L("ui.auth.password"), "password", "password", true, pwdMinLen, null));
    if (state.authTab === "register") {
      form.appendChild(field("d", L("ui.auth.displayName"), "text", null, false, null, null));
    }
    var submit = el(
      "button",
      "btn btn-primary tw:mt-2 tw:w-full tw:rounded-lg tw:font-semibold tw:transition-opacity tw:hover:opacity-90 tw:disabled:opacity-60",
      state.busy
        ? "…"
        : state.authTab === "login"
          ? L("auth.loginSubmit")
          : L("auth.registerSubmit")
    );
    submit.type = "submit";
    submit.disabled = state.busy;
    submit.setAttribute("data-testid", "auth-submit");
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
    var uEl = document.getElementById("u");
    var pEl = document.getElementById("p");
    var dEl = document.getElementById("d");
    var u = uEl ? uEl.value.trim() : "";
    var p = pEl ? pEl.value : "";
    var d = dEl ? dEl.value.trim() : "";
    state.error = null;
    state.busy = true;
    render();
    try {
      if (!u || !p) {
        throw new Error(L("auth.usernamePasswordRequired"));
      }
      if (state.authTab === "register") {
        if (p.length < 8) {
          throw new Error(L("auth.passwordMinLength"));
        }
        await apiJson("/auth/register", {
          method: "POST",
          jsonBody: { username: u, password: p, display_name: d || u },
          noAuth: true,
          noRefresh: true,
        });
      }
      var t = await apiJson("/auth/login", {
        method: "POST",
        jsonBody: { username: u, password: p },
        noAuth: true,
        noRefresh: true,
      });
      saveTokens({
        access_token: t.access_token,
        refresh_token: t.refresh_token,
        expires_in: t.expires_in,
      });
      await initAfterLogin();
    } catch (err) {
      var msg = localErr(err && err.message);
      if (
        state.authTab === "register" &&
        (msg.indexOf("недоступ") !== -1 || msg.indexOf("unavailable") !== -1)
      ) {
        msg = L("auth.registerUnavailable");
      } else if (state.authTab === "register" && msg.indexOf("Неверные") !== -1) {
        msg = L("auth.registerLoginFailed");
      }
      state.error = msg;
    } finally {
      state.busy = false;
      render();
    }
  }

  async function ensureSessionFresh() {
    if (!state.tokens || !state.tokens.refresh_token) {
      return false;
    }
    var stored = state.tokens.stored_at_ms || 0;
    var expiresIn = state.tokens.expires_in || 0;
    if (!expiresIn) {
      return true;
    }
    var expiresAt = stored + expiresIn * 1000;
    if (Date.now() + 60000 < expiresAt) {
      return true;
    }
    return tryRefreshTokens();
  }

  async function logout() {
    stopHeartbeat();
    stopCallMedia();
    exportPollGeneration++;
    chatPreviewHydrateGen++;
    state.exportBusy = false;
    state.exportJobId = null;
    state.exportJobChatId = null;
    state.callPanelOpen = false;
    await unregisterWebPush();
    if (state.tokens && state.tokens.refresh_token) {
      try {
        await apiJson("/auth/logout", {
          method: "POST",
          jsonBody: { refresh_token: state.tokens.refresh_token },
        });
      } catch (e) {}
    }
    revokeBlobUrls();
    clearTokens();
    state.selectedId = null;
    state.chats = [];
    state.messages = [];
    state.mediaCaps = null;
    state.unreadByChat = {};
    state.userSearchHits = null;
    state.userSearchBusy = false;
    state.chatPreview = {};
    state.typingExpireByChat = {};
    state.replyTo = null;
    state.reactionsByMsg = {};
    state.pinnedMessages = [];
    state.threadHasMore = false;
    state.threadLoadingMore = false;
    state.threadSearch = "";
    state.threadSearchHits = null;
    state.composerTtl = "";
    state.forwardPick = null;
    state.globalSearch = "";
    state.globalSearchHits = null;
    state.e2eeKeyCount = null;
    state.settingsOpen = false;
    state.e2eePlaintextCache = {};
    state.localKeyPackageMeta = null;
    state.myDevices = null;
    state.webPushRegistered = false;
    state.webPushError = null;
    state.contacts = null;
    state.sidebarMode = "chats";
    state.membersModalOpen = false;
    state.chatMembers = null;
    state.chatBans = null;
    state.callMode = "mesh";
    state.activeConference = null;
    state.activeConferenceByChat = {};
    state.chatConferences = null;
    clearJitsiIframe();
    state.jitsiIframeEl = null;
    state.contactImportText = "";
    state.statusMessage = null;
    state.lastPublicLink = null;
    saveLastPublicLink(null);
    state.incomingRtcCall = null;
    stopIncomingCallRing();
    state.messageVersionsOpen = false;
    state.fileLinksOpen = false;
    state.fileLinksFileId = null;
    state.fileLinksRows = null;
    state.fileLinksBusy = false;
    state.myPublicLinks = null;
    state.myPublicLinksBusy = false;
    closeWs();
    document.title = L("ui.brand.title");
    render();
  }

  async function newGroup() {
    var title = window.prompt(L("chat.groupTitlePrompt"));
    if (!title || !title.trim() || !state.tokens) return;
    var membersRaw =
      window.prompt(L("chat.createMembersPrompt")) || "";
    var memberIds = membersRaw
      .split(/[,;\s]+/)
      .map(function (s) {
        return s.trim();
      })
      .filter(function (s) {
        return s.length > 0;
      });
    state.error = null;
    state.busy = true;
    render();
    try {
      var chat = await apiJson("/chats", {
        method: "POST",
        jsonBody: { type: "group", title: title.trim(), member_ids: memberIds },
      });
      await refreshChats();
      await openChatById(chat.id, { forceReload: true });
    } catch (err) {
      state.error = err.message || L("chat.createFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function openP2pChat(userId) {
    if (!userId || !state.tokens) return;
    state.error = null;
    state.busy = true;
    render();
    try {
      var chat = await apiJson("/chats", {
        method: "POST",
        jsonBody: { type: "p2p", title: null, member_ids: [userId] },
      });
      state.sidebarSearch = "";
      state.userSearchHits = null;
      await refreshChats();
      await openChatById(chat.id, { forceReload: true });
    } catch (err) {
      state.error = err.message || L("chat.openDmFailed");
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

  async function sendFileMessage(file) {
    if (!file || !state.tokens || !state.selectedId) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var up = await uploadChatFile(file);
      var msgType = messageTypeForMime((up && up.mime_type) || file.type);
      var body = {
          type: msgType,
          content: up.id,
          reply_to_msg_id: currentReplyToId(),
          client_msg_id: null,
          visibility_ttl_seconds: getComposerTtlSeconds(),
        };
      var scheme = preferredE2eeScheme();
      if (scheme) {
        body.e2ee_scheme = scheme;
        if (scheme === "mls") await mlsEnsureKeyPackage();
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("messages.sendFileFailed");
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
      var body = {
          type: "text",
          content: text,
          reply_to_msg_id: currentReplyToId(),
          client_msg_id: null,
          visibility_ttl_seconds: getComposerTtlSeconds(),
        };
      var scheme = preferredE2eeScheme();
      if (scheme) {
        body.e2ee_scheme = scheme;
        if (scheme === "mls") {
          await mlsEnsureKeyPackage();
          var enc = await mlsClientEncrypt(text, state.selectedId);
          if (!enc) {
            throw new Error("MLS client encrypt failed — reload page or check browser crypto support");
          }
          body.content = enc;
        }
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      ta.value = "";
      clearComposerDraftForChat(state.selectedId);
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("messages.sendFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function renderCallPanel(shell) {
    if (!state.callPanelOpen) return;
    var panel = el("aside", "call-panel");
    var ph = el("div", "call-panel-head");
    var titleSpan = el("span", "call-panel-title", L("ui.call.title"));
    titleSpan.setAttribute("data-testid", "call-panel-title");
    ph.appendChild(titleSpan);
    var cl = iconBtn("✕", L("ui.call.collapse"), {
      onClick: function () {
        toggleCallPanel();
      },
    });
    ph.appendChild(cl);
    panel.appendChild(ph);
    var modeBar = el("div", "call-mode-bar");
    var bMesh = iconBtn("📡", "Mesh WebRTC", {
      primary: state.callMode === "mesh",
      testId: "mesh-webrtc-button",
      disabled: state.conferenceBusy,
      onClick: function () {
        switchCallMode("mesh");
      },
    });
    var bJitsi = iconBtn("🎥", "Jitsi", {
      primary: state.callMode === "jitsi",
      disabled: state.conferenceBusy || state.busy,
      onClick: function () {
        switchCallMode("jitsi");
      },
    });
    modeBar.appendChild(bMesh);
    modeBar.appendChild(bJitsi);
    panel.appendChild(modeBar);
    if (state.tokens) {
      var confSec = el("div", "call-conferences");
      var confHead = el("div", "call-conferences-head");
      var confTitle = el("div", "call-conferences-title");
      confTitle.textContent = L("conference.sectionTitle");
      confHead.appendChild(confTitle);
      confHead.appendChild(
        iconBtn("↻", L("conference.refreshList"), {
          disabled: state.conferenceBusy || state.busy,
          onClick: function () {
            loadActiveConferences()
              .then(function () {
                return loadChatConferences();
              })
              .then(render)
              .catch(function () {
                render();
              });
          },
        })
      );
      confSec.appendChild(confHead);
      confSec.appendChild(el("p", "call-hint", L("conference.inviteHint")));
      var confActions = el("div", "call-conferences-actions");
      var bNewConf = iconBtn("＋", state.conferenceBusy ? L("conference.creating") : L("conference.create"), {
        primary: true,
        disabled: state.conferenceBusy || state.busy,
        onClick: function () {
          createConference();
        },
      });
      var bJoinLink = iconBtn("🔗", L("conference.joinByLink"), {
        disabled: state.conferenceBusy || state.busy,
        onClick: function () {
          joinConferenceByLink();
        },
      });
      confActions.appendChild(bNewConf);
      if (state.selectedId && state.selectedId !== state.savedChatId) {
        confActions.appendChild(
          iconBtn("🎬", L("conference.startInChat"), {
            disabled: state.conferenceBusy || state.busy,
            onClick: function () {
              createConferenceInChat();
            },
          })
        );
      }
      confActions.appendChild(bJoinLink);
      confSec.appendChild(confActions);
      var confList = listUserActiveConferences();
      if (confList.length) {
        confList.forEach(function (c) {
          var row = el("div", "call-conf-row");
          var chatLabel = c.chat_id ? chatTitleById(c.chat_id) : "";
          var baseTitle =
            (c.title && c.title.trim() ? c.title : c.room_slug || (c.conference_id || "").slice(0, 8)) +
            (chatLabel ? " · " + chatLabel : "") +
            conferenceParticipantsLabel(c.participant_count) +
            (state.activeConference &&
            state.activeConference.conference_id === c.conference_id
              ? " · " + L("conference.live")
              : "");
          row.appendChild(el("span", "call-conf-label", baseTitle));
          row.appendChild(
            iconBtn("▶", L("conference.join"), {
              disabled: state.conferenceBusy,
              onClick: function () {
                joinJitsiConference(c);
              },
            })
          );
          confSec.appendChild(row);
        });
      } else {
        confSec.appendChild(el("p", "call-conf-empty", L("conference.noneActive")));
      }
      panel.appendChild(confSec);
    }
    if (state.callMode === "mesh" && !meshCallChatReady()) {
      panel.appendChild(el("p", "call-hint call-hint-warn", L("conference.meshNeedsChatHint")));
    }
    if (state.callMode === "jitsi" && state.activeConference && state.activeConference.join_url) {
      var jHint = el("p", "call-hint");
      jHint.textContent = L("conference.jitsiHint", {
        host:
          state.mediaCaps && state.mediaCaps.jitsi_base_url
            ? state.mediaCaps.jitsi_base_url
            : "meet.jit.si",
      });
      panel.appendChild(jHint);
      if (
        conferenceIsTracked(state.activeConference) &&
        state.activeConference.conference_id &&
        state.conferenceParticipantsConfId !== state.activeConference.conference_id
      ) {
        loadConferenceParticipants(state.activeConference.conference_id)
          .then(render)
          .catch(function () {});
      }
      if (conferenceIsTracked(state.activeConference)) {
        var partSec = el("div", "call-participants");
        var partHead = el("div", "call-participants-head");
        partHead.appendChild(el("span", "call-participants-title", L("conference.participantsTitle")));
        partHead.appendChild(
          iconBtn("↻", L("conference.refreshParticipants"), {
            onClick: function () {
              loadConferenceParticipants(state.activeConference.conference_id)
                .then(render)
                .catch(function () {
                  render();
                });
            },
          })
        );
        partSec.appendChild(partHead);
        var partList = el("ul", "call-participants-list");
        var participants = state.conferenceParticipantsList;
        if (participants && participants.length) {
          participants.forEach(function (p) {
            var li = el("li", "call-participant-row", conferenceParticipantLabel(p));
            partList.appendChild(li);
          });
        } else if (participants && !participants.length) {
          partList.appendChild(
            el("li", "call-participant-row call-participant-empty", L("conference.noParticipants"))
          );
        } else {
          partList.appendChild(el("li", "call-participant-row call-participant-empty", "…"));
        }
        partSec.appendChild(partList);
        panel.appendChild(partSec);
      }
      var jWrap = el("div", "call-jitsi-wrap");
      var iframe = getOrCreateJitsiIframe();
      if (iframe.src !== state.activeConference.join_url) {
        iframe.src = state.activeConference.join_url;
      }
      jWrap.appendChild(iframe);
      panel.appendChild(jWrap);
      var jBar = el("div", "call-toolbar");
      jBar.appendChild(
        iconBtn("📋", L("conference.copyLinkHint"), {
          onClick: function () {
            copyConferenceLink();
          },
        })
      );
      jBar.appendChild(
        iconBtn("↻", L("conference.reloadJitsiHint"), {
          onClick: function () {
            reloadJitsiIframe();
          },
        })
      );
      if (conferenceIsTracked(state.activeConference)) {
        jBar.appendChild(
          iconBtn("➕", L("conference.inviteMembers"), {
            disabled: state.busy,
            onClick: function () {
              inviteMembersToMeetingChat(state.activeConference);
            },
          })
        );
        jBar.appendChild(
          iconBtn("📢", L("conference.repostInvite"), {
            disabled: state.busy,
            onClick: function () {
              postMeetingInviteMessage(state.activeConference.chat_id, state.activeConference)
                .then(function () {
                  state.statusMessage = L("conference.invitePosted");
                  render();
                })
                .catch(function (e) {
                  state.error = localErr(e.message) || L("conference.invitePostFailed");
                  render();
                });
            },
          })
        );
      }
      jBar.appendChild(
        iconBtn("🚪", L("conference.leave"), {
          onClick: function () {
            leaveActiveConference();
          },
        })
      );
      jBar.appendChild(
        iconBtn("⏹", L("conference.endAll"), {
          disabled: state.busy || !conferenceIsTracked(state.activeConference),
          onClick: function () {
            endActiveConference();
          },
        })
      );
      if (!conferenceIsTracked(state.activeConference)) {
        jBar.lastChild.title = L("conference.endGuestHint");
        jBar.lastChild.setAttribute("aria-label", L("conference.endGuestHint"));
      }
      panel.appendChild(jBar);
      shell.appendChild(panel);
      return;
    }
    panel.appendChild(
      el(
        "p",
        "call-hint",
        "WebRTC mesh через NATS (rtc.signal). ICE: STUN по умолчанию; TURN — WEB_CLIENT_RTC_ICE_SERVERS в /web-client-env.js."
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
    thumbs.appendChild(el("span", "call-thumbs-label", L("ui.call.thumbs")));
    var c1 = document.createElement("canvas");
    c1.className = "call-thumb-canvas";
    var c2 = document.createElement("canvas");
    c2.className = "call-thumb-canvas";
    thumbs.appendChild(c1);
    thumbs.appendChild(c2);
    panel.appendChild(thumbs);
    var remotes = el("div", "call-remotes");
    remotes.appendChild(el("div", "call-remotes-title", L("ui.call.remotes")));
    state.rtcPeerIds.forEach(function (pid) {
      var slot = el("div", "rtc-remote-slot");
      slot.id = "rtc-remote-" + pid;
      slot.appendChild(el("div", "rtc-remote-label", pid.slice(0, 8) + "…"));
      var rv = document.createElement("video");
      rv.className = "call-video rtc-remote-video rtc-remote-cam";
      rv.autoplay = true;
      rv.playsInline = true;
      rv.setAttribute("playsinline", "");
      slot.appendChild(rv);
      var rs = document.createElement("video");
      rs.className = "call-video rtc-remote-video rtc-remote-screen";
      rs.autoplay = true;
      rs.playsInline = true;
      rs.setAttribute("playsinline", "");
      rs.style.display = "none";
      slot.appendChild(rs);
      remotes.appendChild(slot);
    });
    if (!state.rtcPeerIds.length) {
      remotes.appendChild(
        el("div", "call-participant-slot", L("ui.call.aloneInChat"))
      );
    }
    panel.appendChild(remotes);
    var bar = el("div", "call-toolbar");
    var bMic = iconBtn(state.callMicOn ? "🎤" : "🔇", state.callMicOn ? L("ui.call.micOn") : L("ui.call.micOff"), {
      primary: state.callMicOn,
      size: "md",
      onClick: function () {
        toggleCallMic();
      },
    });
    var bCam = iconBtn(state.callCamOn ? "📷" : "📷", state.callCamOn ? L("ui.call.camOn") : L("ui.call.camOff"), {
      primary: state.callCamOn,
      size: "md",
      onClick: function () {
        toggleCallCam();
      },
    });
    var bScr = iconBtn("🖥", state.callScreenStream ? L("ui.call.stopScreen") : L("ui.call.screen"), {
      size: "md",
      onClick: function () {
        toggleScreenShare();
      },
    });
    bar.appendChild(bMic);
    bar.appendChild(bCam);
    bar.appendChild(bScr);
    panel.appendChild(bar);
    shell.appendChild(panel);
    setTimeout(attachLocalVideo, 0);
  }

  function normalizeSettingsTab(tab) {
    if (tab && SETTINGS_TAB_IDS.indexOf(tab) >= 0) return tab;
    return "general";
  }

  function getSettingsTab() {
    if (state.settingsTab) return normalizeSettingsTab(state.settingsTab);
    try {
      var stored = localStorage.getItem(SETTINGS_TAB_KEY);
      if (stored) return normalizeSettingsTab(stored);
    } catch (e) {}
    return "general";
  }

  function setSettingsTab(tabId) {
    var next = normalizeSettingsTab(tabId);
    if (getSettingsTab() === next && state.settingsTab === next) return;
    state.settingsTab = next;
    try {
      localStorage.setItem(SETTINGS_TAB_KEY, next);
    } catch (e2) {}
    render();
  }

  function closeSettingsModal() {
    state.settingsOpen = false;
    render();
  }

  function appendSettingsGeneralPanel(panel) {
    var rowTheme = el("div", "settings-row");
    rowTheme.appendChild(el("span", null, L("ui.settings.appearance")));
    rowTheme.appendChild(
      iconBtn(state.appearance === "light" ? "🌙" : "☀️", state.appearance === "light" ? L("ui.common.darkTheme") : L("ui.common.lightTheme"), {
        onClick: function () {
          toggleAppearance();
        },
      })
    );
    panel.appendChild(rowTheme);

    var rowLocale = el("div", "settings-row settings-row-locale");
    rowLocale.appendChild(el("span", null, L("settings.locale")));
    var localePicker = el("div", "settings-locale-picker");
    localePicker.setAttribute("role", "group");
    localePicker.setAttribute("aria-label", L("settings.locale"));
    var localeCodes = i18n.supportedLocales ? i18n.supportedLocales() : ["ru", "en"];
    var currentLocale = i18n.getLocale();
    localeCodes.forEach(function (code) {
      var labelKey = LOCALE_LABEL_KEYS[code] || "settings.localeEn";
      var bLoc = el(
        "button",
        "btn btn-ghost btn-sm" + (currentLocale === code ? " active" : ""),
        L(labelKey)
      );
      bLoc.type = "button";
      bLoc.setAttribute("data-testid", "locale-" + code);
      bLoc.setAttribute("aria-pressed", currentLocale === code ? "true" : "false");
      bLoc.onclick = (function (localeCode) {
        return function () {
          i18n
            .setLocale(localeCode)
            .then(function () {
              return persistUiLocale(localeCode, { silent: false });
            })
            .then(function () {
              render();
            });
        };
      })(code);
      localePicker.appendChild(bLoc);
    });
    rowLocale.appendChild(localePicker);
    panel.appendChild(rowLocale);
    panel.appendChild(el("p", "settings-hint", L("ui.settings.appearanceKorus")));

    var rowCache = el("div", "settings-row");
    rowCache.appendChild(el("span", null, L("ui.settings.cache")));
    rowCache.appendChild(
      iconBtn("🗑", L("ui.settings.resetCache"), {
        disabled: state.busy,
        onClick: function () {
          resetAppUiCache();
        },
      })
    );
    panel.appendChild(rowCache);
    panel.appendChild(el("p", "settings-hint", L("ui.settings.cacheHint")));

    if (state.serverVersion) {
      var rowVer = el("div", "settings-row");
      rowVer.appendChild(el("span", null, L("ui.settings.apiVersion")));
      rowVer.appendChild(el("span", "settings-value", state.serverVersion));
      panel.appendChild(rowVer);
    }

    var kbdHint = document.createElement("p");
    kbdHint.className = "settings-hint settings-kbd-hint";
    kbdHint.innerHTML = L("ui.settings.kbdHint");
    panel.appendChild(kbdHint);

    if (state.pwaInstallPrompt || deferredInstallPrompt) {
      var rowPwa = el("div", "settings-row");
      rowPwa.appendChild(el("span", null, L("ui.settings.pwaInstall")));
      rowPwa.appendChild(
        iconBtn("📲", L("ui.settings.pwaInstallBtn"), {
          primary: true,
          onClick: function () {
            promptInstallPwa();
          },
        })
      );
      panel.appendChild(rowPwa);
    }

    if ("serviceWorker" in navigator) {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.offlineHint")));
    }
  }

  function appendSettingsProfilePanel(panel) {
    var rowPres = el("div", "settings-row");
    var presLabel = document.createElement("label");
    presLabel.setAttribute("for", "settings-presence");
    presLabel.textContent = L("ui.settings.status");
    rowPres.appendChild(presLabel);
    var presSel = document.createElement("select");
    presSel.id = "settings-presence";
    presSel.name = "presence";
    presSel.className = "settings-select";
    ["online", "away", "dnd", "offline"].forEach(function (st) {
      var opt = document.createElement("option");
      opt.value = st;
      opt.textContent = L(PRESENCE_LABEL_KEYS[st] || st);
      if (st === state.myPresence) opt.selected = true;
      presSel.appendChild(opt);
    });
    presSel.disabled = state.busy;
    presSel.onchange = function () {
      updatePresence(presSel.value);
    };
    rowPres.appendChild(presSel);
    panel.appendChild(rowPres);

    var rowProf = el("div", "settings-row");
    var nameLabel = document.createElement("label");
    nameLabel.setAttribute("for", "settings-display-name");
    nameLabel.textContent = L("ui.settings.name");
    rowProf.appendChild(nameLabel);
    var nameInp = document.createElement("input");
    nameInp.type = "text";
    nameInp.id = "settings-display-name";
    nameInp.name = "displayName";
    nameInp.setAttribute("autocomplete", "name");
    nameInp.className = "settings-text-input";
    nameInp.value = state.myDisplayName || "";
    nameInp.disabled = state.busy;
    nameInp.oninput = function () {
      state.myDisplayName = nameInp.value;
    };
    rowProf.appendChild(nameInp);
    rowProf.appendChild(
      iconBtn("💾", L("ui.common.save"), {
        disabled: state.busy,
        onClick: function () {
          saveMyProfile();
        },
      })
    );
    panel.appendChild(rowProf);

    if (state.blockedUsers && state.blockedUsers.length) {
      var rowBl = el("div", "settings-row");
      rowBl.appendChild(
        el("span", null, L("ui.settings.blockedUsers", { count: state.blockedUsers.length }))
      );
      panel.appendChild(rowBl);
      state.blockedUsers.forEach(function (bu) {
        var rowBu = el("div", "settings-row settings-row-sub");
        var buLabel = bu.display_name || bu.username || bu.user_id;
        rowBu.appendChild(el("span", "settings-value", buLabel));
        rowBu.appendChild(
          iconBtn("✓", L("ui.settings.unblockShort"), {
            disabled: state.busy,
            onClick: function () {
              unblockUser(bu.user_id);
            },
          })
        );
        panel.appendChild(rowBu);
      });
    }
  }

  function appendSettingsNotificationsPanel(panel) {
    var rowNotif = el("div", "settings-row");
    rowNotif.appendChild(el("span", null, L("ui.settings.notifications")));
    rowNotif.appendChild(
      iconBtn(notificationsAllowed() ? "🔕" : "🔔", notificationsAllowed() ? L("ui.common.off") : L("ui.common.on"), {
        onClick: function () {
          if (notificationsAllowed()) {
            state.notifyPref = false;
            localStorage.setItem(NOTIF_KEY, "0");
            unregisterWebPush().then(render);
          } else {
            enableNotifications();
          }
        },
      })
    );
    panel.appendChild(rowNotif);

    var rowSound = el("div", "settings-row");
    rowSound.appendChild(el("span", null, L("ui.settings.sound")));
    rowSound.appendChild(
      iconBtn(state.soundNotify ? "🔇" : "🔊", state.soundNotify ? L("ui.common.off") : L("ui.common.on"), {
        onClick: function () {
          state.soundNotify = !state.soundNotify;
          localStorage.setItem(SOUND_NOTIF_KEY, state.soundNotify ? "1" : "0");
          if (!state.soundNotify) {
            stopIncomingCallRing();
          } else {
            syncIncomingCallRing();
          }
          render();
        },
      })
    );
    panel.appendChild(rowSound);

    if (notificationsAllowed()) {
      var rowTestN = el("div", "settings-row");
      rowTestN.appendChild(el("span", null, L("ui.settings.testNotification")));
      rowTestN.appendChild(
        iconBtn("🔔", L("ui.settings.showTest"), {
          onClick: function () {
            testLocalNotification();
          },
        })
      );
      panel.appendChild(rowTestN);
    }

    if (vapidPublicKey() && notificationsAllowed()) {
      var rowPush = el("div", "settings-row");
      rowPush.appendChild(el("span", null, L("ui.settings.webPush")));
      rowPush.appendChild(
        iconBtn("↻", L("ui.settings.pushSyncUpdate"), {
          disabled: state.busy,
          onClick: function () {
            resyncWebPush();
          },
        })
      );
      panel.appendChild(rowPush);
    }

    if (state.myDevices && state.myDevices.length) {
      var rowDev = el("div", "settings-row");
      rowDev.appendChild(
        el("span", null, L("ui.settings.devicesCount", { count: state.myDevices.length }))
      );
      panel.appendChild(rowDev);
      state.myDevices.forEach(function (d) {
        var rowD = el("div", "settings-row settings-row-sub");
        var active = d.push_active === true;
        var label =
          (d.device_name || "?") +
          (active ? L("ui.settings.devicePushActive") : L("ui.settings.devicePushOff")) +
          (d.push_provider ? " (" + d.push_provider + ")" : "");
        rowD.appendChild(el("span", "settings-value", label));
        rowD.appendChild(
          iconBtn("✕", L("ui.settings.resetPushTitle"), {
            disabled: state.busy,
            onClick: function () {
              unregisterDevice(d.device_name);
            },
          })
        );
        panel.appendChild(rowD);
      });
    } else if (state.myDevices) {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.noDevices")));
    }

    if (state.webPushError && vapidPublicKey()) {
      panel.appendChild(el("p", "settings-hint settings-hint-error", state.webPushError));
    }
  }

  function appendSettingsLinksPanel(panel) {
    if (state.lastPublicLink) {
      var rowRevoke = el("div", "settings-row");
      rowRevoke.appendChild(el("span", null, L("ui.settings.lastPublicLink")));
      rowRevoke.appendChild(
        iconBtn("🚫", L("ui.common.revoke"), {
          disabled: state.busy,
          onClick: function () {
            revokeLastPublicLink();
          },
        })
      );
      panel.appendChild(rowRevoke);
    }

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.myPublicLinks")));
    if (state.myPublicLinksBusy) {
      panel.appendChild(el("p", "settings-hint", L("ui.common.loading")));
    } else if (state.myPublicLinks && state.myPublicLinks.length) {
      state.myPublicLinks.forEach(function (row) {
        var linkRow = el("div", "file-link-row");
        var head = el("div", "file-link-row-head");
        var fname = row.filename || row.file_id.slice(0, 8);
        head.textContent = L("ui.settings.myLinkRow", {
          name: fname,
          kind: row.link_kind || "?",
          expires: formatInstantLabel(row.expires_at),
        });
        linkRow.appendChild(head);
        var acts = el("div", "file-link-row-actions");
        acts.appendChild(
          iconBtn("↗", L("ui.settings.goToMessage"), {
            disabled: state.busy,
            onClick: function () {
              openChatMessageForFile(row.file_id);
            },
          })
        );
        acts.appendChild(
          iconBtn("⬇", L("ui.common.download"), {
            disabled: state.busy,
            onClick: function () {
              downloadChatFile(row.file_id).catch(function (e) {
                state.error = e.message || L("files.downloadFailed");
                render();
              });
            },
          })
        );
        acts.appendChild(
          iconBtn("🚫", L("ui.common.revoke"), {
            disabled: state.busy,
            onClick: function () {
              revokeFilePublicLink(row.file_id, row.id);
            },
          })
        );
        linkRow.appendChild(acts);
        panel.appendChild(linkRow);
      });
    } else if (state.myPublicLinks) {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.noPublicLinks")));
    }

    var rowLinksRefresh = el("div", "settings-row");
    rowLinksRefresh.appendChild(
      iconBtn("↻", L("ui.settings.refreshLinks"), {
        disabled: state.busy || state.myPublicLinksBusy,
        onClick: function () {
          loadMyPublicLinks().then(render);
        },
      })
    );
    panel.appendChild(rowLinksRefresh);
  }

  function appendSettingsSecurityPanel(panel) {
    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.e2eeSection")));

    if (state.e2eeKeyCount !== null) {
      var rowE2 = el("div", "settings-row");
      rowE2.appendChild(el("span", null, L("ui.settings.e2eeKeyPackages")));
      rowE2.appendChild(el("span", "settings-value", String(state.e2eeKeyCount)));
      panel.appendChild(rowE2);
    }

    if (state.serverKeyPackages && state.serverKeyPackages.length) {
      state.serverKeyPackages.forEach(function (kp) {
        var rowKpItem = el("div", "settings-row settings-row-sub");
        var pkHint = kp.public_key ? String(kp.public_key).slice(0, 14) + "…" : kp.id;
        rowKpItem.appendChild(el("span", "settings-value", pkHint));
        rowKpItem.appendChild(
          iconBtn("🗑", L("ui.actions.delete"), {
            disabled: state.busy,
            onClick: function () {
              deleteServerKeyPackage(kp.id);
            },
          })
        );
        panel.appendChild(rowKpItem);
      });
    }

    var rowKp = el("div", "settings-row");
    rowKp.appendChild(el("span", null, L("ui.settings.newKeyPackage")));
    rowKp.appendChild(
      iconBtn("＋", L("ui.settings.create"), {
        disabled: state.busy,
        onClick: function () {
          createAndUploadKeyPackage();
        },
      })
    );
    panel.appendChild(rowKp);

    var rowKeyIo = el("div", "settings-row");
    rowKeyIo.appendChild(el("span", null, L("ui.settings.localKey")));
    rowKeyIo.appendChild(
      iconBtn("📤", L("ui.settings.exportKey"), {
        disabled: !state.localKeyPackageMeta,
        onClick: function () {
          exportLocalKeyPackage();
        },
      })
    );
    rowKeyIo.appendChild(
      iconBtn("📥", L("ui.settings.importKey"), {
        onClick: function () {
          importLocalKeyPackage();
        },
      })
    );
    panel.appendChild(rowKeyIo);

    if (state.localKeyPackageMeta) {
      var rowLoc = el("div", "settings-row");
      rowLoc.appendChild(
        el(
          "span",
          null,
          L("ui.settings.localKeySaved", {
            prefix:
              state.localKeyPackageMeta.public_key_prefix || L("ui.settings.localKeySavedYes"),
          })
        )
      );
      rowLoc.appendChild(
        iconBtn("🗑", L("ui.actions.delete"), {
          onClick: function () {
            wipeLocalKeyPackage();
          },
        })
      );
      panel.appendChild(rowLoc);
    }

    var privateHint = el("p", "settings-hint");
    privateHint.setAttribute("translate", "no");
    var pushHint = L("ui.settings.privateKeyHint");
    if (vapidPublicKey()) {
      pushHint += state.webPushRegistered
        ? L("ui.settings.webPushHintRegistered")
        : L("ui.settings.webPushHintDefault");
    } else {
      pushHint += L("ui.settings.webPushNoVapid");
    }
    privateHint.textContent = pushHint;
    panel.appendChild(privateHint);
  }

  function renderSettingsModal(shell) {
    if (!state.settingsOpen) return;

    var activeTab = getSettingsTab();
    state.settingsTab = activeTab;

    var sOv = el("div", "settings-overlay");
    var sCard = el("div", "settings-card");
    sCard.setAttribute("role", "dialog");
    sCard.setAttribute("aria-modal", "true");
    sCard.setAttribute("aria-labelledby", "settings-dialog-title");

    var sTitle = el("h2", "settings-title", L("ui.settings.title"));
    sTitle.id = "settings-dialog-title";
    sCard.appendChild(sTitle);

    var tablist = el("div", "settings-tablist");
    tablist.setAttribute("role", "tablist");
    tablist.setAttribute("aria-label", L("ui.settings.tabListLabel"));
    SETTINGS_TAB_IDS.forEach(function (tabId) {
      var tabBtn = el(
        "button",
        "settings-tab" + (tabId === activeTab ? " active" : ""),
        L(SETTINGS_TAB_LABEL_KEYS[tabId] || tabId)
      );
      tabBtn.type = "button";
      tabBtn.id = "settings-tab-" + tabId;
      tabBtn.setAttribute("role", "tab");
      tabBtn.setAttribute("data-testid", "settings-tab-" + tabId);
      tabBtn.setAttribute("aria-selected", tabId === activeTab ? "true" : "false");
      tabBtn.setAttribute("aria-controls", "settings-panel-" + tabId);
      tabBtn.tabIndex = tabId === activeTab ? 0 : -1;
      tabBtn.onclick = (function (id) {
        return function () {
          setSettingsTab(id);
        };
      })(tabId);
      tabBtn.onkeydown = (function (id) {
        return function (e) {
          var idx = SETTINGS_TAB_IDS.indexOf(id);
          if (idx < 0) return;
          var next = null;
          if (e.key === "ArrowRight") {
            next = SETTINGS_TAB_IDS[(idx + 1) % SETTINGS_TAB_IDS.length];
          } else if (e.key === "ArrowLeft") {
            next = SETTINGS_TAB_IDS[(idx - 1 + SETTINGS_TAB_IDS.length) % SETTINGS_TAB_IDS.length];
          } else if (e.key === "Home") {
            next = SETTINGS_TAB_IDS[0];
          } else if (e.key === "End") {
            next = SETTINGS_TAB_IDS[SETTINGS_TAB_IDS.length - 1];
          }
          if (!next) return;
          e.preventDefault();
          setSettingsTab(next);
        };
      })(tabId);
      tablist.appendChild(tabBtn);
    });
    sCard.appendChild(tablist);

    var sBody = el("div", "settings-body");
    SETTINGS_TAB_IDS.forEach(function (tabId) {
      var tabPanel = el("div", "settings-tabpanel");
      tabPanel.id = "settings-panel-" + tabId;
      tabPanel.setAttribute("role", "tabpanel");
      tabPanel.setAttribute("aria-labelledby", "settings-tab-" + tabId);
      if (tabId !== activeTab) {
        tabPanel.hidden = true;
      } else if (tabId === "general") {
        appendSettingsGeneralPanel(tabPanel);
      } else if (tabId === "profile") {
        appendSettingsProfilePanel(tabPanel);
      } else if (tabId === "notifications") {
        appendSettingsNotificationsPanel(tabPanel);
      } else if (tabId === "links") {
        appendSettingsLinksPanel(tabPanel);
      } else if (tabId === "security") {
        appendSettingsSecurityPanel(tabPanel);
      }
      sBody.appendChild(tabPanel);
    });
    sCard.appendChild(sBody);

    var sClose = iconBtn("✕", L("ui.common.close"), {
      primary: true,
      testId: "settings-close",
      onClick: closeSettingsModal,
    });
    sCard.appendChild(sClose);

    sOv.appendChild(sCard);
    sOv.onclick = function (e) {
      if (e.target === sOv) closeSettingsModal();
    };
    shell.appendChild(sOv);
  }

  function renderMain() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el("div", "app-shell messenger-shell" + (state.callPanelOpen ? " call-open" : ""));
    if (state.networkOnline === false) {
      var netBanner = el("div", "network-banner");
      netBanner.textContent = L("errors.networkOffline");
      shell.appendChild(netBanner);
    }
    if (state.swUpdateReady) {
      var swBanner = el("div", "sw-update-banner");
      swBanner.appendChild(document.createTextNode(L("ui.shell.swUpdate")));
      var swBtn = iconBtn("↻", L("ui.common.update"), {
        primary: true,
        onClick: function () {
          applyServiceWorkerUpdate();
        },
      });
      swBanner.appendChild(swBtn);
      shell.appendChild(swBanner);
    }
    if (
      state.exportBusy &&
      state.exportJobId &&
      state.exportJobChatId
    ) {
      var exBanner = el("div", "export-progress-banner");
      var exLabel = L("ui.shell.exportBanner", {
        chat: chatTitleById(state.exportJobChatId),
        bg: state.exportJobChatId === state.selectedId ? "" : L("common.exportBackground"),
        status: state.exportProgressLabel || L("common.exportStarting"),
      });
      exBanner.appendChild(document.createTextNode(exLabel));
      var exCancel = iconBtn("⏹", L("ui.common.cancel"), {
        disabled: state.busy,
        onClick: function () {
          cancelChatExport();
        },
      });
      exBanner.appendChild(exCancel);
      shell.appendChild(exBanner);
    }
    var header = el("header", "app-header");
    var hl = el("div", "app-header-left");
    appendAppTitle(hl);
    var gSearchWrap = el("div", "global-search-wrap");
    var gSearch = document.createElement("input");
    gSearch.type = "search";
    gSearch.className = "global-search-input";
    gSearch.placeholder = L("ui.shell.globalSearchPlaceholder");
    gSearch.value = state.globalSearch;
    gSearch.oninput = function () {
      state.globalSearch = gSearch.value;
      scheduleGlobalSearch();
      render();
    };
    gSearchWrap.appendChild(gSearch);
    hl.appendChild(gSearchWrap);
    header.appendChild(hl);
    var hdrR = el("div", "app-header-right");
    var callTip = state.callPanelOpen ? L("ui.shell.hideVideo") : L("ui.shell.showVideo");
    var callBtn = iconBtn(state.callPanelOpen ? "📵" : "📹", callTip, {
      testId: "call-panel-toggle",
      onClick: function () {
        toggleCallPanel();
      },
    });
    hdrR.appendChild(callBtn);
    if (state.e2eeKeyCount !== null) {
      var e2eeSpan = el("span", "e2ee-status");
      e2eeSpan.title = L("ui.shell.e2eeTitle");
      e2eeSpan.textContent = L("ui.shell.e2eeCount", { count: state.e2eeKeyCount });
      hdrR.appendChild(e2eeSpan);
    }
    var themeBtn = el(
      "button",
      "btn btn-ghost btn-icon",
      state.appearance === "light" ? "🌙" : "☀️"
    );
    themeBtn.type = "button";
    themeBtn.title =
      state.appearance === "light" ? L("ui.common.darkTheme") : L("ui.common.lightTheme");
    themeBtn.onclick = function () {
      toggleAppearance();
    };
    hdrR.appendChild(themeBtn);
    var notifBtn = el("button", "btn btn-ghost btn-icon", notificationsAllowed() ? "🔔" : "🔕");
    notifBtn.type = "button";
    notifBtn.title = notificationsAllowed()
      ? L("ui.shell.notifDisable")
      : L("ui.shell.notifEnable");
    notifBtn.onclick = function () {
      if (notificationsAllowed()) {
        state.notifyPref = false;
        localStorage.setItem(NOTIF_KEY, "0");
        unregisterWebPush().then(render);
      } else {
        enableNotifications();
      }
    };
    hdrR.appendChild(notifBtn);
    var setBtn = iconBtn("⚙", L("ui.shell.settings"), {
      testId: "settings-toggle",
      onClick: function () {
        toggleSettings();
      },
    });
    hdrR.appendChild(setBtn);
    var wsConnected = state.wsState === "open";
    var ws = el("span", "ws-status " + (wsConnected ? "connected" : "disconnected"));
    ws.title = wsBaseUrl();
    ws.textContent =
      L("ws.prefix") +
      " " +
      (state.wsState === "open"
        ? L("ws.online")
        : state.wsState === "connecting"
          ? L("ws.connecting")
          : state.wsState === "reconnecting"
            ? L("ws.reconnecting")
          : state.wsState === "error"
            ? L("ws.error")
            : L("ws.offline"));
    hdrR.appendChild(ws);
    var lo = iconBtn("🚪", L("common.logout"), {
      testId: "logout",
      onClick: function () {
        logout();
      },
    });
    hdrR.appendChild(lo);
    header.appendChild(hdrR);
    shell.appendChild(header);
    if (state.globalSearch.trim().length >= 2) {
      var gPanel = el("div", "global-search-panel");
      if (state.globalSearchBusy) {
        gPanel.appendChild(el("div", "global-search-hint", L("ui.common.searching")));
      } else if (state.globalSearchHits && state.globalSearchHits.length) {
        state.globalSearchHits.forEach(function (hit) {
          var gb = el("button", "global-search-hit");
          gb.type = "button";
          var chatLbl = chatTitleById(hit.chat_id);
          gb.textContent = chatLbl + ": " + formatPreviewText(hit.type, hit.content);
          gb.onclick = function () {
            openGlobalSearchHit(hit);
          };
          gPanel.appendChild(gb);
        });
      } else if (state.globalSearchHits) {
        gPanel.appendChild(el("div", "global-search-hint", L("ui.common.nothingFound")));
      }
      shell.appendChild(gPanel);
    }
    if (state.incomingRtcCall) {
      var inc = state.incomingRtcCall;
      var incWrap = el("div", "incoming-call-banner");
      incWrap.appendChild(
        el(
          "span",
          "incoming-call-text",
          L("ui.shell.incomingCall", {
            chat: chatTitleById(inc.chatId),
            user: inc.fromUserId.slice(0, 8),
          })
        )
      );
      var incActs = el("div", "incoming-call-actions");
      var bAcc = iconBtn("📞", L("ui.shell.accept"), {
        primary: true,
        disabled: state.busy,
        onClick: function () {
          acceptIncomingRtcCall();
        },
      });
      var bDec = iconBtn("✕", L("ui.shell.decline"), {
        disabled: state.busy,
        onClick: function () {
          declineIncomingRtcCall();
        },
      });
      incActs.appendChild(bAcc);
      incActs.appendChild(bDec);
      incWrap.appendChild(incActs);
      shell.appendChild(incWrap);
    }
    if (state.statusMessage) {
      var okWrap = el("div", "banner-wrap banner-wrap-ok");
      var okBanner = el("div", "info-banner");
      okBanner.textContent = state.statusMessage;
      var okDismiss = el("button", "banner-dismiss", "×");
      okDismiss.type = "button";
      okDismiss.title = L("ui.common.close");
      okDismiss.onclick = function () {
        state.statusMessage = null;
        render();
      };
      okWrap.appendChild(okBanner);
      okWrap.appendChild(okDismiss);
      shell.appendChild(okWrap);
    }
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
    search.placeholder = L("ui.sidebar.searchPlaceholder");
    search.value = state.sidebarSearch;
    search.oninput = function () {
      state.sidebarSearch = search.value;
      scheduleUserSearch();
      render();
    };
    sh.appendChild(search);
    var sideActs = el("div", "sidebar-actions");
    sideActs.appendChild(
      iconBtn("✚", L("ui.sidebar.newGroup"), {
        primary: true,
        block: true,
        disabled: state.busy,
        onClick: function () {
          newGroup();
        },
      })
    );
    sideActs.appendChild(
      iconBtn("🔒", L("ui.sidebar.vaultTitle"), {
        block: true,
        disabled: state.busy,
        onClick: function () {
          openSavedVault();
        },
      })
    );
    sideActs.appendChild(
      iconBtn("✓", L("ui.sidebar.readAllTitle"), {
        block: true,
        disabled: state.busy,
        onClick: function () {
          markAllChatsRead();
        },
      })
    );
    sh.appendChild(sideActs);
    side.appendChild(sh);
    var tabs = el("div", "sidebar-tabs");
    var tabChats = el(
      "button",
      "sidebar-tab" + (state.sidebarMode === "chats" ? " active" : ""),
      L("ui.sidebar.chats")
    );
    tabChats.type = "button";
    tabChats.setAttribute("data-testid", "sidebar-tab-chats");
    tabChats.onclick = function () {
      state.sidebarMode = "chats";
      render();
    };
    var tabContacts = el(
      "button",
      "sidebar-tab" + (state.sidebarMode === "contacts" ? " active" : ""),
      L("ui.sidebar.contacts")
    );
    tabContacts.type = "button";
    tabContacts.setAttribute("data-testid", "sidebar-tab-contacts");
    tabContacts.onclick = function () {
      if (state.sidebarMode === "contacts") return;
      state.sidebarMode = "contacts";
      loadContacts().then(render);
    };
    tabs.appendChild(tabChats);
    tabs.appendChild(tabContacts);
    side.appendChild(tabs);
    var qTrim = state.sidebarSearch.trim();
    if (qTrim.length >= 2) {
      var usBlock = el("div", "user-search-block");
      usBlock.appendChild(el("div", "user-search-label", L("ui.sidebar.users")));
      if (state.userSearchBusy) {
        usBlock.appendChild(el("div", "user-search-hint", L("ui.common.searching")));
      } else if (state.userSearchHits && state.userSearchHits.length) {
        state.userSearchHits.forEach(function (u) {
          var row = el("div", "user-search-item-row");
          var ub = el("button", "user-search-item");
          ub.type = "button";
          ub.disabled = state.busy;
          var label = u.display_name || u.username || u.user_id;
          ub.textContent = label + " · " + (u.username || u.user_id.slice(0, 8));
          ub.onclick = function () {
            openP2pChat(u.user_id);
          };
          row.appendChild(ub);
          var bBlock = el("button", "btn btn-ghost btn-sm user-search-block-btn", "⛔");
          bBlock.type = "button";
          bBlock.title = L("ui.sidebar.blockUser");
          bBlock.disabled = state.busy;
          bBlock.onclick = function (e) {
            e.stopPropagation();
            blockUser(u.user_id);
          };
          row.appendChild(bBlock);
          var bContact = el("button", "btn btn-ghost btn-sm user-search-block-btn", "＋");
          bContact.type = "button";
          bContact.title = L("ui.sidebar.addContact");
          bContact.disabled = state.busy;
          bContact.onclick = function (e) {
            e.stopPropagation();
            addContact(u.user_id);
          };
          row.appendChild(bContact);
          var selForAdd = currentChat();
          if (selForAdd && selForAdd.type === "group" && state.selectedId) {
            var bGrp = el("button", "btn btn-ghost btn-sm user-search-block-btn", "👥");
            bGrp.type = "button";
            bGrp.title = L("ui.sidebar.addToGroup");
            bGrp.disabled = state.busy;
            bGrp.onclick = function (e) {
              e.stopPropagation();
              addMemberToChatById(u.user_id);
            };
            row.appendChild(bGrp);
          }
          usBlock.appendChild(row);
        });
      } else if (state.userSearchHits && !state.userSearchHits.length) {
        usBlock.appendChild(el("div", "user-search-hint", L("ui.sidebar.noUsers")));
      }
      side.appendChild(usBlock);
    }
    if (state.sidebarMode === "contacts") {
      var cList = el("div", "chat-list contacts-list");
      if (state.contactsBusy) {
        cList.appendChild(el("div", "chat-list-empty", L("ui.sidebar.loadingContacts")));
      } else if (state.contacts && state.contacts.length) {
        state.contacts.forEach(function (ct) {
          var row = el("div", "contact-item-row");
          var cb = el("button", "chat-item contact-item-btn");
          cb.type = "button";
          cb.disabled = state.busy;
          var label = ct.display_name || ct.username || ct.id;
          cb.textContent = label + (ct.username ? " · @" + ct.username : "");
          cb.onclick = function () {
            openP2pChat(ct.id);
          };
          row.appendChild(cb);
          var bRm = el("button", "btn btn-ghost btn-sm user-search-block-btn", "✕");
          bRm.type = "button";
          bRm.title = L("ui.sidebar.removeContact");
          bRm.disabled = state.busy;
          bRm.onclick = function (e) {
            e.stopPropagation();
            removeContact(ct.id);
          };
          row.appendChild(bRm);
          cList.appendChild(row);
        });
      } else {
        cList.appendChild(
          el("div", "chat-list-empty", L("ui.sidebar.noContacts"))
        );
      }
      side.appendChild(cList);
      var impBlock = el("div", "contact-import-block");
      var impLabel = el("div", "user-search-label");
      impLabel.textContent = L("ui.sidebar.importLabel");
      impBlock.appendChild(impLabel);
      var impTa = document.createElement("textarea");
      impTa.className = "contact-import-textarea";
      impTa.rows = 3;
      impTa.placeholder = L("ui.sidebar.importPlaceholder");
      impTa.value = state.contactImportText || "";
      impTa.disabled = state.busy;
      impTa.oninput = function () {
        state.contactImportText = impTa.value;
      };
      impBlock.appendChild(impTa);
      var bImp = iconBtn("📥", L("ui.sidebar.importBtn"), {
        block: true,
        disabled: state.busy,
        onClick: function () {
          importContactsByPhoneHashes();
        },
      });
      impBlock.appendChild(bImp);
      side.appendChild(impBlock);
    } else {
    var list = el("div", "chat-list");
    list.addEventListener("scroll", function () {
      if (list.scrollTop + list.clientHeight >= list.scrollHeight - 48) {
        scheduleChatPreviewHydrateMore();
      }
    });
    var fc = filteredChats();
    if (fc.length === 0) {
      list.appendChild(el("div", "chat-list-empty", state.chats.length ? L("ui.sidebar.noChatsFilter") : L("ui.sidebar.noChats")));
    }
    fc.forEach(function (c) {
      var b = el("button", "chat-item" + (c.id === state.selectedId ? " active" : ""));
      b.type = "button";
      b.onclick = function () {
        openChatById(c.id);
      };
      var row = el("div", "chat-item-row");
      var av = el("div", "chat-avatar", chatInitial(c.title || c.id));
      row.appendChild(av);
      var txt = el("div", "chat-item-text");
      var unread = state.unreadByChat[c.id] || 0;
      var titleRow = el("div", "chat-item-title-row");
      titleRow.appendChild(
        el(
          "div",
          "chat-item-title",
          (c.title || c.id.slice(0, 8)) +
            (c.muted ? " 🔕" : "") +
            (c.personal_filter_active ? " 🔍" : "")
        )
      );
      var listTimeMs = chatListTimeMs(c);
      if (listTimeMs) {
        var timeCls = "chat-item-time";
        if (unread > 0 && c.id !== state.selectedId) timeCls += " chat-item-time-unread";
        titleRow.appendChild(el("span", timeCls, formatChatListTime(listTimeMs)));
      }
      txt.appendChild(titleRow);
      var prev = state.chatPreview[c.id];
      var typingSide = getTypingLabel(c.id);
      var liveConf = activeConferenceInChat(c.id);
      if (liveConf && c.id !== state.selectedId) {
        txt.appendChild(
          el("div", "chat-preview chat-preview-conference", conferencePreviewLabel(liveConf))
        );
      } else if (typingSide && c.id !== state.selectedId) {
        txt.appendChild(el("div", "chat-preview chat-preview-typing", typingSide));
      } else if (prev && prev.text) {
        var prevCls = "chat-preview";
        if (unread > 0 && c.id !== state.selectedId) prevCls += " chat-preview-unread";
        txt.appendChild(el("div", prevCls, prev.text));
      } else {
        var draftPrev = composerDraftPreview(c.id);
        if (draftPrev && c.id !== state.selectedId) {
          txt.appendChild(
            el("div", "chat-preview chat-preview-draft", L("ui.sidebar.draft", { text: draftPrev }))
          );
        } else {
          txt.appendChild(el("div", "chat-meta", L("ui.sidebar.membersMeta", { type: c.type, count: c.member_count })));
        }
      }
      row.appendChild(txt);
      if (unread > 0 && c.id !== state.selectedId) {
        var badge = el(
          "span",
          "chat-unread-badge",
          unread > 99 ? "99+" : String(unread)
        );
        row.appendChild(badge);
      }
      b.appendChild(row);
      list.appendChild(b);
    });
    side.appendChild(list);
    }
    main.appendChild(side);
    var thread = el("section", "thread");
    if (!state.selectedId) {
      thread.appendChild(el("div", "empty-thread", L("ui.thread.empty")));
    } else {
      var sel = state.chats.find(function (x) {
        return x.id === state.selectedId;
      });
      var th = el("div", "thread-header");
      var thMain = el("div", "thread-header-main");
      thMain.appendChild(el("div", "thread-title", (sel && sel.title) || state.selectedId));
      var chatTtlSeconds = messageVisibilityTtlSeconds(sel);
      if (chatTtlSeconds) {
        thMain.appendChild(
          el(
            "div",
            "thread-subtitle",
            L("ui.thread.messageTtl", { ttl: formatTtlLabel(chatTtlSeconds) })
          )
        );
      }
      if (
        state.exportBusy &&
        state.exportJobChatId === state.selectedId &&
        state.exportJobId
      ) {
        thMain.appendChild(el("div", "thread-subtitle thread-export-hint", L("common.exportHint")));
      }
      var typingHdr = getTypingLabel(state.selectedId);
      if (typingHdr) {
        thMain.appendChild(el("div", "thread-typing", typingHdr));
      }
      th.appendChild(thMain);
      var thActs = el("div", "thread-header-actions");
      if (sel && sel.type !== "saved") {
        var bMute = iconBtn(sel.muted ? "🔊" : "🔇", sel.muted ? L("ui.thread.unmute") : L("ui.thread.mute"), {
          disabled: state.busy,
          onClick: function () {
            toggleChatMute();
          },
        });
        thActs.appendChild(bMute);
        var bFilter = iconBtn(
          sel.personal_filter_active ? "🔎" : "🔍",
          L("ui.thread.filterTitle"),
          {
            disabled: state.busy,
            onClick: function () {
              togglePersonalFilter();
            },
          }
        );
        thActs.appendChild(bFilter);
        if (sel.type === "group") {
          thActs.appendChild(
            iconBtn("👥", L("ui.common.members"), {
              disabled: state.busy,
              onClick: function () {
                openMembersModal();
              },
            })
          );
        }
        var exportThisChat =
          state.exportBusy && state.exportJobChatId === state.selectedId;
        var exportTip = exportThisChat
          ? L("ui.thread.exportCancel")
          : state.exportBusy
            ? L("common.exportBusy")
            : L("ui.thread.export");
        var bExp = iconBtn(exportThisChat ? "⏹" : "📤", exportTip, {
          testId: "chat-export-button",
          disabled: state.busy || (state.exportBusy && !exportThisChat),
          onClick: function () {
            if (exportThisChat) {
              cancelChatExport();
            } else {
              startChatExport();
            }
          },
        });
        thActs.appendChild(bExp);
        thActs.appendChild(
          iconBtn("↻", L("ui.thread.refresh"), {
            disabled: state.busy,
            onClick: function () {
              refreshCurrentThread();
            },
          })
        );
        thActs.appendChild(
          iconBtn("🔗", L("ui.thread.copyChatLink"), {
            disabled: state.busy,
            onClick: function () {
              copyChatDeepLink();
            },
          })
        );
      }
      th.appendChild(thActs);
      thread.appendChild(th);
      var threadLiveConf = activeConferenceInChat(state.selectedId);
      if (threadLiveConf && state.selectedId !== state.savedChatId) {
        var confBanner = el("div", "conference-live-banner");
        var confBannerText = el(
          "span",
          "conference-live-banner-text",
          "🎥 " +
            (threadLiveConf.title || L("ui.thread.confLiveDefault")) +
            conferenceParticipantsLabel(threadLiveConf.participant_count)
        );
        confBanner.appendChild(confBannerText);
        if (
          state.conferenceParticipantsConfId === threadLiveConf.conference_id &&
          state.conferenceParticipantsList &&
          state.conferenceParticipantsList.length
        ) {
          var namesLine = conferenceParticipantNamesSummary(state.conferenceParticipantsList, 5);
          if (namesLine) {
            confBanner.appendChild(
              el("span", "conference-live-banner-names", namesLine)
            );
          }
        } else if (threadLiveConf.conference_id) {
          loadConferenceParticipants(threadLiveConf.conference_id)
            .then(render)
            .catch(function () {});
        }
        confBanner.appendChild(
          iconBtn("▶", L("ui.common.join"), {
            primary: true,
            disabled: state.conferenceBusy || state.busy,
            onClick: function () {
              joinConferenceFromBanner(threadLiveConf);
            },
          })
        );
        thread.appendChild(confBanner);
      }
      var tSearchRow = el("div", "thread-search-row");
      var tSearch = el("input");
      tSearch.type = "search";
      tSearch.className = "thread-search-input";
      tSearch.placeholder = L("ui.thread.searchPlaceholder");
      tSearch.value = state.threadSearch;
      tSearch.oninput = function () {
        state.threadSearch = tSearch.value;
        scheduleThreadSearch();
        render();
      };
      tSearchRow.appendChild(tSearch);
      thread.appendChild(tSearchRow);
      if (state.threadSearchBusy) {
        thread.appendChild(el("div", "thread-search-hint", L("ui.common.searching")));
      } else if (state.threadSearchHits && state.threadSearch.trim().length >= 2) {
        var hitsBox = el("div", "thread-search-hits");
        if (!state.threadSearchHits.length) {
          hitsBox.appendChild(el("div", "thread-search-hint", L("ui.thread.nothingInChat")));
        } else {
          state.threadSearchHits.forEach(function (hit) {
            var hb = el("button", "thread-search-hit");
            hb.type = "button";
            hb.textContent = formatPreviewText(hit.type, hit.content);
            hb.onclick = function () {
              openSearchHit(hit);
            };
            hitsBox.appendChild(hb);
          });
        }
        thread.appendChild(hitsBox);
      }
      if (state.pinnedMessages.length) {
        var pinsBar = el("div", "thread-pins");
        state.pinnedMessages.forEach(function (p) {
          var pinBtn = el("button", "thread-pin-item");
          pinBtn.type = "button";
          pinBtn.textContent = "📌 " + replySnippetForId(p.message_id);
          pinBtn.onclick = function () {
            scrollToMessageId(p.message_id).catch(function (e) {
              state.error = (e && e.message) || L("messages.notFound");
              render();
            });
          };
          pinsBar.appendChild(pinBtn);
        });
        thread.appendChild(pinsBar);
      }
      var msgs = el("div", "messages");
      if (state.threadHasMore) {
        var loadOlder = iconBtn("↑", state.threadLoadingMore ? "…" : L("ui.thread.loadOlder"), {
          cls: "messages-load-more",
          disabled: state.threadLoadingMore,
          onClick: function () {
            loadOlderMessages();
          },
        });
        msgs.appendChild(loadOlder);
      }
      msgs.onscroll = function () {
        if (msgs.scrollTop < 64 && state.threadHasMore && !state.threadLoadingMore) {
          loadOlderMessages();
        }
      };
      var myId = jwtSub(state.tokens.access_token);
      state.messages.forEach(function (m) {
        var art = el(
          "article",
          "msg" +
            (myId && m.sender_id === myId ? " own" : "") +
            (isMessagePinned(m.id) ? " pinned" : "") +
            (m.deleted ? " deleted" : "")
        );
        art.id = "msg-" + m.id;
        var meta = el("div", "msg-meta");
        meta.appendChild(document.createTextNode(myId && m.sender_id === myId ? L("ui.thread.you") : m.sender_id.slice(0, 8)));
        var ts = el("span");
        ts.className = "msg-ts";
        ts.textContent = new Date(m.created_at).toLocaleString();
        meta.appendChild(ts);
        if (m.edited_at) {
          var ed = el("button", "msg-edited");
          ed.type = "button";
          ed.textContent = L("ui.message.editedShort");
          ed.title = L("ui.message.editHistoryTitle");
          ed.onclick = function () {
            openMessageVersions(m);
          };
          meta.appendChild(ed);
        }
        if (myId && m.sender_id === myId) {
          var rr = state.readReceiptsByMessage[m.id];
          var rrCount = rr ? Object.keys(rr).length : 0;
          if (rrCount > 0) {
            var rrEl = el("span", "msg-read-receipt-double-check", " ✓✓");
            rrEl.title = L("readReceipts.title") + ": " + rrCount;
            rrEl.style.cursor = "pointer";
            rrEl.onclick = function (ev) {
              ev.stopPropagation();
              showReadReceiptPopup(m.id);
            };
            meta.appendChild(rrEl);
          }
        }
        var ttlSeconds = messageVisibilityTtlSeconds(m);
        var expiresAt = messageExpiryEpochMs(m);
        var isExpired = expiresAt != null && Date.now() >= expiresAt;
        if (ttlSeconds) {
          var ttlLbl = el("span");
          ttlLbl.className = "msg-ttl msg-ttl-indicator" + (isExpired ? " msg-ttl-expired" : "");
          if (isExpired) {
            ttlLbl.textContent = L("ui.message.ttlExpiredLabel");
            ttlLbl.title = L("ui.message.ttlExpiredTitle");
          } else {
            var leftSeconds = Math.max(1, Math.ceil((expiresAt - Date.now()) / 1000));
            ttlLbl.textContent = " · ⏱ " + formatTimeLeft(leftSeconds);
            ttlLbl.title = L("ui.message.ttlExpiresIn", { time: formatTimeLeft(leftSeconds) });
          }
          meta.appendChild(ttlLbl);
        }
        art.appendChild(meta);
        if (m.reply_to_msg_id) {
          var rq = el("button", "msg-reply-quote");
          rq.type = "button";
          rq.textContent = "↩ " + replySnippetForId(m.reply_to_msg_id);
          rq.onclick = function () {
            scrollToMessageId(m.reply_to_msg_id).catch(function () {});
          };
          art.appendChild(rq);
        }
        if (!m.deleted && (m.type !== "text" || isE2eeType(m.type))) {
          var typeLbl = isE2eeType(m.type)
            ? "e2ee · " + e2eePlainType(m.type)
            : m.type;
          art.appendChild(el("div", "msg-type" + (isE2eeType(m.type) ? " msg-type-e2ee" : ""), typeLbl));
        }
        var body = el("div", "msg-body md");
        if (m.deleted || isExpired) {
          body.className = "msg-body msg-deleted-body";
          body.textContent = isExpired ? L("ui.message.unavailableTtl") : L("ui.message.deleted");
        } else {
          renderMessageContent(body, m);
        }
        art.appendChild(body);
        var agg = aggregateReactions(m.id, myId);
        var emojis = Object.keys(agg);
        if (emojis.length) {
          var reactBar = el("div", "msg-reactions");
          emojis.forEach(function (em) {
            var chip = el(
              "button",
              "msg-reaction-chip" + (agg[em].mine ? " mine" : ""),
              em + " " + agg[em].count
            );
            chip.type = "button";
            chip.onclick = function () {
              toggleReaction(m.id, em).catch(function (err) {
                state.error = err.message || L("messages.reactionFailed");
                render();
              });
            };
            reactBar.appendChild(chip);
          });
          art.appendChild(reactBar);
        }
        if (!m.deleted) {
          var actions = el("div", "msg-actions");
          actions.appendChild(
            iconBtn("↩", L("ui.actions.reply"), {
              testId: "message-reply-button",
              onClick: function () {
                setReplyTo(m);
              },
            })
          );
          if (m.type === "text" || isE2eeType(m.type) || (m.content && m.content.trim())) {
            actions.appendChild(
              iconBtn("📋", L("ui.actions.copy"), {
                onClick: function () {
                  copyMessageText(m);
                },
              })
            );
          }
          actions.appendChild(
            iconBtn("🔗", L("ui.message.messageLinkTitle"), {
              onClick: function () {
                copyMessageDeepLink(m);
              },
            })
          );
          var attachId = messageAttachmentFileId(m);
          if (attachId) {
            actions.appendChild(
              iconBtn("⬇", L("ui.common.download"), {
                onClick: function () {
                  downloadChatFile(attachId).catch(function (err) {
                    state.error = err.message || L("files.downloadFailedShort");
                    render();
                  });
                },
              })
            );
          }
          actions.appendChild(
            iconBtn(
              isMessagePinned(m.id) ? "📍" : "📌",
              isMessagePinned(m.id) ? L("ui.message.unpin") : L("ui.message.pin"),
              {
                onClick: function () {
                  togglePinMessage(m).catch(function (err) {
                    state.error = err.message || L("messages.pinFailed");
                    render();
                  });
                },
              }
            )
          );
          actions.appendChild(
            iconBtn("↪", L("ui.actions.forward"), {
              onClick: function () {
                openForwardPicker(m);
              },
            })
          );
          if (myId && m.sender_id === myId && messageAttachmentFileId(m)) {
            var fileId = messageAttachmentFileId(m);
            actions.appendChild(
              iconBtn("🌐", L("ui.message.pubLinkTitle"), {
                onClick: function () {
                  createPublicLinkForFile(fileId);
                },
              })
            );
            actions.appendChild(
              iconBtn("🔗", L("ui.message.linksTitle"), {
                onClick: function () {
                  openFilePublicLinksModal(fileId);
                },
              })
            );
            actions.appendChild(
              iconBtn("🗑", L("ui.actions.deleteFile"), {
                onClick: function () {
                  deleteOwnFile(fileId);
                },
              })
            );
          }
          if (state.savedChatId && state.selectedId !== state.savedChatId) {
            actions.appendChild(
              iconBtn("🔒", L("ui.actions.toVault"), {
                onClick: function () {
                  saveMessageToVault(m).catch(function (err) {
                    state.error = err.message || L("saved.saveFailed");
                    render();
                  });
                },
              })
            );
          }
          if (myId && m.sender_id === myId && m.type === "text") {
            actions.appendChild(
              iconBtn("✎", L("ui.actions.edit"), {
                onClick: function () {
                  editMessagePrompt(m).catch(function (err) {
                    state.error = err.message || L("messages.editFailed");
                    render();
                  });
                },
              })
            );
            actions.appendChild(
              iconBtn("🗑", L("ui.actions.delete"), {
                onClick: function () {
                  deleteMessageConfirm(m).catch(function (err) {
                    state.error = err.message || L("messages.deleteFailed");
                    render();
                  });
                },
              })
            );
          }
          QUICK_REACTIONS.forEach(function (em) {
            var br = el("button", "btn btn-ghost btn-sm msg-react-btn", em);
            br.type = "button";
            br.title = L("ui.message.reactionTitle", { emoji: em });
            br.onclick = function () {
              toggleReaction(m.id, em).catch(function (err) {
                state.error = err.message || L("messages.reactionAddFailed");
                render();
              });
            };
            actions.appendChild(br);
          });
          art.appendChild(actions);
        }
        msgs.appendChild(art);
      });
      thread.appendChild(msgs);
      var comp = el("form", "composer");
      comp.onsubmit = function (e) {
        e.preventDefault();
        sendMessage();
      };
      if (state.replyTo) {
        var rbar = el("div", "composer-reply-bar");
        rbar.appendChild(
          el("span", "composer-reply-text", L("ui.thread.replyPrefix", { text: state.replyTo.snippet }))
        );
        var rCancel = el("button", "btn btn-ghost btn-sm", "✕");
        rCancel.type = "button";
        rCancel.title = L("ui.thread.cancelReply");
        rCancel.onclick = function () {
          clearReplyTo();
          render();
        };
        rbar.appendChild(rCancel);
        comp.appendChild(rbar);
      }
      var fmt = el("div", "composer-format");
      var bBold = el("button", "btn btn-ghost btn-icon", "B");
      bBold.type = "button";
      bBold.title = L("ui.thread.bold");
      bBold.onclick = function () {
        wrapComposerSelection("**", "**");
      };
      var bIt = el("button", "btn btn-ghost btn-icon", "I");
      bIt.type = "button";
      bIt.title = L("ui.thread.italic");
      bIt.onclick = function () {
        wrapComposerSelection("*", "*");
      };
      var bCode = el("button", "btn btn-ghost btn-icon", "</>");
      bCode.type = "button";
      bCode.title = L("ui.thread.code");
      bCode.onclick = function () {
        wrapComposerSelection("`", "`");
      };
      fmt.appendChild(bBold);
      fmt.appendChild(bIt);
      fmt.appendChild(bCode);
      var filePick = document.createElement("input");
      filePick.type = "file";
      filePick.id = "msgFilePick";
      filePick.setAttribute("data-testid", "file-attach-input");
      filePick.style.display = "none";
      filePick.accept = "image/*,video/*,*/*";
      var maxHint =
        state.mediaCaps && state.mediaCaps.max_upload_bytes
          ? " до " + Math.round(state.mediaCaps.max_upload_bytes / (1024 * 1024)) + " МБ"
          : "";
      var bFile = iconBtn("📎", maxHint
        ? L("ui.thread.attachFileMax", {
            mb: Math.round(state.mediaCaps.max_upload_bytes / (1024 * 1024)),
          })
        : L("ui.thread.attachFile"), {
        testId: "file-attach",
        disabled: state.busy,
        onClick: function () {
          filePick.click();
        },
      });
      filePick.onchange = function () {
        if (filePick.files && filePick.files[0]) {
          sendFileMessage(filePick.files[0]);
        }
        filePick.value = "";
      };
      fmt.appendChild(bFile);
      fmt.appendChild(filePick);
      fmt.appendChild(el("span", "composer-md-hint", L("ui.thread.markdownHint")));
      comp.appendChild(fmt);
      var ttlRow = el("div", "composer-ttl-row");
      ttlRow.appendChild(el("label", "composer-ttl-label", L("ui.thread.autoDelete")));
      var ttlSel = document.createElement("select");
      ttlSel.id = "composerTtl";
      ttlSel.className = "composer-ttl-select";
      [
        { v: "", l: L("ui.thread.ttlNone") },
        { v: "60", l: L("ui.thread.ttl1min") },
        { v: "3600", l: L("ui.thread.ttl1hour") },
        { v: "86400", l: L("ui.thread.ttl24h") },
      ].forEach(function (opt) {
        var o = document.createElement("option");
        o.value = opt.v;
        o.textContent = opt.l;
        if (state.composerTtl === opt.v) o.selected = true;
        ttlSel.appendChild(o);
      });
      ttlSel.onchange = function () {
        state.composerTtl = ttlSel.value;
      };
      ttlRow.appendChild(ttlSel);
      comp.appendChild(ttlRow);
      var ta = el("textarea");
      ta.id = "msgdraft";
      ta.setAttribute("data-testid", "message-composer");
      ta.rows = 3;
      ta.placeholder = L("ui.thread.composerPlaceholder");
      ta.value = loadComposerDraftForChat(state.selectedId);
      ta.oninput = function () {
        scheduleSaveComposerDraft();
        scheduleTypingNotify();
      };
      ta.onkeydown = function (e) {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          sendMessage();
        }
      };
      comp.appendChild(ta);
      var sb = iconBtn("➤", L("ui.thread.send"), {
        primary: true,
        cls: "composer-send-btn",
        submit: true,
        disabled: state.busy,
      });
      comp.appendChild(sb);
      bindComposerDrop(comp);
      thread.appendChild(comp);
    }
    main.appendChild(thread);
    shell.appendChild(main);
    renderCallPanel(shell);
    if (state.settingsOpen) {
      renderSettingsModal(shell);
    }
    if (state.forwardPick) {
      var fOv = el("div", "forward-overlay");
      var fCard = el("div", "forward-card");
      fCard.appendChild(el("h2", "forward-title", L("ui.forward.title")));
      fCard.appendChild(el("p", "forward-snippet", state.forwardPick.snippet));
      var fList = el("div", "forward-chat-list");
      state.chats
        .filter(function (c) {
          return c.id !== state.selectedId;
        })
        .forEach(function (c) {
          var fb = el("button", "forward-chat-item");
          fb.type = "button";
          fb.disabled = state.busy;
          fb.textContent =
            (c.title || c.type) +
            " · " +
            L("ui.sidebar.membersMeta", { type: c.type, count: c.member_count });
          fb.onclick = function () {
            forwardMessageTo(c.id);
          };
          fList.appendChild(fb);
        });
      fCard.appendChild(fList);
      var fCancel = iconBtn("✕", L("ui.common.cancel"), {
        onClick: function () {
          closeForwardPicker();
        },
      });
      fCard.appendChild(fCancel);
      fOv.appendChild(fCard);
      fOv.onclick = function (e) {
        if (e.target === fOv) closeForwardPicker();
      };
      shell.appendChild(fOv);
    }
    if (state.membersModalOpen) {
      var mOv = el("div", "settings-overlay");
      var mCard = el("div", "settings-card members-card");
      var selChat = currentChat();
      var mTitle = el("h2", "settings-title");
      mTitle.textContent =
        selChat && selChat.type === "group"
          ? L("ui.members.groupTitle", { name: selChat.title || selChat.id.slice(0, 8) })
          : L("ui.members.chatTitle");
      mCard.appendChild(mTitle);
      var mBody = el("div", "settings-body members-body");
      if (state.chatMembersBusy) {
        mBody.appendChild(el("p", "settings-hint", L("ui.common.loading")));
      } else if (state.chatMembers && state.chatMembers.length) {
        var meId = state.tokens ? jwtSub(state.tokens.access_token) : null;
        var myRole = myChatRole(state.chatMembers);
        var manageBans = canManageChatBans(myRole);
        var manageMembers = canManageMembers(myRole);
        if (selChat && selChat.type === "group" && manageMembers) {
          var mTools = el("div", "member-tools");
          mTools.appendChild(
            iconBtn("✎", L("ui.members.rename"), {
              disabled: state.busy,
              onClick: function () {
                renameGroupChat();
              },
            })
          );
          mTools.appendChild(
            iconBtn("＋", L("ui.members.addMember"), {
              disabled: state.busy,
              onClick: function () {
                addMemberToChat();
              },
            })
          );
          mBody.appendChild(mTools);
        }
        state.chatMembers.forEach(function (m) {
          var mRow = el("div", "member-row");
          var label =
            (m.display_name || m.username || m.user_id) +
            " · " +
            m.role +
            (m.banned ? L("ui.members.banned") : "");
          mRow.appendChild(el("span", "member-row-label", label));
          if (myRole === "owner" && m.user_id !== meId && m.role !== "owner" && !m.banned) {
            var roleSel = document.createElement("select");
            roleSel.className = "member-role-select";
            ["member", "admin"].forEach(function (r) {
              var opt = document.createElement("option");
              opt.value = r;
              opt.textContent = r;
              if (m.role === r) opt.selected = true;
              roleSel.appendChild(opt);
            });
            roleSel.disabled = state.busy;
            roleSel.onchange = function () {
              setMemberRole(m.user_id, roleSel.value);
            };
            mRow.appendChild(roleSel);
          }
          if (m.user_id === meId && selChat && selChat.type === "group") {
            mRow.appendChild(
              iconBtn("🚪", L("ui.members.leave"), {
                disabled: state.busy,
                onClick: function () {
                  removeMemberFromChat(m.user_id);
                },
              })
            );
          } else if (
            manageMembers &&
            m.user_id !== meId &&
            m.role !== "owner" &&
            !m.banned
          ) {
            mRow.appendChild(
              iconBtn("✕", L("ui.members.remove"), {
                disabled: state.busy,
                onClick: function () {
                  removeMemberFromChat(m.user_id);
                },
              })
            );
          }
          if (manageBans && m.user_id !== meId && m.role !== "owner") {
            if (m.banned) {
              mRow.appendChild(
                iconBtn("✓", L("ui.members.unban"), {
                  disabled: state.busy,
                  onClick: function () {
                    unbanUserInChat(m.user_id);
                  },
                })
              );
            } else if (m.role !== "admin" || myRole === "owner") {
              mRow.appendChild(
                iconBtn("⛔", L("ui.members.ban"), {
                  disabled: state.busy,
                  onClick: function () {
                    banUserInChat(m.user_id);
                  },
                })
              );
            }
          }
          mBody.appendChild(mRow);
        });
      } else {
        mBody.appendChild(el("p", "settings-hint", L("ui.members.noMembers")));
      }
      mCard.appendChild(mBody);
      var mClose = iconBtn("✕", L("ui.common.close"), {
        primary: true,
        onClick: function () {
          closeMembersModal();
        },
      });
      mCard.appendChild(mClose);
      mOv.appendChild(mCard);
      mOv.onclick = function (e) {
        if (e.target === mOv) closeMembersModal();
      };
      shell.appendChild(mOv);
    }
    if (state.fileLinksOpen) {
      var flOv = el("div", "settings-overlay");
      var flCard = el("div", "settings-card members-card");
      flCard.appendChild(el("h2", "settings-title", L("ui.fileLinks.title")));
      var flBody = el("div", "settings-body members-body");
      if (state.fileLinksBusy) {
        flBody.appendChild(el("p", "settings-hint", L("ui.common.loading")));
      } else if (state.fileLinksRows && state.fileLinksRows.length) {
        state.fileLinksRows.forEach(function (row) {
          var flRow = el("div", "file-link-row");
          var head = el("div", "file-link-row-head");
          head.textContent = L("ui.fileLinks.row", {
            kind: row.link_kind || "?",
            expires: formatInstantLabel(row.expires_at),
            created: formatInstantLabel(row.created_at),
          });
          flRow.appendChild(head);
          var flActs = el("div", "file-link-row-actions");
          flActs.appendChild(
            iconBtn("🚫", L("ui.common.revoke"), {
              disabled: state.busy,
              onClick: function () {
                revokeFilePublicLink(state.fileLinksFileId, row.id);
              },
            })
          );
          flRow.appendChild(flActs);
          flBody.appendChild(flRow);
        });
      } else {
        flBody.appendChild(el("p", "settings-hint", L("ui.fileLinks.none")));
      }
      flCard.appendChild(flBody);
      var flFoot = el("div", "modal-footer");
      flFoot.appendChild(
        iconBtn("＋", L("ui.fileLinks.create"), {
          disabled: state.busy || !state.fileLinksFileId,
          onClick: function () {
            createPublicLinkForFile(state.fileLinksFileId);
          },
        })
      );
      flFoot.appendChild(
        iconBtn("✕", L("ui.common.close"), {
          primary: true,
          onClick: function () {
            closeFilePublicLinksModal();
          },
        })
      );
      flCard.appendChild(flFoot);
      flOv.appendChild(flCard);
      flOv.onclick = function (e) {
        if (e.target === flOv) closeFilePublicLinksModal();
      };
      shell.appendChild(flOv);
    }
    if (state.messageVersionsOpen) {
      var vOv = el("div", "settings-overlay");
      var vCard = el("div", "settings-card members-card");
      vCard.appendChild(el("h2", "settings-title", L("ui.versions.title")));
      var vBody = el("div", "settings-body members-body");
      if (state.messageVersionsBusy) {
        vBody.appendChild(el("p", "settings-hint", L("ui.common.loading")));
      } else if (state.messageVersions && state.messageVersions.length) {
        state.messageVersions.forEach(function (ver, idx) {
          var vRow = el("div", "version-row");
          var head = el("div", "version-row-head");
          head.textContent =
            "#" +
            (idx + 1) +
            " · " +
            new Date(ver.created_at).toLocaleString() +
            (ver.edited_by ? " · " + ver.edited_by.slice(0, 8) : "");
          vRow.appendChild(head);
          var body = el("pre", "version-row-body", ver.content || "");
          vRow.appendChild(body);
          vBody.appendChild(vRow);
        });
      } else {
        vBody.appendChild(el("p", "settings-hint", L("ui.versions.none")));
      }
      vCard.appendChild(vBody);
      var vClose = iconBtn("✕", L("ui.common.close"), {
        primary: true,
        onClick: function () {
          closeMessageVersionsModal();
        },
      });
      vCard.appendChild(vClose);
      vOv.appendChild(vCard);
      vOv.onclick = function (e) {
        if (e.target === vOv) closeMessageVersionsModal();
      };
      shell.appendChild(vOv);
    }
    root.appendChild(shell);
  }

  function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    requestAnimationFrame(function () {
      renderScheduled = false;
      render();
    });
  }

  function render() {
    if (!state.tokens) {
      renderAuth();
    } else {
      renderMain();
      if (state.shouldScrollThread) {
        state.shouldScrollThread = false;
        scheduleScrollMessages();
      }
    }
  }

  function stripDeepLinkFromUrl() {
    try {
      var params = new URLSearchParams(window.location.search);
      var chatId = params.has("chat") ? params.get("chat") : null;
      var msgId = params.has("msg") ? params.get("msg") : null;
      var meet = params.has("meet") ? params.get("meet") : null;
      var conf = params.has("conf") ? params.get("conf") : null;
      var changed = params.has("chat") || params.has("msg") || params.has("meet") || params.has("conf");
      if (params.has("chat")) params.delete("chat");
      if (params.has("msg")) params.delete("msg");
      if (params.has("meet")) params.delete("meet");
      if (params.has("conf")) params.delete("conf");
      if (changed && window.history && window.history.replaceState) {
        var q = params.toString();
        var path = window.location.pathname + (q ? "?" + q : "");
        window.history.replaceState(null, "", path);
      }
      return { chatId: chatId, msgId: msgId, meet: meet, conf: conf };
    } catch (e) {
      return { chatId: null, msgId: null, meet: null, conf: null };
    }
  }

  function stashPendingDeepLink(chatId, msgId) {
    uiShellUtils.stashPendingDeepLink(PENDING_CHAT_KEY, PENDING_MSG_KEY, chatId, msgId);
  }

  function consumePendingDeepLink() {
    var fromUrl = stripDeepLinkFromUrl();
    if (fromUrl.chatId || fromUrl.msgId) {
      stashPendingDeepLink(fromUrl.chatId, fromUrl.msgId);
    }
    var pending = uiShellUtils.readAndClearPendingDeepLink(
      PENDING_CHAT_KEY,
      PENDING_MSG_KEY
    );
    return {
      chatId: pending.chatId || fromUrl.chatId,
      msgId: pending.msgId || fromUrl.msgId,
    };
  }

  function openChatFromUrlParam() {
    var pending = consumePendingDeepLink();
    if (pending.chatId) state.selectedId = pending.chatId;
    return pending.msgId || null;
  }

  function stashPendingMeetingDeepLink(fromUrl) {
    if (fromUrl && fromUrl.meet) {
      try {
        sessionStorage.setItem(PENDING_MEET_KEY, fromUrl.meet);
      } catch (e) {}
    }
    if (fromUrl && fromUrl.conf) {
      try {
        sessionStorage.setItem(PENDING_CONF_KEY, fromUrl.conf);
      } catch (e) {}
    }
  }

  async function consumePendingMeetingDeepLink() {
    var fromUrl = stripDeepLinkFromUrl();
    stashPendingMeetingDeepLink(fromUrl);
    var meet = null;
    var confId = null;
    try {
      meet = sessionStorage.getItem(PENDING_MEET_KEY);
      confId = sessionStorage.getItem(PENDING_CONF_KEY);
      sessionStorage.removeItem(PENDING_MEET_KEY);
      sessionStorage.removeItem(PENDING_CONF_KEY);
    } catch (e) {}
    if (!meet && !confId) return;
    ensureCallPanelOpen();
    if (confId) {
      try {
        var byId = await apiJson("/conferences/" + confId, { method: "GET" });
        await joinJitsiConference(byId);
        return;
      } catch (e) {}
    }
    if (meet) {
      try {
        var byRoom = await apiJson(
          "/conferences/by-room/" + encodeURIComponent(meet),
          { method: "GET" }
        );
        await joinJitsiConference(byRoom);
        return;
      } catch (e) {}
      await joinJitsiConference({
        join_url: buildGuestJitsiUrl(meet),
        title: L("conference.guestMeeting"),
        status: "active",
        room_slug: meet,
      });
    }
  }

  async function initAfterLogin() {
    try {
      state.lastPublicLink = loadLastPublicLink();
      var pendingMsgId = openChatFromUrlParam();
      await loadMyProfile({ applyLocale: true });
      await loadMediaCaps();
      await loadServerVersion();
      await loadE2eeStatus();
      await loadLocalKeyPackageMeta();
      await loadSavedChatId();
      if (notificationsAllowed()) {
        await registerWebPush();
      }
      await refreshChats();
      if (state.selectedId) {
        await openChatById(state.selectedId, { forceReload: true });
        if (pendingMsgId) {
          try {
            await scrollToMessageId(pendingMsgId);
          } catch (e) {
            state.error = e.message || L("messages.deepLinkNotFound");
          }
        }
      }
      await consumePendingMeetingDeepLink();
    } catch (err) {
      state.error = err.message;
    }
    openWs();
    startHeartbeat();
    render();
  }

  function boot() {
    i18n.init().then(function () {
      bootAfterI18n();
    }).catch(function (err) {
      console.error("i18n init failed", err);
      bootAfterI18n();
    });
  }

  function bootAfterI18n() {
    applyStyleSet(loadStyleSet());
    syncNotifyPref();
    startTtlRenderTicker();
    state.networkOnline =
      typeof navigator.onLine === "boolean" ? navigator.onLine : true;
    setupConnectivityHandlers();
    setupEscapeHandler();
    setupKeyboardShortcuts();
    setupServiceWorkerMessages();
    setupPwaInstallCapture();
    registerServiceWorker();
    state.tokens = loadTokens();
    if (state.tokens) {
      ensureSessionFresh()
        .then(function (ok) {
          if (!ok) {
            clearTokens();
            state.error = L("errors.sessionExpired");
            updateDocumentTitle();
            render();
            return;
          }
          return initAfterLogin();
        })
        .catch(function () {
          clearTokens();
          state.error = L("errors.sessionExpired");
          render();
        });
    } else {
      var pendingUrl = stripDeepLinkFromUrl();
      if (pendingUrl.chatId || pendingUrl.msgId) {
        stashPendingDeepLink(pendingUrl.chatId, pendingUrl.msgId);
      }
      stashPendingMeetingDeepLink(pendingUrl);
      updateDocumentTitle();
      render();
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
