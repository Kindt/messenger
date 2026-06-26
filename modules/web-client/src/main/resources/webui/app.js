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
  const PENDING_GUEST_KEY = "korus_pending_guest";
  const PENDING_CONF_KEY = "korus_pending_conf";
  const LAST_PUBLIC_LINK_KEY = "korus_last_public_link";
  const DRAFT_KEY_PREFIX = "korus_draft_";
  const SIDEBAR_WIDTH_KEY = "korus-sidebar-width";
  const SIDEBAR_WIDTH_MIN = 220;
  const SIDEBAR_WIDTH_MAX = 520;
  const SIDEBAR_WIDTH_DEFAULT = 280;
  const SOUND_NOTIF_KEY = "korus_sound_notify";

  const state = {
    tokens: null,
    authTab: "login",
    loginOptions: null,
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
    activeLiveSession: null,
    activeLiveSessionByChat: {},
    chatLiveSessions: null,
    liveSessionBusy: false,
    liveKitRoom: null,
    liveKitRole: null,
    jitsiIframeEl: null,
    contactImportText: "",
    callStream: null,
    callScreenStream: null,
    callMediaMode: "audio",
    callThumbTimer: null,
    callCamOn: false,
    callMicOn: true,
    rtcPeerIds: [],
    rtcPeerMeta: {},
    rtcSpeakingPeers: {},
    rtcSharingPeers: {},
    rtcPeers: {},
    rtcPendingCandidates: {},
    callPanelToggleBusy: false,
    mediaCaps: null,
    platformCaps: null,
    blobUrls: [],
    unreadByChat: {},
    userSearchHits: null,
    userSearchBusy: false,
    chatPreview: {},
    typingExpireByChat: {},
    readReceiptsByMessage: {},
    readReceiptPopupMessageId: null,
    replyTo: null,
    reactionsByMsg: {},
    shouldScrollThread: false,
    pinnedMessages: [],
    threadHasMore: false,
    threadLoading: false,
    threadLoadingMore: false,
    threadLoadGeneration: 0,
    chatsLoading: false,
    uploadProgress: null,
    virtualFocusMessageId: null,
    messageSearch: "",
    messageSearchHits: null,
    messageSearchBusy: false,
    messageSearchScope: "auto",
    uiPaneFocus: "sidebar",
    composerTtl: "",
    forwardPick: null,
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
    myCustomStatus: "",
    myDndUntil: null,
    myDndDurationPreset: "manual",
    blockedUsers: null,
    sidebarMode: "chats",
    sidebarFolder: "all",
    sidebarChatFilter: "all",
    sidebarWidth: SIDEBAR_WIDTH_DEFAULT,
    mentionPendingChats: {},
    integrationPanel: null,
    discussionThreadRootId: null,
    integrations: null,
    integrationsVitrine: null,
    integrationsMarketplace: null,
    integrationsMarketplaceCategories: null,
    integrationsSearch: "",
    integrationsCategory: "all",
    contacts: null,
    contactsBusy: false,
    myDisplayName: "",
    myUsername: "",
    myProfilePhone: "",
    myProfileEmail: "",
    myAvatarUrl: null,
    myAvatarFileId: null,
    myAvatarHidden: false,
    avatarByUserId: {},
    displayAvatarByChatId: {},
    profileCardUserId: null,
    exportBusy: false,
    exportJobId: null,
    exportJobChatId: null,
    exportProgressLabel: null,
    serverVersion: null,
    membersModalOpen: false,
    chatMembers: null,
    chatBans: null,
    chatMembersBusy: false,
    chatHeaderMembers: null,
    chatHeaderMembersChatId: null,
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
    chatPolls: [],
    chatPollsBusy: false,
    pollCreateOpen: false,
    scheduleSendOpen: false,
    reminderPick: null,
    contactShareOpen: false,
    myReminders: [],
    myRemindersBusy: false,
    myScheduledMessages: [],
    myScheduledMessagesBusy: false,
    threadOfflineCached: false,
    chatKanbanTasks: [],
    chatWhiteboard: null,
    stickerGifs: [],
    stickerPacks: [],
    sipGateway: null,
    threadExtrasTab: null,
    phase5StickersOpen: false,
    phase5AiOpen: false,
    phase5AiReply: null,
    phase5Modal: null,
    phase5Toast: null,
    federationDirectory: [],
    myPasskeys: [],
    passkeysBusy: false,
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
  var messageSearchTimer = null;
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
    instantEpochMs: function (value) {
      if (value == null || value === "") return NaN;
      if (typeof value === "number") return value > 100000000000 ? value : value * 1000;
      var raw = String(value).trim();
      if (/^-?\d+(\.\d+)?$/.test(raw)) {
        var n = Number(raw);
        return n > 100000000000 ? n : n * 1000;
      }
      return new Date(raw).getTime();
    },
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
  var uiAvatar = window.KorusUiAvatar || {
    renderAvatar: function (opts) {
      var d = document.createElement("div");
      d.className = "chat-avatar";
      d.textContent = (opts.title || "?").charAt(0).toUpperCase();
      return d;
    },
  };
  var uiAvatarCrop = window.KorusUiAvatarCrop || null;
  var uiProfileCard = window.KorusUiProfileCard || {
    mountProfileCardOverlay: function () {
      return null;
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
  if (!window.KorusUiTransportUtils) {
    throw new Error("KorusUiTransportUtils required — load ui-transport-utils.js before app.js");
  }
  var uiTransportUtils = window.KorusUiTransportUtils;
  if (!window.KorusUiMessagesUtils) {
    throw new Error("KorusUiMessagesUtils required — load ui-messages-utils.js before app.js");
  }
  var uiMessagesUtils = window.KorusUiMessagesUtils;
  var uiMessageList = window.KorusUiMessageList || null;
  var uiDeepLinkUtils = window.KorusUiDeepLinkUtils || {
    stripDeepLinkFromUrl: function () {
      return { chatId: null, msgId: null, meet: null, conf: null, guest: null };
    },
    buildChatUrl: function () {
      return "";
    },
    buildMessageUrl: function () {
      return "";
    },
    syncChatUrl: function () {},
  };
  var uiClipboardUtils = window.KorusUiClipboardUtils || {
    copyText: function (text, onSuccess, onFallback) {
      if (onFallback) onFallback(text);
    },
  };
  if (!window.KorusUiMessageArticle) {
    throw new Error("KorusUiMessageArticle required — load ui-message-article.js before app.js");
  }
  var uiMessageArticle = window.KorusUiMessageArticle;
  var uiMarkdownUtils = window.KorusUiMarkdownUtils || {
    safeMarkdown: function (s) {
      return s || "";
    },
  };
  if (!window.KorusUiMessageContent) {
    throw new Error("KorusUiMessageContent required — load ui-message-content.js before app.js");
  }
  var uiMessageContent = window.KorusUiMessageContent;
  if (!window.KorusUiMessageReply) {
    throw new Error("KorusUiMessageReply required — load ui-message-reply.js before app.js");
  }
  var uiMessageReply = window.KorusUiMessageReply;
  if (!window.KorusUiComposer) {
    throw new Error("KorusUiComposer required — load ui-composer.js before app.js");
  }
  var uiComposer = window.KorusUiComposer;
  if (!window.KorusUiPolls) {
    throw new Error("KorusUiPolls required — load ui-polls.js before app.js");
  }
  var uiPolls = window.KorusUiPolls;
  if (!window.KorusUiPhase5Ext) {
    throw new Error("KorusUiPhase5Ext required — load ui-phase5-ext.js before app.js");
  }
  var uiPhase5Ext = window.KorusUiPhase5Ext;
  var uiThreadExtras = window.KorusUiThreadExtras || null;
  var uiNoticeToast = window.KorusUiNoticeToast || null;
  if (!window.KorusUiCallAdr) {
    throw new Error("KorusUiCallAdr required — load ui-call-adr.js before app.js");
  }
  var uiCallAdr = window.KorusUiCallAdr;
  var callMeshImportPromise = null;
  var callLivekitImportPromise = null;

  function ensureCallMeshModule() {
    if (window.KorusUiCallMesh) return Promise.resolve(window.KorusUiCallMesh);
    if (!callMeshImportPromise) {
      callMeshImportPromise = import("/ui-lazy-call.mjs").then(function (m) {
        return m.loadCallMesh();
      });
    }
    return callMeshImportPromise;
  }

  function ensureCallLivekitModule() {
    if (window.KorusUiCallLivekit) return Promise.resolve(window.KorusUiCallLivekit);
    if (!callLivekitImportPromise) {
      callLivekitImportPromise = import("/ui-lazy-call.mjs").then(function (m) {
        return m.loadCallLivekit();
      });
    }
    return callLivekitImportPromise;
  }
  if (!window.KorusUiWsHandler) {
    throw new Error("KorusUiWsHandler required — load ui-ws-handler.js before app.js");
  }
  var uiWsHandler = window.KorusUiWsHandler;
  if (!window.KorusUiUxPerception) {
    throw new Error("KorusUiUxPerception required — load ui-ux-perception.js before app.js");
  }
  var uiUx = window.KorusUiUxPerception;
  if (!window.KorusUiFileAttach) {
    throw new Error("KorusUiFileAttach required — load ui-file-attach.js before app.js");
  }
  var uiFileAttach = window.KorusUiFileAttach;
  var wsEvents = window.KorusUiWsEvents || {
    isMessageSendEvent: function () { return false; },
    isMessageChangeEvent: function () { return false; },
    isReactionChangeEvent: function () { return false; },
    isMentionEvent: function () { return false; },
    isPinChangeEvent: function () { return false; },
    isConferenceChangeEvent: function () { return false; },
    isTypingEvent: function () { return false; },
    isPresenceEvent: function () { return false; },
    isAvatarEvent: function () { return false; },
    isChatAvatarEvent: function () { return false; },
    isReadReceiptEvent: function () { return false; },
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

  function loadSidebarWidth() {
    try {
      var n = parseInt(localStorage.getItem(SIDEBAR_WIDTH_KEY), 10);
      if (n >= SIDEBAR_WIDTH_MIN && n <= SIDEBAR_WIDTH_MAX) return n;
    } catch (e) {}
    return SIDEBAR_WIDTH_DEFAULT;
  }

  function saveSidebarWidth(px) {
    try {
      localStorage.setItem(SIDEBAR_WIDTH_KEY, String(px));
    } catch (e) {}
  }

  function applySidebarWidthCss() {
    if (typeof document === "undefined") return;
    document.documentElement.style.setProperty("--sidebar-width", state.sidebarWidth + "px");
  }

  function bindSidebarResizer(handle) {
    if (!handle || handle.dataset.bound === "1") return;
    handle.dataset.bound = "1";
    handle.addEventListener("mousedown", function (e) {
      e.preventDefault();
      var startX = e.clientX;
      var startW = state.sidebarWidth;
      handle.classList.add("dragging");
      function onMove(ev) {
        var w = Math.min(
          SIDEBAR_WIDTH_MAX,
          Math.max(SIDEBAR_WIDTH_MIN, startW + ev.clientX - startX)
        );
        state.sidebarWidth = w;
        applySidebarWidthCss();
      }
      function onUp() {
        handle.classList.remove("dragging");
        saveSidebarWidth(state.sidebarWidth);
        document.removeEventListener("mousemove", onMove);
        document.removeEventListener("mouseup", onUp);
      }
      document.addEventListener("mousemove", onMove);
      document.addEventListener("mouseup", onUp);
    });
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

  function maybeNotifyMention(data) {
    if (!notificationsAllowed() || !isMentionEvent(data)) return;
    var myId = jwtSub(state.tokens.access_token);
    if (!myId || data.mentioned_user_id !== myId) return;
    if (data.chat_id === state.selectedId && !document.hidden) return;
    playNotifySound();
    var title = L("ui.mention.notificationTitle", { chat: chatTitleById(data.chat_id) });
    try {
      var note = new Notification(title, {
        body: L("ui.mention.notificationBody"),
        tag: "korus-mention-" + data.message_id,
        icon: notificationIconUrl(data.chat_id, null),
      });
      note.onclick = function () {
        window.focus();
        note.close();
        openChatById(data.chat_id).catch(function () {});
      };
    } catch (e) {}
  }

  function maybeNotifyMessage(data) {
    if (!notificationsAllowed() || !wsEvents.isMessageSendEvent(data)) return;
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
        icon: notificationIconUrl(data.chatId, data.senderId),
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
        if (p.custom_status_text) state.myCustomStatus = p.custom_status_text;
        state.myDndUntil = p.dnd_until != null ? p.dnd_until : null;
        state.myDisplayName = p.display_name || p.username || "";
        state.myUsername = p.username || "";
        state.myProfilePhone = p.phone || "";
        state.myProfileEmail = p.email || "";
        applyMyProfileAvatar(p);
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

  async function loadIntegrations() {
    if (!state.tokens) return;
    try {
      var res = await apiJson("/me/integrations", { method: "GET" });
      state.integrations = res && res.items ? res.items : [];
      state.integrationsVitrine = res && res.vitrine_tiles ? res.vitrine_tiles : [];
      var market = await apiJson("/me/integrations/marketplace", { method: "GET" });
      state.integrationsMarketplace = market && market.items ? market.items : [];
      state.integrationsMarketplaceCategories =
        market && market.categories ? market.categories : [];
    } catch (e) {
      state.integrations = [];
      state.integrationsMarketplace = [];
    }
  }

  async function connectMarketplaceItem(it) {
    if (!it || !it.id) return;
    state.busy = true;
    render();
    try {
      await apiFetch("/me/integrations/marketplace/" + encodeURIComponent(it.id) + "/connect", {
        method: "POST",
      });
      await loadIntegrations();
    } catch (e) {
      state.error = e.message || L("ui.marketplace.connectFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function disconnectMarketplaceItem(it) {
    if (!it || !it.id) return;
    state.busy = true;
    render();
    try {
      await apiFetch("/me/integrations/marketplace/" + encodeURIComponent(it.id) + "/connect", {
        method: "DELETE",
      });
      await loadIntegrations();
    } catch (e) {
      state.error = e.message || L("ui.marketplace.disconnectFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadContacts() {
    if (!state.tokens) return;
    state.contactsBusy = true;
    try {
      var rows = await apiJson("/contacts", { method: "GET" });
      state.contacts = Array.isArray(rows) ? rows : [];
      ingestAvatarRecords(state.contacts, "id");
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

  function stopTtlRenderTicker() {
    if (ttlRenderTimer) {
      clearInterval(ttlRenderTimer);
      ttlRenderTimer = null;
    }
  }

  function clearDeferredUiTimers() {
    if (draftSaveTimer) {
      clearTimeout(draftSaveTimer);
      draftSaveTimer = null;
    }
    if (typingSidebarTimer) {
      clearTimeout(typingSidebarTimer);
      typingSidebarTimer = null;
    }
    if (typingNotifyTimer) {
      clearTimeout(typingNotifyTimer);
      typingNotifyTimer = null;
    }
    if (userSearchTimer) {
      clearTimeout(userSearchTimer);
      userSearchTimer = null;
    }
    if (messageSearchTimer) {
      clearTimeout(messageSearchTimer);
      messageSearchTimer = null;
    }
    if (chatPreviewMoreTimer) {
      clearTimeout(chatPreviewMoreTimer);
      chatPreviewMoreTimer = null;
    }
    if (uiNoticeToast) uiNoticeToast.reset();
  }

  function dismissNotice() {
    if (uiNoticeToast) {
      uiNoticeToast.dismiss({ state: state, render: scheduleRender });
      return;
    }
    state.error = null;
    state.phase5Toast = null;
    state.statusMessage = null;
    scheduleRender();
  }

  function mountAppNotice(host) {
    if (!uiNoticeToast || !host) return;
    uiNoticeToast.mount(host, {
      el: el,
      state: state,
      dismissNotice: dismissNotice,
      render: scheduleRender,
    });
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

  function openAvatarCropThen(file, onBlob) {
    if (!file) return;
    if (uiAvatarCrop && uiAvatarCrop.open) {
      uiAvatarCrop.open({
        file: file,
        L: L,
        el: el,
        iconBtn: iconBtn,
        modalCardHead: modalCardHead,
        onApply: onBlob,
      });
      return;
    }
    onBlob(file);
  }

  async function uploadMyAvatar(file) {
    if (!file || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var uploadFile =
        file instanceof Blob && !(file instanceof File)
          ? new File([file], "avatar.jpg", { type: "image/jpeg" })
          : file;
      var parsed = await uploadChatFile(uploadFile);
      var fileId = parsed && (parsed.id || parsed.file_id);
      if (!fileId) throw new Error(L("ui.profile.avatarUploadFailed"));
      var p = await apiJson("/users/me", {
        method: "PATCH",
        jsonBody: { avatar_file_id: fileId },
      });
      applyMyProfileAvatar(p);
      state.statusMessage = L("ui.profile.avatarChange");
    } catch (e) {
      state.error = e.message || L("ui.profile.avatarUploadFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function removeMyAvatar() {
    if (!state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var p = await apiJson("/users/me", {
        method: "PATCH",
        jsonBody: { remove_avatar: true },
      });
      applyMyProfileAvatar(p);
      state.statusMessage = L("ui.profile.avatarRemove");
    } catch (e) {
      state.error = e.message || L("profile.saveFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function uploadGroupAvatar(file) {
    var chat = currentChat();
    if (!file || !chat || chat.type !== "group" || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var uploadFile =
        file instanceof Blob && !(file instanceof File)
          ? new File([file], "avatar.jpg", { type: "image/jpeg" })
          : file;
      var parsed = await uploadChatFile(uploadFile);
      var fileId = parsed && (parsed.id || parsed.file_id);
      if (!fileId) throw new Error(L("ui.profile.avatarUploadFailed"));
      var updated = await apiJson("/chats/" + chat.id, {
        method: "PATCH",
        jsonBody: { avatar_file_id: fileId },
      });
      if (updated) {
        if (updated.display_avatar_url != null) {
          chat.display_avatar_url = updated.display_avatar_url;
          if (!state.displayAvatarByChatId) state.displayAvatarByChatId = {};
          state.displayAvatarByChatId[chat.id] = updated.display_avatar_url;
        }
        if (updated.avatar_url != null) chat.avatar_url = updated.avatar_url;
      }
      await refreshChats();
      state.statusMessage = L("ui.profile.avatarChange");
    } catch (e) {
      state.error = e.message || L("ui.profile.avatarUploadFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function saveAvatarHidden(hidden) {
    if (!state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var p = await apiJson("/users/me", {
        method: "PATCH",
        jsonBody: { avatar_hidden: !!hidden },
      });
      if (p) {
        state.myAvatarHidden = !!p.avatar_hidden;
        applyMyProfileAvatar(p);
      }
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

  async function toggleChatArchive() {
    var chat = currentChat();
    if (!chat || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var next = !chat.archived;
      await apiJson("/chats/" + chat.id + "/archive", {
        method: "PATCH",
        jsonBody: { archived: next },
      });
      chat.archived = next;
      await loadChats();
    } catch (e) {
      state.error = e.message || L("ui.sidebar.archiveFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function setChatFolder(tag) {
    var chat = currentChat();
    if (!chat || !state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + chat.id + "/folder", {
        method: "PATCH",
        jsonBody: { folder_tag: tag || null },
      });
      chat.folder_tag = tag || null;
    } catch (e) {
      state.error = e.message || L("ui.sidebar.folderFailed");
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
      ingestAvatarRecords(state.chatMembers, "user_id");
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
    return L("conference.participantsBadge", { count: count });
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
      if (window.KorusUiCallLivekit) KorusUiCallLivekit.disconnectRoom(state);
      stopMeshCallMedia();
      state.callMode = "jitsi";
      render();
      return;
    }
    if (mode === "livekit") {
      try {
        await ensureCallLivekitModule();
      } catch (e) {
        state.error = L("conference.livekitUnavailable");
        render();
        return;
      }
      if (!window.KorusUiCallLivekit || !KorusUiCallLivekit.groupCallSfuEnabled(state)) {
        state.error = L("conference.livekitUnavailable");
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
      stopMeshCallMedia();
      state.callMode = "livekit";
      state.callPanelToggleBusy = true;
      render();
      try {
        await KorusUiCallLivekit.joinGroupCall(state, apiJson);
      } catch (e) {
        state.error = localErr(e.message) || L("conference.livekitJoinFailed");
        state.callMode = "jitsi";
        KorusUiCallLivekit.disconnectRoom(state);
      } finally {
        state.callPanelToggleBusy = false;
      }
      render();
      return;
    }
    if (window.KorusUiCallLivekit) KorusUiCallLivekit.disconnectRoom(state);
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
      await ensureCallMeshModule();
      await ensureCallAudio();
      await loadRtcPeerIds();
      if (state.callCamOn) {
        await addCallVideoTrack();
      }
      startThumbCapture();
      attachLocalVideo();
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.ensureSpeakerMonitor(state);
        KorusUiCallMesh.syncAllSlots(state);
      }
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
      ingestAvatarRecords(state.blockedUsers, "user_id");
    } catch (e) {
      state.blockedUsers = null;
    }
  }

  function instantToMs(v) {
    if (v == null || v === "") return null;
    if (typeof v === "number") return v < 1e12 ? v * 1000 : v;
    var d = new Date(v);
    return isNaN(d.getTime()) ? null : d.getTime();
  }

  function dndUntilIso(ms) {
    if (!ms) return null;
    return new Date(ms).toISOString();
  }

  function computeDndUntilMs(preset) {
    var now = Date.now();
    if (preset === "30m") return now + 30 * 60 * 1000;
    if (preset === "1h") return now + 60 * 60 * 1000;
    if (preset === "4h") return now + 4 * 60 * 60 * 1000;
    if (preset === "tomorrow") {
      var d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(9, 0, 0, 0);
      return d.getTime();
    }
    return null;
  }

  async function updatePresence(status) {
    if (!state.tokens || !status) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var body = { presence_status: status };
      if (state.myCustomStatus) {
        body.custom_status_text = state.myCustomStatus;
      }
      if (status === "dnd") {
        var dndMs = computeDndUntilMs(state.myDndDurationPreset);
        if (dndMs) {
          body.dnd_until = dndUntilIso(dndMs);
        }
      }
      var p = await apiJson("/users/me/presence", {
        method: "PATCH",
        jsonBody: body,
      });
      if (p && p.presence_status) {
        state.myPresence = p.presence_status;
      } else {
        state.myPresence = status;
      }
      if (p && p.custom_status_text) {
        state.myCustomStatus = p.custom_status_text;
      }
      if (p) {
        state.myDndUntil = p.dnd_until != null ? p.dnd_until : null;
      }
    } catch (e) {
      state.error = e.message || L("profile.presenceUpdateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function updateDndSchedule(preset) {
    if (!state.tokens || state.myPresence !== "dnd") return;
    state.myDndDurationPreset = preset || "manual";
    state.busy = true;
    state.error = null;
    render();
    try {
      var dndMs = computeDndUntilMs(preset);
      var body = { presence_status: "dnd" };
      if (dndMs) {
        body.dnd_until = dndUntilIso(dndMs);
      }
      var p = await apiJson("/users/me/presence", {
        method: "PATCH",
        jsonBody: body,
      });
      if (p) {
        if (p.presence_status) state.myPresence = p.presence_status;
        if (p.custom_status_text) state.myCustomStatus = p.custom_status_text;
        state.myDndUntil = p.dnd_until != null ? p.dnd_until : null;
      } else if (dndMs) {
        state.myDndUntil = dndUntilIso(dndMs);
      } else {
        state.myDndUntil = null;
      }
    } catch (e) {
      state.error = e.message || L("profile.presenceUpdateFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function saveCustomStatus() {
    if (!state.tokens) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var p = await apiJson("/users/me/presence", {
        method: "PATCH",
        jsonBody: {
          custom_status_text: state.myCustomStatus || "",
        },
      });
      if (p && p.custom_status_text) {
        state.myCustomStatus = p.custom_status_text;
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

  function closeMobileThread() {
    persistCurrentComposerDraft();
    state.selectedId = null;
    state.discussionThreadRootId = null;
    state.uiPaneFocus = "sidebar";
    render();
  }

  function bumpThreadLoadGeneration() {
    state.threadLoadGeneration = (state.threadLoadGeneration || 0) + 1;
    return state.threadLoadGeneration;
  }

  function isThreadLoadStale(chatId, generation, discussionRoot) {
    return (
      state.selectedId !== chatId ||
      (state.discussionThreadRootId || null) !== (discussionRoot || null) ||
      state.threadLoadGeneration !== generation
    );
  }

  function threadNavStillActive(chatId, generation) {
    return state.selectedId === chatId && state.threadLoadGeneration === generation;
  }

  function openChatById(chatId, options) {
    options = options || {};
    state.discussionThreadRootId = null;
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
    clearMentionPending(chatId);
    clearReplyTo();
    state.chatPolls = [];
    state.messageSearchHits = null;
    state.pollCreateOpen = false;
    state.threadExtrasTab = null;
    state.messageSearchScope = "auto";
    state.uiPaneFocus = "thread";
    state.scheduleSendOpen = false;
    if ((state.messageSearch || "").trim().length >= 2) {
      scheduleMessageSearch();
    }
    state.reminderPick = null;
    state.contactShareOpen = false;
    state.threadOfflineCached = false;
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
          if (uiDeepLinkUtils.syncChatUrl) {
            uiDeepLinkUtils.syncChatUrl(chatId);
          }
          render();
        })
        .catch(function (err) {
          state.error = err.message;
          render();
        });
    }
    var loadGen = bumpThreadLoadGeneration();
    state.messages = [];
    state.threadLoading = true;
    render();
    return loadThread(chatId, { loadGeneration: loadGen })
      .then(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        scheduleRender();
        return markChatRead(chatId);
      })
      .then(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        return refreshChatMeta(chatId);
      })
      .then(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        loadChatHeaderMembers(chatId).then(scheduleRender).catch(function () {});
      })
      .then(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        if (state.callPanelOpen) {
          return loadChatConferences();
        }
      })
      .then(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        openWs();
        if (uiDeepLinkUtils.syncChatUrl) {
          uiDeepLinkUtils.syncChatUrl(chatId);
        }
        scheduleRender();
      })
      .catch(function (err) {
        if (!threadNavStillActive(chatId, loadGen)) return;
        state.error = err.message;
        scheduleRender();
      })
      .finally(function () {
        if (!threadNavStillActive(chatId, loadGen)) return;
        state.threadLoading = false;
        scheduleRender();
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
        loadMyReminders(),
        loadMyScheduledMessages(),
        loadFederationDirectory(),
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
      if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
        openWs();
      }
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
          openWs({ force: true });
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
    state.blobUrls = (uiMessagesUtils.revokeBlobUrls || function (urls) {
      (urls || []).forEach(function (u) {
        try {
          URL.revokeObjectURL(u);
        } catch (e) {}
      });
      return [];
    })(state.blobUrls);
  }

  function messageTypeForMime(mime) {
    if (uiMessagesUtils.messageTypeForMime) {
      return uiMessagesUtils.messageTypeForMime(mime);
    }
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
  function isOpenMlsDevEnabled() {
    try {
      if (localStorage.getItem("e2ee_openmls_dev") === "1") return true;
      var q = new URLSearchParams(window.location.search);
      return q.get("e2ee_openmls_dev") === "1";
    } catch (e) {
      return false;
    }
  }
  function getKorusMlsWasm() {
    if (!korusMlsWasmInstance) {
      var factory = isOpenMlsDevEnabled() && window.KorusOpenMlsDevFactory
        ? window.KorusOpenMlsDevFactory
        : window.KorusMlsWasmFactory;
      if (!factory) return null;
      korusMlsWasmInstance = factory(apiJson);
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
    L: L,
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
      if (cacheKey === "e2eePlaintextCache") {
        if (!state.e2eePlaintextCache) state.e2eePlaintextCache = {};
        state.e2eePlaintextCache[msgId] = text;
      }
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

  function safeMarkdown(src) {
    return uiMarkdownUtils.safeMarkdown(src);
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
    if (window.KorusUiCallMesh) {
      KorusUiCallMesh.unregisterPeerStream(state, peerId);
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
      await ensureCallAudio();
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
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.ensureSpeakerMonitor(state);
        KorusUiCallMesh.syncAllSlots(state);
      }
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

  function instantEpochMs(value) {
    return uiFormatUtils.instantEpochMs ? uiFormatUtils.instantEpochMs(value) : new Date(value).getTime();
  }

  function formatChatListTime(ms) {
    return uiFormatUtils.formatChatListTime(ms);
  }

  function chatListTimeMs(c) {
    var prev = state.chatPreview[c.id];
    if (prev && prev.at) return prev.at;
    if (c.created_at) return instantEpochMs(c.created_at);
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
    uiClipboardUtils.copyText(
      text,
      function () {
        state.statusMessage = okMessage;
        render();
      },
      function (fallback) {
        state.statusMessage = okMessage + ": " + fallback;
        render();
      }
    );
  }

  function copyChatDeepLink() {
    if (!state.selectedId) return;
    copyTextToClipboardOrShow(
      uiDeepLinkUtils.buildChatUrl(state.selectedId),
      L("chat.chatLinkCopied")
    );
  }

  function copyMessageDeepLink(m) {
    if (!m || !m.id || !state.selectedId) return;
    copyTextToClipboardOrShow(
      uiDeepLinkUtils.buildMessageUrl(state.selectedId, m.id),
      L("chat.messageLinkCopied")
    );
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
    state.callMediaMode = "audio";
    state.callCamOn = false;
    state.callMicOn = true;
    if (window.KorusUiCallMesh) {
      KorusUiCallMesh.resetCallMeshUi(state);
    }
  }

  async function ensureCallAudio() {
    if (state.callStream && state.callStream.getAudioTracks().length) return;
    try {
      var audioOnly = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: false,
      });
      if (!state.callStream) {
        state.callStream = audioOnly;
      } else {
        audioOnly.getAudioTracks().forEach(function (t) {
          state.callStream.addTrack(t);
        });
      }
      state.callMediaMode = "audio";
      state.callCamOn = false;
    } catch (e) {
      state.error = localMediaErr(e.message);
      throw e;
    }
  }

  async function addCallVideoTrack() {
    if (!state.callStream) await ensureCallAudio();
    var existing = state.callStream.getVideoTracks();
    if (existing.length) {
      existing.forEach(function (t) {
        t.enabled = true;
      });
      state.callCamOn = true;
      state.callMediaMode = "video";
      return;
    }
    var videoOnly = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user" },
      audio: false,
    });
    var vt = videoOnly.getVideoTracks()[0];
    if (!vt) return;
    state.callStream.addTrack(vt);
    state.callCamOn = true;
    state.callMediaMode = "video";
    Object.keys(state.rtcPeers).forEach(function (pid) {
      var pc = state.rtcPeers[pid];
      if (!pc) return;
      try {
        pc.addTrack(vt, state.callStream);
      } catch (e) {}
    });
    await rtcRenegotiateMesh();
  }

  /** @deprecated use ensureCallAudio or addCallVideoTrack */
  async function ensureCallStream() {
    if (state.callMediaMode === "video" || state.callCamOn) {
      await ensureCallAudio();
      await addCallVideoTrack();
      return;
    }
    await ensureCallAudio();
  }

  async function loadRtcPeerIds() {
    state.rtcPeerIds = [];
    state.rtcPeerMeta = {};
    if (!state.selectedId || !state.tokens) return;
    try {
      var rows = await apiJson("/chats/" + state.selectedId + "/members", { method: "GET" });
      var me = jwtSub(state.tokens.access_token);
      rows.forEach(function (r) {
        if (r.user_id === me) return;
        state.rtcPeerIds.push(r.user_id);
        state.rtcPeerMeta[r.user_id] = {
          displayName: r.display_name || r.user_id,
        };
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
        if (window.KorusUiCallMesh) {
          KorusUiCallMesh.handleRemoteTrack(state, peerId, ev.track, ev.streams[0]);
        }
        return;
      }
      var v = wrap.querySelector("video.rtc-remote-cam");
      if (v && ev.streams[0]) {
        v.srcObject = ev.streams[0];
      }
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.handleRemoteTrack(state, peerId, ev.track, ev.streams[0]);
      }
    };
    pc.onconnectionstatechange = function () {
      if (pc.connectionState !== "failed") return;
      try {
        sendRtcSignal({ kind: "hangup", targetUserId: peerId });
      } catch (e1) {}
      teardownPeer(peerId);
      if (state.callPanelOpen) {
        state.error = L("rtc.iceFailedHelp");
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
    if (window.KorusUiCallMesh) {
      KorusUiCallMesh.syncLocalStage(state);
    }
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
    if (state.callPanelToggleBusy) return;
    state.callPanelOpen = !state.callPanelOpen;
    if (!state.callPanelOpen) {
      state.callPanelToggleBusy = true;
      try {
        if (state.activeConference && conferenceIsTracked(state.activeConference)) {
          await leaveActiveConference();
        } else if (state.activeConference) {
          state.activeConference = null;
          clearJitsiIframe();
        }
        stopMeshCallMedia();
        if (window.KorusUiLiveSession) {
          KorusUiLiveSession.disconnectLiveKitRoom(state);
          state.activeLiveSession = null;
        }
        render();
      } finally {
        state.callPanelToggleBusy = false;
      }
      return;
    }
    render();
    state.callPanelToggleBusy = true;
    try {
      await loadActiveConferences();
      await loadChatConferences();
      if (window.KorusUiLiveSession) {
        await KorusUiLiveSession.loadChatLiveSessions(state, apiJson);
      }
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
      await ensureCallMeshModule();
      await ensureCallAudio();
      await loadRtcPeerIds();
      startThumbCapture();
      render();
      attachLocalVideo();
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.ensureSpeakerMonitor(state);
        KorusUiCallMesh.syncAllSlots(state);
      }
      setTimeout(function () {
        beginRtcMesh();
      }, 120);
    } catch (e) {
      state.error = (e && e.message) || L("conference.meshUnavailable");
      if (state.callMode === "mesh") {
        state.callMode = "jitsi";
      }
      render();
    } finally {
      state.callPanelToggleBusy = false;
    }
  }

  function attachLocalVideo() {
    var v = document.getElementById("callLocalVideo");
    if (v && state.callStream) {
      v.srcObject = state.callStream;
    }
    if (window.KorusUiCallMesh) {
      KorusUiCallMesh.ensureSpeakerMonitor(state);
      KorusUiCallMesh.syncLocalStage(state);
    }
  }

  function startThumbCapture() {
    if (state.callThumbTimer) clearInterval(state.callThumbTimer);
    if (!state.callCamOn || state.callMediaMode !== "video") return;
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
    if (!state.callStream) await ensureCallAudio();
    if (!state.callStream.getVideoTracks().length) {
      try {
        await addCallVideoTrack();
        startThumbCapture();
      } catch (e) {
        state.error = localMediaErr(e.message);
      }
      render();
      attachLocalVideo();
      return;
    }
    state.callCamOn = !state.callCamOn;
    state.callStream.getVideoTracks().forEach(function (t) {
      t.enabled = state.callCamOn;
    });
    state.callMediaMode = state.callCamOn ? "video" : "audio";
    if (!state.callCamOn && state.callThumbTimer) {
      clearInterval(state.callThumbTimer);
      state.callThumbTimer = null;
    } else if (state.callCamOn) {
      startThumbCapture();
    }
    render();
    attachLocalVideo();
  }

  async function toggleScreenShare() {
    if (state.callScreenStream) {
      await stopScreenShareInternal();
      return;
    }
    var mediaDevices = navigator.mediaDevices;
    if (!mediaDevices || typeof mediaDevices.getDisplayMedia !== "function") {
      state.error = L("rtc.screenShareFailed", {
        detail: L("rtc.screenShareUnsupported"),
      });
      render();
      return;
    }
    try {
      state.callScreenStream = await mediaDevices.getDisplayMedia({ video: true, audio: false });
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
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.syncLocalStage(state);
      }
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
    return wsEvents.isMessageSendEvent(o);
  }

  function isMessageChangeEvent(o) {
    return wsEvents.isMessageChangeEvent(o);
  }

  function isReactionChangeEvent(o) {
    return wsEvents.isReactionChangeEvent(o);
  }

  function isMentionEvent(o) {
    return wsEvents.isMentionEvent(o);
  }

  function isPinChangeEvent(o) {
    return wsEvents.isPinChangeEvent(o);
  }

  function isConferenceChangeEvent(o) {
    return wsEvents.isConferenceChangeEvent(o);
  }

  function isTypingEvent(o) {
    return wsEvents.isTypingEvent(o);
  }

  function isPresenceEvent(o) {
    return wsEvents.isPresenceEvent(o);
  }

  function isAvatarEvent(o) {
    return wsEvents.isAvatarEvent(o);
  }

  function isChatAvatarEvent(o) {
    return wsEvents.isChatAvatarEvent(o);
  }

  function isReadReceiptEvent(o) {
    return wsEvents.isReadReceiptEvent(o);
  }

  function messageMentionsMe(m) {
    var myId = jwtSub(state.tokens && state.tokens.access_token);
    if (!myId || !m) return false;
    if (m.mention_all) return true;
    var ids = m.mention_user_ids;
    if (!ids || !ids.length) return false;
    return ids.indexOf(myId) >= 0;
  }

  function openDiscussionThread(rootId) {
    if (!rootId) return;
    state.discussionThreadRootId = rootId;
    if (state.selectedId) {
      var loadGen = bumpThreadLoadGeneration();
      state.threadLoading = true;
      render();
      loadThread(state.selectedId, { loadGeneration: loadGen })
        .then(function () {
          state.threadLoading = false;
          render();
        })
        .catch(function () {
          state.threadLoading = false;
          render();
        });
    }
  }

  function closeDiscussionThread() {
    state.discussionThreadRootId = null;
    if (state.selectedId) {
      var loadGen = bumpThreadLoadGeneration();
      state.threadLoading = true;
      render();
      loadThread(state.selectedId, { loadGeneration: loadGen })
        .then(function () {
          state.threadLoading = false;
          render();
        })
        .catch(function () {
          state.threadLoading = false;
          render();
        });
    }
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
        loadConferenceParticipants(data.conference_id)
          .then(function () {
            if (state.selectedId === data.chat_id) scheduleRender();
          })
          .catch(function () {});
      }
    }
    if (
      data.change === "updated" &&
      state.activeConference &&
      state.activeConference.conference_id === data.conference_id
    ) {
      loadConferenceParticipants(data.conference_id)
        .then(function () {
          if (
            state.activeConference &&
            state.activeConference.conference_id === data.conference_id
          ) {
            scheduleRender();
          }
        })
        .catch(function () {});
    }
  }

  function applyReactionChangeEvent(data) {
    if (data.chatId !== state.selectedId) return;
    if (!state.reactionsByMsg) {
      state.reactionsByMsg = {};
    }
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

  function applyPresenceEvent(ev) {
    if (!ev || !ev.user_id) return;
    var myId = jwtSub(state.tokens && state.tokens.access_token);
    if (myId && ev.user_id === myId) {
      if (ev.presence_status) state.myPresence = ev.presence_status;
      if (ev.custom_status_text) state.myCustomStatus = ev.custom_status_text;
      if (ev.dnd_until !== undefined) state.myDndUntil = ev.dnd_until != null ? ev.dnd_until : null;
    }
  }

  function applyAvatarEvent(ev) {
    if (!ev || !ev.user_id) return;
    rememberUserAvatar(ev.user_id, ev.avatar_url, null);
    var meId = state.tokens ? jwtSub(state.tokens.access_token) : null;
    if (meId && ev.user_id === meId) {
      state.myAvatarUrl = ev.avatar_url || null;
      state.myAvatarFileId = ev.avatar_file_id || null;
    }
  }

  function applyChatAvatarEvent(ev) {
    if (!ev || !ev.chat_id) return;
    if (!state.displayAvatarByChatId) state.displayAvatarByChatId = {};
    if (ev.display_avatar_url !== undefined) {
      state.displayAvatarByChatId[ev.chat_id] = ev.display_avatar_url || null;
    }
    var chat = state.chats.find(function (c) {
      return c.id === ev.chat_id;
    });
    if (!chat) return;
    if (ev.avatar_file_id !== undefined) chat.avatar_file_id = ev.avatar_file_id;
    if (ev.display_avatar_url !== undefined) {
      chat.display_avatar_url = ev.display_avatar_url;
      chat.avatar_url = ev.display_avatar_url;
    }
  }

  function syncDisplayAvatarsFromChats() {
    if (!state.displayAvatarByChatId) state.displayAvatarByChatId = {};
    (state.chats || []).forEach(function (c) {
      var url = c.display_avatar_url || c.avatar_url || null;
      if (url) state.displayAvatarByChatId[c.id] = url;
    });
  }

  function openProfileCard(userId) {
    if (!userId) return;
    var meId = state.tokens ? jwtSub(state.tokens.access_token) : null;
    if (meId && userId === meId) return;
    state.profileCardUserId = userId;
    render();
  }

  function closeProfileCard() {
    state.profileCardUserId = null;
    render();
  }

  function applyReadReceiptEvent(ev) {
    if (!state.readReceiptsByMessage) {
      state.readReceiptsByMessage = {};
    }
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
    var rows =
      (state.messagesByChat && state.messagesByChat[chatId]) || state.messages || [];
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
    if (base === "image" || base === "video" || base === "file" || base === "voice" || base === "audio") return base;
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

  function getMessageContentCtx() {
    return {
      L: L,
      el: el,
      iconBtn: iconBtn,
      state: state,
      render: render,
      messageAttachmentKind: messageAttachmentKind,
      messageAttachmentFileId: messageAttachmentFileId,
      attachAuthenticatedImage: attachAuthenticatedImage,
      attachAuthenticatedAudio: attachAuthenticatedAudio,
      downloadChatFile: downloadChatFile,
      isE2eeType: isE2eeType,
      isMlsCapabilitiesActive: isMlsCapabilitiesActive,
      loadE2eePlaintext: loadE2eePlaintext,
      safeMarkdown: safeMarkdown,
    };
  }

  function appendMessageAttachment(bodyEl, kind, fileId, durationMs) {
    uiMessageContent.appendMessageAttachment(bodyEl, kind, fileId, durationMs, getMessageContentCtx());
  }

  function chatTitleById(chatId) {
    return displayTitleForChat(null, chatId);
  }

  function peerLabelFromMembers(members, myId) {
    if (!members || !members.length) return null;
    for (var i = 0; i < members.length; i++) {
      var m = members[i];
      if (m.user_id !== myId) {
        var dn = m.display_name && String(m.display_name).trim();
        return dn || m.username || m.user_id.slice(0, 8);
      }
    }
    return null;
  }

  function displayTitleForChat(chat, chatId) {
    chatId = chatId || (chat && chat.id);
    if (!chatId) return L("ui.message.chatFallback");
    if (!chat) {
      chat = state.chats.find(function (x) {
        return x.id === chatId;
      });
    }
    if (chat && chat.title && String(chat.title).trim()) {
      return String(chat.title).trim();
    }
    if (chat && chat.type === "p2p") {
      var myId = state.tokens ? jwtSub(state.tokens.access_token) : null;
      if (state.chatHeaderMembersChatId === chatId && state.chatHeaderMembers) {
        var peer = peerLabelFromMembers(state.chatHeaderMembers, myId);
        if (peer) return peer;
      }
    }
    return chatId.slice(0, 8) + "…";
  }

  async function loadChatHeaderMembers(chatId) {
    if (!chatId || !state.tokens) {
      state.chatHeaderMembers = null;
      state.chatHeaderMembersChatId = null;
      return;
    }
    var chat = state.chats.find(function (c) {
      return c.id === chatId;
    });
    if (!chat || chat.type !== "p2p") {
      state.chatHeaderMembers = null;
      state.chatHeaderMembersChatId = null;
      return;
    }
    try {
      var rows = await apiJson("/chats/" + chatId + "/members", { method: "GET" });
      state.chatHeaderMembers = Array.isArray(rows) ? rows : [];
      state.chatHeaderMembersChatId = chatId;
      ingestAvatarRecords(state.chatHeaderMembers, "user_id");
    } catch (e) {
      state.chatHeaderMembers = null;
      state.chatHeaderMembersChatId = null;
    }
  }

  function formatPreviewText(type, content) {
    return uiMessagesUtils.formatPreviewText(
      type,
      content,
      isE2eeType,
      e2eePlainType,
      L
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
      instantEpochMs(m.created_at),
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

  async function loadPlatformCaps() {
    try {
      state.platformCaps = await apiJson("/platform/capabilities", { method: "GET", noAuth: true });
    } catch (e) {
      state.platformCaps = null;
    }
  }

  function isPlatformAddonEnabled(addonId) {
    if (!addonId || !state.platformCaps || !state.platformCaps.modules) return false;
    var mod = state.platformCaps.modules[addonId];
    return !!(mod && mod.state === "enabled");
  }

  function isPlatformFeatureEnabled(featureKey) {
    if (!featureKey || !state.platformCaps || !state.platformCaps.features) return false;
    var feature = state.platformCaps.features[featureKey];
    return !!(feature && feature.state === "enabled");
  }

  var PLATFORM_BASE_UI_FEATURES = {
    "chat.mute": true,
    "chat.archive": true,
    "chat.folders": true,
    "chat.members.list": true,
    "chat.read": true,
    "message.send": true,
    "file.upload": true,
    "contacts.list": true,
  };

  function isPlatformFeatureVisible(featureKey) {
    if (!featureKey) return false;
    if (!state.platformCaps || !state.platformCaps.features) {
      return !!PLATFORM_BASE_UI_FEATURES[featureKey];
    }
    var feature = state.platformCaps.features[featureKey];
    if (!feature) return false;
    return feature.state === "enabled";
  }

  async function loadChats() {
    if (!state.tokens) return;
    state.chatsLoading = true;
    render();
    try {
      var list = await apiJson("/chats", { method: "GET" });
      state.chats = list;
      syncDisplayAvatarsFromChats();
    } finally {
      state.chatsLoading = false;
    }
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
    updateAppBadge();
    scheduleRender();
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
    scheduleRender();
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
        updateAppBadge();
        scheduleRender();
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
    if (!state.readReceiptsByMessage) {
      state.readReceiptsByMessage = {};
    }
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
    if (!messageId) return;
    if (!state.readReceiptsByMessage) {
      state.readReceiptsByMessage = {};
    }
    var rr = state.readReceiptsByMessage[messageId];
    if (!rr || !Object.keys(rr).length) {
      state.error = L("readReceipts.none");
      render();
      return;
    }
    state.readReceiptPopupMessageId = messageId;
    render();
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
          ingestAvatarRecords(state.userSearchHits, "user_id");
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
    var generation =
      options.loadGeneration != null ? options.loadGeneration : state.threadLoadGeneration || 0;
    var discussionRoot = state.discussionThreadRootId || null;
    function stale() {
      return isThreadLoadStale(chatId, generation, discussionRoot);
    }
    if (!state.tokens) return;
    if (!options.preserveBlobs) revokeBlobUrls();
    if (!options.preserveE2eeCache) state.e2eePlaintextCache = {};
    var offline =
      typeof navigator.onLine === "boolean" && !navigator.onLine && window.KorusOfflineCache;
    if (offline) {
      var cached = await window.KorusOfflineCache.getMessages(chatId);
      if (stale()) return;
      if (cached && cached.length) {
        state.messages = sortMessagesAsc(cached);
        state.threadHasMore = false;
        state.threadOfflineCached = true;
        syncPreviewFromThread(chatId);
        if (!options.keepScroll) {
          state.shouldScrollThread = true;
        }
        return;
      }
    }
    state.threadOfflineCached = false;
    var q = new URLSearchParams({ limit: String(THREAD_PAGE) });
    if (state.discussionThreadRootId) {
      q.set("thread_id", state.discussionThreadRootId);
    }
    var rows = await apiJson("/chats/" + chatId + "/messages?" + q, { method: "GET" });
    if (stale()) return;
    state.messages = sortMessagesAsc(rows);
    state.threadHasMore = rows.length >= THREAD_PAGE;
    if (window.KorusOfflineCache) {
      window.KorusOfflineCache.putMessages(chatId, state.messages).catch(function () {});
    }
    syncPreviewFromThread(chatId);
    if (!options.keepScroll) {
      state.shouldScrollThread = true;
    }
    loadThreadExtras(chatId, options);
  }

  function loadThreadExtras(chatId, options) {
    options = options || {};
    var generation =
      options.loadGeneration != null ? options.loadGeneration : state.threadLoadGeneration || 0;
    var discussionRoot = state.discussionThreadRootId || null;
    function stale() {
      return isThreadLoadStale(chatId, generation, discussionRoot);
    }
    (async function () {
      try {
        await loadReactionsForThread(chatId);
        if (stale()) return;
        await loadPinnedMessages(chatId);
        if (stale()) return;
        await hydrateReadReceiptsForThread(chatId);
        if (stale()) return;
        var liveConf = activeConferenceInChat(chatId);
        if (liveConf && liveConf.conference_id) {
          loadConferenceParticipants(liveConf.conference_id).catch(function () {});
        } else if (state.conferenceParticipantsConfId) {
          clearConferenceParticipants();
        }
        scheduleRender();
        var c = (state.chats || []).find(function (x) {
          return x.id === chatId;
        });
        if (c && c.type === "group") {
          if (isPlatformFeatureVisible("productivity.polls.list")) {
            loadChatPolls(chatId).catch(function () {});
          }
          if (isPlatformFeatureVisible("collaboration.kanban.list")) {
            loadChatKanban(chatId).catch(function () {});
          }
          if (isPlatformFeatureVisible("collaboration.whiteboard.open")) {
            loadChatWhiteboard(chatId).catch(function () {});
          }
        }
      } catch (e) {
        if (!stale()) scheduleRender();
      }
    })();
  }

  async function loadChatPolls(chatId) {
    if (!state.tokens || !chatId) {
      state.chatPolls = [];
      return;
    }
    state.chatPollsBusy = true;
    try {
      var rows = await apiJson("/chats/" + chatId + "/polls?limit=20", { method: "GET" });
      state.chatPolls = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.chatPolls = [];
    } finally {
      state.chatPollsBusy = false;
    }
  }

  function openPollCreate() {
    var chat = currentChat();
    if (!chat || chat.type !== "group") {
      return;
    }
    state.pollCreateOpen = true;
    render();
  }

  function closePollCreate() {
    state.pollCreateOpen = false;
    render();
  }

  function openScheduleSend() {
    state.scheduleSendOpen = true;
    render();
  }

  function closeScheduleSend() {
    state.scheduleSendOpen = false;
    render();
  }

  async function createChatPoll(question, options, allowMultiple) {
    var chat = currentChat();
    if (!chat || chat.type !== "group") {
      return;
    }
    if (!state.selectedId || !question || !options || !options.length) {
      state.error = L("ui.polls.createFailed");
      render();
      return;
    }
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/polls", {
        method: "POST",
        jsonBody: {
          question: question,
          options: options,
          allow_multiple: !!allowMultiple,
        },
      });
      state.pollCreateOpen = false;
      await loadChatPolls(state.selectedId);
    } catch (err) {
      state.error = err.message || L("ui.polls.createFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function voteChatPoll(pollId, optionIndexes) {
    if (!state.selectedId || !pollId) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson(
        "/chats/" + state.selectedId + "/polls/" + pollId + "/vote",
        {
          method: "POST",
          jsonBody: { option_indexes: optionIndexes },
        }
      );
      await loadChatPolls(state.selectedId);
    } catch (err) {
      state.error = err.message || L("ui.polls.voteFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function closeChatPoll(pollId) {
    if (!state.selectedId || !pollId) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/polls/" + pollId + "/close", {
        method: "POST",
      });
      state.phase5Toast = L("ui.polls.closedByYou");
      await loadChatPolls(state.selectedId);
    } catch (err) {
      state.error = err.message || L("ui.polls.closeFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function scheduleComposerMessage(text, scheduledAtIso) {
    if (!state.selectedId || !text || !scheduledAtIso) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var body = {
        type: "text",
        content: text,
        scheduled_at: scheduledAtIso,
        reply_to_msg_id: currentReplyToId(),
        client_msg_id: null,
      };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
      await apiJson("/chats/" + state.selectedId + "/messages/scheduled", {
        method: "POST",
        jsonBody: body,
      });
      var ta = document.getElementById("msgdraft");
      if (ta) ta.value = "";
      clearComposerDraftForChat(state.selectedId);
      clearReplyTo();
      state.scheduleSendOpen = false;
      state.statusMessage = L("ui.schedule.ok");
      await loadMyScheduledMessages();
    } catch (err) {
      state.error = err.message || L("ui.schedule.failed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function getPollsUiCtx() {
    return {
      el: el,
      iconBtn: iconBtn,
      L: L,
      state: state,
      render: render,
      openPollCreate: openPollCreate,
      closePollCreate: closePollCreate,
      closeScheduleSend: closeScheduleSend,
      createPoll: createChatPoll,
      votePoll: voteChatPoll,
      closePoll: closeChatPoll,
      reloadChatPolls: function () {
        var c = currentChat();
        if (c && c.type === "group" && state.selectedId) {
          loadChatPolls(state.selectedId).then(render);
        }
      },
      currentUserId: function () {
        return jwtSub(state.tokens && state.tokens.access_token);
      },
      scheduleMessage: scheduleComposerMessage,
      closeMessageReminder: closeMessageReminder,
      createMessageReminder: createMessageReminder,
      closeContactShare: closeContactShare,
      shareSelfContact: shareSelfContact,
      sendContactCard: sendContactCard,
    };
  }

  function toggleThreadExtrasTab(tabId) {
    var chat = currentChat();
    if (!chat || chat.type !== "group") {
      return;
    }
    if (tabId === "polls" && !isPlatformFeatureVisible("productivity.polls.list")) return;
    if (tabId === "kanban" && !isPlatformFeatureVisible("collaboration.kanban.list")) return;
    if (tabId === "whiteboard" && !isPlatformFeatureVisible("collaboration.whiteboard.open")) return;
    if (state.threadExtrasTab === tabId) {
      state.threadExtrasTab = null;
      render();
      return;
    }
    state.threadExtrasTab = tabId;
    var loadP = Promise.resolve();
    if (tabId === "polls" && state.selectedId) {
      loadP = loadChatPolls(state.selectedId);
    } else if (tabId === "kanban" && state.selectedId) {
      loadP = loadChatKanban(state.selectedId);
    } else if (tabId === "whiteboard" && state.selectedId) {
      loadP = loadChatWhiteboard(state.selectedId);
    }
    loadP.then(render).catch(render);
  }

  function getPhase5UiCtx() {
    return {
      el: el,
      iconBtn: iconBtn,
      L: L,
      state: state,
      render: render,
      apiJson: apiJson,
      openStickersPanel: openStickersPanel,
      closeStickersPanel: closeStickersPanel,
      isPlatformFeatureVisible: isPlatformFeatureVisible,
      addKanbanTask: addKanbanTask,
      moveKanbanTask: moveKanbanTask,
      deleteKanbanTask: deleteKanbanTask,
      saveWhiteboard: saveWhiteboard,
      insertGifMessage: insertGifMessage,
      insertStickerMessage: insertStickerMessage,
      toggleAiAssistPanel: toggleAiAssistPanel,
      closeAiAssistPanel: closeAiAssistPanel,
      runAiAssist: runAiAssist,
      insertAiReplyToComposer: insertAiReplyToComposer,
      loadFederationDirectory: loadFederationDirectory,
      registerPasskeyScaffold: registerPasskeyScaffold,
      saveSipGateway: saveSipGateway,
      createStickerPack: createStickerPack,
      saveEditedMessage: saveEditedMessage,
    };
  }

  function getThreadExtrasUiCtx() {
    var ctx = getPollsUiCtx();
    var p5 = getPhase5UiCtx();
    Object.keys(p5).forEach(function (k) {
      ctx[k] = p5[k];
    });
    ctx.toggleThreadExtrasTab = toggleThreadExtrasTab;
    return ctx;
  }

  async function loadChatKanban(chatId) {
    if (!chatId || !state.tokens) {
      state.chatKanbanTasks = [];
      return;
    }
    try {
      var rows = await apiJson("/chats/" + chatId + "/kanban/tasks", { method: "GET" });
      state.chatKanbanTasks = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.chatKanbanTasks = [];
    }
  }

  async function loadChatWhiteboard(chatId) {
    if (!chatId || !state.tokens) {
      state.chatWhiteboard = null;
      return;
    }
    try {
      state.chatWhiteboard = await apiJson("/chats/" + chatId + "/whiteboard", { method: "GET" });
    } catch (e) {
      state.chatWhiteboard = null;
    }
  }

  async function loadStickerGifs() {
    if (!state.tokens) {
      state.stickerGifs = [];
      return;
    }
    try {
      var rows = await apiJson("/stickers/gifs?q=", { method: "GET" });
      state.stickerGifs = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.stickerGifs = [];
    }
  }

  async function loadStickerPacks() {
    if (!state.tokens) {
      state.stickerPacks = [];
      return;
    }
    try {
      var rows = await apiJson("/stickers/packs", { method: "GET" });
      state.stickerPacks = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.stickerPacks = [];
    }
  }

  async function createStickerPack(name) {
    if (!name) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/stickers/packs", { method: "POST", jsonBody: { name: name } });
      state.phase5Toast = L("ui.phase5.stickerPackCreated");
      await loadStickerPacks();
    } catch (err) {
      state.error = err.message || L("ui.phase5.stickerPackFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadFederationDirectory() {
    if (!state.tokens) {
      state.federationDirectory = [];
      return;
    }
    try {
      var data = await apiJson("/platform/federation/directory", { method: "GET" });
      state.federationDirectory = data && data.partner_orgs ? data.partner_orgs : [];
    } catch (e) {
      state.federationDirectory = [];
    }
  }

  function toggleAiAssistPanel() {
    if (!isPlatformFeatureVisible("ai.assist.request")) return;
    state.phase5AiOpen = !state.phase5AiOpen;
    if (!state.phase5AiOpen) {
      state.phase5AiReply = null;
    }
    render();
  }

  function closeAiAssistPanel() {
    state.phase5AiOpen = false;
    state.phase5AiReply = null;
    render();
  }

  async function runAiAssist(prompt) {
    if (!state.selectedId || !prompt) return;
    state.busy = true;
    state.phase5AiReply = null;
    render();
    try {
      var data = await apiJson("/chats/" + state.selectedId + "/ai/assist", {
        method: "POST",
        jsonBody: { prompt: prompt },
      });
      var reply = data && data.reply;
      if (reply && /gateway not configured|ai-chat-gateway preset/i.test(reply)) {
        state.phase5AiReply = null;
        state.error = L("ui.phase5.aiAssistUnavailable");
      } else {
        state.phase5AiReply = reply || null;
        if (!state.phase5AiReply) {
          state.error = L("ui.phase5.aiAssistEmpty");
        }
      }
    } catch (err) {
      state.error = err.message || L("ui.phase5.aiAssistFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function moveKanbanTask(taskId, columnKey) {
    if (!state.selectedId || !taskId || !columnKey) return;
    state.busy = true;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/kanban/tasks/" + taskId, {
        method: "PATCH",
        jsonBody: { column_key: columnKey },
      });
      await loadChatKanban(state.selectedId);
    } catch (err) {
      state.error = err.message || L("ui.phase5.kanbanFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function deleteKanbanTask(taskId) {
    if (!state.selectedId || !taskId) return;
    state.busy = true;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/kanban/tasks/" + taskId, {
        method: "DELETE",
      });
      await loadChatKanban(state.selectedId);
      state.phase5Toast = L("ui.phase5.kanbanDeleted");
    } catch (err) {
      state.error = err.message || L("ui.phase5.kanbanFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadMyPasskeys() {
    if (!state.tokens) {
      state.myPasskeys = [];
      return;
    }
    state.passkeysBusy = true;
    try {
      var rows = await apiJson("/platform/passkeys", { method: "GET" });
      state.myPasskeys = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.myPasskeys = [];
    } finally {
      state.passkeysBusy = false;
    }
  }

  async function registerPasskeyScaffold() {
    if (!state.tokens) return;
    state.passkeysBusy = true;
    render();
    try {
      var cred = "web-scaffold-" + Date.now().toString(36);
      await apiJson("/platform/passkeys", {
        method: "POST",
        jsonBody: { credential_id: cred, public_key: "pk-scaffold" },
      });
      await loadMyPasskeys();
      state.phase5Toast = L("ui.phase5.passkeysRegistered");
    } catch (err) {
      state.error = err.message || L("ui.phase5.passkeysFailed");
    } finally {
      state.passkeysBusy = false;
      render();
    }
  }

  async function redeemGuestLinkFromUrl(guestToken) {
    if (!guestToken) return;
    try {
      var data = await apiJson("/conferences/guest/" + encodeURIComponent(guestToken), {
        method: "GET",
        noAuth: true,
        noRefresh: true,
      });
      if (!data || data.status === "expired") {
        state.error = L("ui.phase5.guestExpired");
        return;
      }
      if (data.chat_id) {
        await openChatById(data.chat_id);
      }
      if (data.conference_id) {
        try {
          var conf = await apiJson("/conferences/" + data.conference_id, { method: "GET" });
          await joinJitsiConference(conf);
          ensureCallPanelOpen();
        } catch (e) {}
      }
      state.phase5Toast = L("ui.phase5.guestRedeemed");
    } catch (err) {
      state.error = err.message || L("ui.phase5.guestRedeemFailed");
    }
  }

  function openStickersPanel() {
    if (!isPlatformFeatureVisible("productivity.stickers.use")) return;
    state.phase5StickersOpen = true;
    Promise.all([loadStickerGifs(), loadStickerPacks()])
      .then(render)
      .catch(render);
  }

  function closeStickersPanel() {
    state.phase5StickersOpen = false;
    render();
  }

  async function addKanbanTask(title) {
    if (!state.selectedId || !title) return;
    state.busy = true;
    render();
    try {
      await apiJson("/chats/" + state.selectedId + "/kanban/tasks", {
        method: "POST",
        jsonBody: { column_key: "todo", title: title },
      });
      await loadChatKanban(state.selectedId);
    } catch (err) {
      state.error = err.message || L("ui.phase5.kanbanFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function saveWhiteboard(snapshotJson) {
    if (!state.selectedId) return;
    state.busy = true;
    render();
    try {
      state.chatWhiteboard = await apiJson("/chats/" + state.selectedId + "/whiteboard", {
        method: "PUT",
        jsonBody: { title: L("ui.phase5.whiteboardTitle"), snapshot_json: snapshotJson || "{}" },
      });
      state.phase5Toast = L("ui.phase5.whiteboardSaved");
    } catch (err) {
      state.error = err.message || L("ui.phase5.whiteboardFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function insertGifMessage(gif) {
    if (!state.selectedId || !gif) return;
    var url = gif.gif_url || gif.preview_url || "";
    if (!url) return;
    state.phase5StickersOpen = false;
    state.busy = true;
    render();
    try {
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: {
          type: "gif",
          content: url,
          reply_to_msg_id: currentReplyToId(),
        },
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("messages.sendFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function insertStickerMessage(pack) {
    if (!state.selectedId || !pack) return;
    var label = pack.name || pack.pack_id || L("ui.message.sticker");
    state.phase5StickersOpen = false;
    state.busy = true;
    render();
    try {
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: {
          type: "sticker",
          content: "[sticker:" + label + "]",
          reply_to_msg_id: currentReplyToId(),
        },
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("messages.sendFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function insertAiReplyToComposer() {
    if (!state.phase5AiReply) return;
    var reply = String(state.phase5AiReply);
    if (/gateway not configured|ai-chat-gateway preset/i.test(reply)) {
      state.error = L("ui.phase5.aiAssistUnavailable");
      state.phase5AiOpen = false;
      render();
      return;
    }
    var ta = document.getElementById("msgdraft");
    if (ta) {
      var prefix = ta.value && !ta.value.endsWith("\n") ? "\n" : "";
      ta.value = (ta.value || "") + prefix + state.phase5AiReply;
      scheduleSaveComposerDraft();
    }
    state.phase5AiOpen = false;
    render();
  }

  async function loadSipGateway() {
    if (!state.tokens) {
      state.sipGateway = null;
      return;
    }
    try {
      state.sipGateway = await apiJson("/platform/sip", { method: "GET" });
    } catch (e) {
      state.sipGateway = null;
    }
  }

  async function saveSipGateway(enabled, uri) {
    if (!state.tokens) return;
    state.busy = true;
    render();
    try {
      state.sipGateway = await apiJson("/platform/sip", {
        method: "PUT",
        jsonBody: {
          enabled: !!enabled,
          gateway_uri: uri || null,
          h323_enabled: false,
        },
      });
      state.phase5Toast = L("ui.phase5.sipSaved");
    } catch (err) {
      state.error = err.message || L("ui.phase5.sipFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function sendVideoNoteMessage(blob, durationMs) {
    if (!blob || !state.tokens || !state.selectedId) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var file = new File([blob], "video-note.webm", { type: blob.type || "video/webm" });
      var up = await uploadChatFile(file);
      var body = {
        type: "video_note",
        content: up.id,
        duration_ms: durationMs || null,
        reply_to_msg_id: currentReplyToId(),
      };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("ui.phase5.videoNoteFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function loadMyReminders() {
    if (!state.tokens) {
      state.myReminders = [];
      return;
    }
    state.myRemindersBusy = true;
    try {
      var rows = await apiJson("/me/reminders?limit=20", { method: "GET" });
      state.myReminders = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.myReminders = [];
    } finally {
      state.myRemindersBusy = false;
    }
  }

  async function loadMyScheduledMessages() {
    if (!state.tokens) {
      state.myScheduledMessages = [];
      return;
    }
    state.myScheduledMessagesBusy = true;
    try {
      var rows = await apiJson("/me/scheduled-messages?limit=20", { method: "GET" });
      state.myScheduledMessages = Array.isArray(rows) ? rows : [];
    } catch (e) {
      state.myScheduledMessages = [];
    } finally {
      state.myScheduledMessagesBusy = false;
    }
  }

  async function cancelMyReminder(reminderId) {
    if (!reminderId) return;
    state.busy = true;
    try {
      await apiJson("/me/reminders/" + encodeURIComponent(reminderId), { method: "DELETE" });
      state.statusMessage = L("ui.reminders.cancelled");
      await loadMyReminders();
    } catch (err) {
      state.error = err.message || L("ui.reminders.cancelFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function cancelScheduledMessage(messageId) {
    if (!messageId) return;
    state.busy = true;
    try {
      await apiJson("/me/scheduled-messages/" + encodeURIComponent(messageId), {
        method: "DELETE",
      });
      state.statusMessage = L("ui.schedule.cancel");
      await loadMyScheduledMessages();
    } catch (err) {
      state.error = err.message || L("ui.schedule.cancelFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function openMessageReminder(m) {
    if (!m || !m.id || !state.selectedId) return;
    state.reminderPick = { chatId: state.selectedId, messageId: m.id };
    render();
  }

  function closeMessageReminder() {
    state.reminderPick = null;
    render();
  }

  async function createMessageReminder(remindAtIso) {
    if (!state.reminderPick || !remindAtIso) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      await apiJson("/me/reminders", {
        method: "POST",
        jsonBody: {
          chat_id: state.reminderPick.chatId,
          message_id: state.reminderPick.messageId,
          remind_at: remindAtIso,
        },
      });
      state.reminderPick = null;
      state.statusMessage = L("ui.reminders.ok");
      await loadMyReminders();
    } catch (err) {
      state.error = err.message || L("ui.reminders.failed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function openContactShare() {
    state.contactShareOpen = true;
    if (!state.contacts && !state.contactsBusy) {
      loadContacts().then(render).catch(function () {
        render();
      });
    } else {
      render();
    }
  }

  function closeContactShare() {
    state.contactShareOpen = false;
    render();
  }

  async function sendContactMessage(payload) {
    if (!state.tokens || !state.selectedId || !payload) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var body = {
        type: "contact",
        content: JSON.stringify(payload),
        reply_to_msg_id: currentReplyToId(),
        client_msg_id: null,
        visibility_ttl_seconds: getComposerTtlSeconds(),
      };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      state.contactShareOpen = false;
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("ui.contactShare.failed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function sendContactCard(card) {
    sendContactMessage(card);
  }

  async function shareSelfContact() {
    var profile = await loadMyProfile();
    var payload = {
      display_name: state.myDisplayName || (profile && profile.username) || "",
      phone: state.myProfilePhone || (profile && profile.phone) || null,
      email: state.myProfileEmail || (profile && profile.email) || null,
      username: profile && profile.username ? profile.username : null,
    };
    sendContactCard(payload);
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
    state.phase5Modal = {
      mode: "edit",
      messageId: m.id,
      title: L("ui.edit.title"),
      body: m.content || "",
    };
    render();
  }

  async function saveEditedMessage(modal) {
    if (!state.selectedId || !modal || modal.mode !== "edit" || !modal.messageId) return;
    var text = (modal.body || "").trim();
    if (!text) return;
    state.busy = true;
    render();
    try {
      var updated = await apiJson(
        "/chats/" + state.selectedId + "/messages/" + modal.messageId,
        {
          method: "PATCH",
          jsonBody: { content: text },
        }
      );
      state.phase5Modal = null;
      if (updated && updated.id) {
        mergeMessageIntoThread(updated);
        if (isE2eeType(updated.type) && state.e2eePlaintextCache) {
          delete state.e2eePlaintextCache[updated.id];
        }
        syncPreviewIfLastMessage(state.selectedId, updated.id);
      } else {
        await loadThread(state.selectedId, THREAD_SOFT_RELOAD);
      }
    } catch (e) {
      state.error = e.message || L("ui.edit.failed");
    } finally {
      state.busy = false;
      render();
    }
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
      if (state.discussionThreadRootId) {
        q.set("thread_id", state.discussionThreadRootId);
      }
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
    if (!state.reactionsByMsg) {
      state.reactionsByMsg = {};
    }
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
    var rpRaw = data.reply_preview || data.replyPreview;
    var out = {
      id: data.messageId,
      chat_id: data.chatId,
      sender_id: data.senderId,
      type: data.type || "text",
      content: data.content || "",
      reply_to_msg_id: replyTo,
      deleted: false,
      created_at: data.createdAt
        ? new Date(instantEpochMs(data.createdAt)).toISOString()
        : new Date().toISOString(),
      edited_at: null,
      visibility_ttl_seconds:
        data.visibility_ttl_seconds != null
          ? data.visibility_ttl_seconds
          : data.visibilityTtlSeconds != null
            ? data.visibilityTtlSeconds
            : null,
      attachment_file_id: aid,
    };
    if (rpRaw) {
      out.reply_preview = {
        message_id: rpRaw.message_id || rpRaw.messageId || replyTo,
        sender_id: rpRaw.sender_id || rpRaw.senderId || null,
        snippet: rpRaw.snippet,
        deleted: !!rpRaw.deleted,
      };
    }
    return out;
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
        instantEpochMs(last.created_at),
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
      if (window.KorusOfflineCache) {
        window.KorusOfflineCache.appendMessage(chatId, full).catch(function () {});
      }
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
      if (sent.client_msg_id) {
        state.messages = uiUx.reconcileOptimisticSend(
          state.messages,
          sent.client_msg_id,
          sent,
          sortMessagesAsc
        );
      } else {
        mergeMessageIntoThread(sent);
      }
      if (window.KorusOfflineCache) {
        window.KorusOfflineCache.appendMessage(chatId, sent).catch(function () {});
      }
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
    updateAppBadge();
    scheduleRender();
  }

  function markMentionPending(chatId) {
    if (!chatId) return;
    if (!state.mentionPendingChats) state.mentionPendingChats = {};
    state.mentionPendingChats[chatId] = true;
    scheduleRender();
  }

  function clearMentionPending(chatId) {
    if (!chatId || !state.mentionPendingChats) return;
    delete state.mentionPendingChats[chatId];
  }

  function openIntegration(it) {
    if (!it || !it.launch_url) return;
    if (it.open_mode === "tab") {
      window.open(it.launch_url, "_blank", "noopener,noreferrer");
      return;
    }
    state.integrationPanel = {
      url: it.launch_url,
      label: it.label || it.bot_name || it.id,
    };
    render();
  }

  function closeIntegrationPanel() {
    state.integrationPanel = null;
    render();
  }

  function formatPreviewForMessage(m) {
    return uiMessagesUtils.formatPreviewForMessage(
      m,
      messageAttachmentKind,
      messageAttachmentFileId,
      formatPreviewText,
      L("ui.message.default")
    );
  }

  function replySnippetForId(msgId) {
    var p = findMessageInThread(msgId);
    if (!p) return L("ui.message.default");
    return formatPreviewForMessage(p);
  }

  function senderLabelForUserId(userId) {
    if (!userId) return "";
    var myId = state.tokens ? jwtSub(state.tokens.access_token) : null;
    return myId && userId === myId ? L("ui.thread.you") : userId.slice(0, 8);
  }

  function getMessageReplyCtx() {
    return {
      L: L,
      el: el,
      findMessageInThread: findMessageInThread,
      replySnippetForId: replySnippetForId,
      senderLabelForUserId: senderLabelForUserId,
      isE2eeType: isE2eeType,
      scrollToMessageId: function (msgId) {
        scrollToMessageId(msgId).catch(function () {});
      },
    };
  }

  function appendReplyQuoteBlock(art, m) {
    uiMessageReply.appendReplyQuoteBlock(art, m, getMessageReplyCtx());
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
      state.virtualFocusMessageId = msgId;
      render();
      uiMessageReply.highlightMessageElement(msgId);
      return;
    }
    var pages = 0;
    while (state.threadHasMore && pages < 20) {
      pages++;
      await loadOlderMessages();
      if (findMessageInThread(msgId)) {
        state.virtualFocusMessageId = msgId;
        render();
        uiMessageReply.highlightMessageElement(msgId);
        return;
      }
    }
    if (await ensureMessageInThread(msgId)) {
      state.virtualFocusMessageId = msgId;
      render();
      uiMessageReply.highlightMessageElement(msgId);
      return;
    }
    throw new Error(L("messages.notFoundInHistory"));
  }

  function setReplyTo(m) {
    if (!m || !m.id) return;
    state.replyTo = {
      id: m.id,
      snippet: formatPreviewForMessage(m),
      senderLabel: senderLabelForUserId(m.sender_id),
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
        : m.visibilityTtlSeconds != null
          ? m.visibilityTtlSeconds
          : null;
    if (raw == null) return null;
    var parsed = parseInt(raw, 10);
    return parsed > 0 ? parsed : null;
  }

  function messageExpiryEpochMs(m) {
    var ttl = messageVisibilityTtlSeconds(m);
    if (!ttl || !m || !m.created_at) return null;
    var created = instantEpochMs(m.created_at);
    if (!created || isNaN(created)) return null;
    return created + ttl * 1000;
  }

  function getComposerTtlSeconds() {
    var v = state.composerTtl;
    if (!v) return null;
    var n = parseInt(v, 10);
    return n > 0 ? n : null;
  }

  function effectiveMessageSearchScope() {
    if (state.messageSearchScope === "global") return "global";
    if (state.messageSearchScope === "chat") return "chat";
    if (
      state.selectedId &&
      state.selectedId !== state.savedChatId &&
      state.uiPaneFocus === "thread"
    ) {
      return "chat";
    }
    return "global";
  }

  function messageSearchScopeLabel() {
    var scope = effectiveMessageSearchScope();
    if (state.messageSearchScope === "auto") {
      return L(scope === "chat" ? "ui.search.scopeAutoChat" : "ui.search.scopeAutoGlobal");
    }
    return L(scope === "chat" ? "ui.search.scopeChat" : "ui.search.scopeGlobal");
  }

  function messageSearchPlaceholder() {
    return effectiveMessageSearchScope() === "chat"
      ? L("ui.search.chatPlaceholder")
      : L("ui.search.globalPlaceholder");
  }

  function setUiPaneFocus(pane) {
    if (state.uiPaneFocus === pane) return;
    state.uiPaneFocus = pane;
    if (state.messageSearchScope === "auto" && (state.messageSearch || "").trim().length >= 2) {
      scheduleMessageSearch();
    }
    render();
  }

  function cycleMessageSearchScope() {
    if (!state.selectedId || state.selectedId === state.savedChatId) {
      state.messageSearchScope = "global";
      render();
      return;
    }
    var order = ["auto", "chat", "global"];
    var idx = order.indexOf(state.messageSearchScope);
    if (idx < 0) idx = 0;
    state.messageSearchScope = order[(idx + 1) % order.length];
    scheduleMessageSearch();
    render();
  }

  function appendMessageSearchBar(parent, opts) {
    opts = opts || {};
    var wrap = el(
      "div",
      "global-search-wrap" + (opts.mobile ? " thread-message-search" : "")
    );
    var row = el("div", "message-search-row");
    var inp = document.createElement("input");
    inp.type = "search";
    inp.className = "global-search-input";
    inp.setAttribute("data-testid", opts.testId || "message-search-input");
    inp.placeholder = messageSearchPlaceholder();
    inp.value = state.messageSearch;
    inp.oninput = function () {
      state.messageSearch = inp.value;
      scheduleMessageSearch();
      render();
    };
    inp.onfocus = function () {
      if (opts.paneFocus) state.uiPaneFocus = opts.paneFocus;
    };
    row.appendChild(inp);
    if (state.selectedId && state.selectedId !== state.savedChatId) {
      var scopeBtn = el("button", "message-search-scope");
      scopeBtn.type = "button";
      scopeBtn.setAttribute("data-testid", "message-search-scope");
      scopeBtn.title = L("ui.search.scopeCycleTitle");
      scopeBtn.textContent = messageSearchScopeLabel();
      scopeBtn.onclick = function (e) {
        e.preventDefault();
        cycleMessageSearchScope();
      };
      row.appendChild(scopeBtn);
    }
    wrap.appendChild(row);
    parent.appendChild(wrap);
  }

  function scheduleMessageSearch() {
    if (messageSearchTimer) clearTimeout(messageSearchTimer);
    var q = (state.messageSearch || "").trim();
    if (q.length < 2) {
      state.messageSearchHits = null;
      state.messageSearchBusy = false;
      return;
    }
    state.messageSearchBusy = true;
    messageSearchTimer = setTimeout(function () {
      messageSearchTimer = null;
      var query = state.messageSearch.trim();
      if (query.length < 2) {
        state.messageSearchHits = null;
        state.messageSearchBusy = false;
        render();
        return;
      }
      apiJson("/search/messages?q=" + encodeURIComponent(query) + "&limit=40", {
        method: "GET",
      })
        .then(function (hits) {
          if (state.messageSearch.trim() !== query) return;
          var rows = Array.isArray(hits) ? hits : [];
          if (effectiveMessageSearchScope() === "chat" && state.selectedId) {
            rows = rows.filter(function (h) {
              return h.chat_id === state.selectedId;
            });
          }
          state.messageSearchHits = rows;
          state.messageSearchBusy = false;
          render();
        })
        .catch(function () {
          if (state.messageSearch.trim() !== query) return;
          state.messageSearchHits = [];
          state.messageSearchBusy = false;
          render();
        });
    }, 350);
  }

  async function openSearchHit(hit) {
    if (!hit || !hit.id || !state.selectedId) return;
    if (hit.chat_id !== state.selectedId) return;
    state.messageSearch = "";
    state.messageSearchHits = null;
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
    scheduleMessageSearch();
  }

  async function openGlobalSearchHit(hit) {
    if (!hit || !hit.id || !hit.chat_id) return;
    state.messageSearch = "";
    state.messageSearchHits = null;
    closeForwardPicker();
    await openChatById(hit.chat_id);
    await openSearchHit(hit);
  }

  async function uploadChatFile(file) {
    var max = state.mediaCaps && state.mediaCaps.max_upload_bytes;
    if (max && file.size > max) {
      throw new Error(L("files.tooLarge", { mb: Math.round(max / (1024 * 1024)) }));
    }
    state.uploadProgress = 0;
    render();
    try {
      var parsed = await uiUx.uploadFileWithProgress({
        url: "/api/v1/files/upload",
        file: file,
        getAccessToken: function () {
          return state.tokens && state.tokens.access_token;
        },
        onProgress: function (pct) {
          state.uploadProgress = pct;
          scheduleRender();
        },
      });
      return parsed;
    } finally {
      state.uploadProgress = null;
    }
  }

  function getFileAttachCtx() {
    return {
      L: L,
      state: state,
      render: render,
      apiFetch: apiFetch,
      apiJson: apiJson,
      openChatById: openChatById,
      scrollToMessageId: scrollToMessageId,
    };
  }

  async function fetchFileMetadata(fileId) {
    if (!fileId || !state.tokens) return null;
    return uiFileAttach.fetchFileMetadata(fileId, getFileAttachCtx());
  }

  async function attachAuthenticatedImage(fileId, imgEl) {
    return uiFileAttach.attachAuthenticatedImage(fileId, imgEl, getFileAttachCtx());
  }

  async function attachAuthenticatedAudio(fileId, audioEl) {
    return uiFileAttach.attachAuthenticatedAudio(fileId, audioEl, getFileAttachCtx());
  }

  async function openChatMessageForFile(fileId) {
    return uiFileAttach.openChatMessageForFile(fileId, getFileAttachCtx());
  }

  async function downloadChatFile(fileId) {
    return uiFileAttach.downloadChatFile(fileId, getFileAttachCtx());
  }

  function renderMessageContent(bodyEl, m) {
    uiMessageContent.renderMessageContent(bodyEl, m, getMessageContentCtx());
  }

  function getWsHandlerCtx() {
    return {
      state: state,
      L: L,
      jwtSub: jwtSub,
      sendHeartbeatThrottled: sendHeartbeatThrottled,
      handleRtcEnvelope: handleRtcEnvelope,
      isTypingEvent: isTypingEvent,
      noteTyping: noteTyping,
      scheduleTypingSidebarRefresh: scheduleTypingSidebarRefresh,
      isPresenceEvent: isPresenceEvent,
      applyPresenceEvent: applyPresenceEvent,
      isAvatarEvent: isAvatarEvent,
      applyAvatarEvent: applyAvatarEvent,
      isChatAvatarEvent: isChatAvatarEvent,
      applyChatAvatarEvent: applyChatAvatarEvent,
      isReadReceiptEvent: isReadReceiptEvent,
      applyReadReceiptEvent: applyReadReceiptEvent,
      isMessageChangeEvent: isMessageChangeEvent,
      applyMessageChangeEvent: applyMessageChangeEvent,
      isReactionChangeEvent: isReactionChangeEvent,
      applyReactionChangeEvent: applyReactionChangeEvent,
      isPinChangeEvent: isPinChangeEvent,
      applyPinChangeEvent: applyPinChangeEvent,
      isMentionEvent: isMentionEvent,
      maybeNotifyMention: maybeNotifyMention,
      markMentionPending: markMentionPending,
      bumpUnread: bumpUnread,
      scheduleRender: scheduleRender,
      isConferenceChangeEvent: isConferenceChangeEvent,
      applyConferenceChangeEvent: applyConferenceChangeEvent,
      isMessageSendEvent: isMessageSendEvent,
      setChatPreviewFromSendEvent: setChatPreviewFromSendEvent,
      maybeNotifyMessage: maybeNotifyMessage,
      ingestIncomingMessage: ingestIncomingMessage,
      markChatRead: markChatRead,
      loadThread: loadThread,
      THREAD_SOFT_RELOAD: THREAD_SOFT_RELOAD,
    };
  }

  function handleWsIncoming(ev) {
    uiWsHandler.handleWsIncoming(ev, getWsHandlerCtx());
  }

  var wsClient = window.KorusUiWsClient
    ? KorusUiWsClient.createWsClient({
        getState: function () {
          return state;
        },
        getAccessToken: function () {
          return state.tokens && state.tokens.access_token;
        },
        hasSession: function () {
          return state.tokens && state.tokens.access_token;
        },
        buildWsUrl: function (token) {
          return uiTransportUtils.buildWsUrl(wsBaseUrl(), token);
        },
        onOpen: function () {
          sendHeartbeat();
        },
        onStateChange: function () {
          render();
        },
        onBeforeClose: function () {
          rtcHangupAll();
        },
        onMessage: handleWsIncoming,
      })
    : null;

  function clearWsReconnect() {
    if (wsClient) {
      wsClient.clearReconnect();
    }
  }

  function closeWs() {
    if (wsClient) {
      wsClient.close();
    }
  }

  function openWs(opts) {
    if (wsClient) {
      wsClient.open(opts);
    }
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
      if (opts && opts.cls) btn.className += " " + opts.cls;
      return btn;
    }
    return ib(
      Object.assign(
        { icon: icon, tip: tip, sm: opts && opts.sm !== false },
        opts || {}
      )
    );
  }

  function wsStatusLabel() {
    if (state.wsState === "open") return L("ws.online");
    if (state.wsState === "connecting") return L("ws.connecting");
    if (state.wsState === "reconnecting") return L("ws.reconnecting");
    if (state.wsState === "error") return L("ws.error");
    return L("ws.offline");
  }

  function shellStatusIcon(icon, tip, cls) {
    var span = el("span", "status-icon " + (cls || ""));
    span.setAttribute("role", "status");
    span.title = tip;
    span.setAttribute("aria-label", tip);
    if (icon === "dot") {
      span.appendChild(el("span", "status-dot"));
    } else {
      span.textContent = icon;
    }
    return span;
  }

  function shellWsStatusIcon() {
    var connected = state.wsState === "open";
    var pending = state.wsState === "connecting";
    var cls = "ws-status";
    if (connected) cls += " connected";
    else if (pending) cls += " pending";
    else if (state.wsState === "error") cls += " error";
    else cls += " disconnected";
    var tip = L("ws.statusTip", { state: wsStatusLabel(), url: wsBaseUrl() });
    var span = shellStatusIcon("dot", tip, cls);
    span.setAttribute("data-testid", "ws-status");
    span.style.cursor = connected ? "default" : "pointer";
    if (!connected) {
      span.title = tip + " " + L("ws.clickReconnect");
      span.setAttribute("aria-label", span.title);
      span.onclick = function () {
        if (state.wsState === "open") return;
        if (wsClient) {
          wsClient.reconnectNow();
        } else {
          clearWsReconnect();
          state.wsReconnectAttempt = 0;
          openWs({ force: true });
        }
      };
    }
    return span;
  }

  function shellE2eeStatusIcon(count) {
    var tip = L("ui.shell.e2eeStatusTip", { count: count });
    var cls = "e2ee-status hdr-btn-optional";
    if (!count) cls += " count-zero";
    var span = shellStatusIcon("🔐", tip, cls);
    span.setAttribute("data-testid", "e2ee-status");
    if (count > 0) {
      var badge = el("span", "status-badge");
      badge.textContent = count > 99 ? "99+" : String(count);
      span.appendChild(badge);
    }
    return span;
  }

  function chatActivityMs(c) {
    var prev = state.chatPreview[c.id];
    if (prev && prev.at) return prev.at;
    if (c.created_at) return instantEpochMs(c.created_at);
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

  var SIDEBAR_FOLDER_I18N = {
    all: "ui.sidebar.folderAll",
    work: "ui.sidebar.folderWorkChip",
    archive: "ui.sidebar.folderArchive",
  };

  var SIDEBAR_FOLDER_ICONS = {
    all: "📋",
    work: "💼",
    personal: "👤",
    archive: "🗄",
  };

  function sidebarFolderLabel(fid) {
    var nestedKey = "ui.sidebar.folder." + fid;
    var nested = L(nestedKey);
    if (nested !== nestedKey) return nested;
    var flatKey = SIDEBAR_FOLDER_I18N[fid];
    if (flatKey) {
      var flat = L(flatKey);
      if (flat !== flatKey) return flat;
    }
    return fid;
  }

  function filteredChats() {
    var base = state.chats.filter(function (c) {
      return c.type !== "saved";
    });
    if (state.sidebarFolder === "archive") {
      base = base.filter(function (c) {
        return c.archived;
      });
    } else {
      base = base.filter(function (c) {
        return !c.archived;
      });
      if (state.sidebarFolder === "work") {
        base = base.filter(function (c) {
          return c.folder_tag === "work";
        });
      } else if (state.sidebarFolder === "personal") {
        base = base.filter(function (c) {
          return c.folder_tag === "personal";
        });
      }
    }
    var q = (state.sidebarSearch || "").trim().toLowerCase();
    var list = q
      ? base.filter(function (c) {
          var t = (c.title || c.id || "").toLowerCase();
          return t.indexOf(q) !== -1;
        })
      : base;
    if (state.sidebarChatFilter === "unread") {
      list = list.filter(function (c) {
        return (state.unreadByChat[c.id] || 0) > 0;
      });
    } else if (state.sidebarChatFilter === "mentions") {
      list = list.filter(function (c) {
        return state.mentionPendingChats && state.mentionPendingChats[c.id];
      });
    }
    return list.slice().sort(compareChatsForSidebar);
  }

  function chatInitial(title) {
    var t = (title || "?").trim();
    return t.charAt(0).toUpperCase();
  }

  function rememberUserAvatar(userId, url, title) {
    if (!userId) return;
    if (!state.avatarByUserId) state.avatarByUserId = {};
    var prev = state.avatarByUserId[userId] || {};
    state.avatarByUserId[userId] = {
      url: url != null && url !== "" ? url : prev.url || null,
      title: title != null && title !== "" ? title : prev.title || null,
    };
  }

  function ingestAvatarRecords(records, idKey) {
    if (!Array.isArray(records)) return;
    idKey = idKey || "user_id";
    records.forEach(function (r) {
      if (!r) return;
      var id = r[idKey] || r.user_id || r.id;
      if (!id) return;
      rememberUserAvatar(id, r.avatar_url, r.display_name || r.username);
    });
  }

  function avatarUrlForUser(userId) {
    if (!userId || !state.avatarByUserId) return null;
    var entry = state.avatarByUserId[userId];
    return entry && entry.url ? entry.url : null;
  }

  function avatarTitleForUser(userId) {
    if (!userId || !state.avatarByUserId) return userId ? userId.slice(0, 8) : "?";
    var entry = state.avatarByUserId[userId];
    return (entry && entry.title) || (userId ? userId.slice(0, 8) : "?");
  }

  function applyMyProfileAvatar(p) {
    if (!p) return;
    state.myAvatarUrl = p.avatar_url || null;
    state.myAvatarFileId = p.avatar_file_id || null;
    state.myAvatarHidden = !!p.avatar_hidden;
    var meId = state.tokens ? jwtSub(state.tokens.access_token) : null;
    if (meId) {
      rememberUserAvatar(meId, p.avatar_url, p.display_name || p.username);
    }
  }

  function chatAvatarUrl(chat) {
    if (!chat) return null;
    if (state.displayAvatarByChatId && state.displayAvatarByChatId[chat.id]) {
      return state.displayAvatarByChatId[chat.id];
    }
    return chat.display_avatar_url || chat.avatar_url || null;
  }

  function renderAvatarNode(opts) {
    var alt =
      opts.alt != null
        ? opts.alt
        : L("ui.avatar.altUser", { name: opts.title || "?" });
    return uiAvatar.renderAvatar({
      url: opts.url,
      title: opts.title,
      userId: opts.userId,
      size: opts.size,
      testId: opts.testId,
      alt: alt,
    });
  }

  function notificationIconUrl(chatId, senderId) {
    if (chatId) {
      var chat = state.chats.find(function (c) {
        return c.id === chatId;
      });
      var curl = chatAvatarUrl(chat);
      if (curl) return curl;
    }
    if (senderId) {
      var uurl = avatarUrlForUser(senderId);
      if (uurl) return uurl;
    }
    return "/webui/favicon.ico";
  }

  function markBrandNoTranslate(node) {
    if (node) node.setAttribute("translate", "no");
  }

  function brandTagline() {
    return L("ui.brand.tagline");
  }

  function uiLabelFallback(key, ruFallback, fallback) {
    var translated = L(key);
    if (translated && translated !== key) return translated;
    var locale = i18n && i18n.getLocale ? i18n.getLocale() : "ru";
    return locale === "ru" ? ruFallback : fallback;
  }

  function mountSidebarFiltersPanel() {
    var panel = el("div", "sidebar-filters-panel");
    panel.setAttribute("data-testid", "sidebar-filters-panel");
    panel.setAttribute("aria-label", L("ui.sidebar.filterPanelTitle"));

    if (isPlatformFeatureVisible("chat.folders")) {
      var folderSec = el("div", "sidebar-filters-section");
      folderSec.appendChild(el("div", "sidebar-filters-label", L("ui.sidebar.filterPanelFolders")));
      var folderRow = el("div", "sidebar-filters-row sidebar-filters-row-4");
      ["all", "work", "personal", "archive"].forEach(function (fid) {
        folderRow.appendChild(
          sidebarChipButton(
            SIDEBAR_FOLDER_ICONS[fid],
            sidebarFolderLabel(fid),
            state.sidebarFolder === fid,
            "sidebar-folder-" + fid,
            "sidebar-folder-chip",
            function () {
              state.sidebarFolder = fid;
              render();
            }
          )
        );
      });
      folderSec.appendChild(folderRow);
      panel.appendChild(folderSec);
    }

    var showSec = el("div", "sidebar-filters-section");
    showSec.appendChild(el("div", "sidebar-filters-label", L("ui.sidebar.filterPanelShow")));
    var showRow = el("div", "sidebar-filters-row sidebar-filters-row-3");
    var filterIcons = { all: "≡", unread: "●", mentions: "@" };
    ["all", "unread", "mentions"].forEach(function (fid) {
      showRow.appendChild(
        sidebarChipButton(
          filterIcons[fid] || "·",
          sidebarFilterLabel(fid),
          state.sidebarChatFilter === fid,
          "sidebar-filter-" + fid,
          "sidebar-filter-chip",
          function () {
            state.sidebarChatFilter = fid;
            render();
          }
        )
      );
    });
    showSec.appendChild(showRow);
    panel.appendChild(showSec);

    var actRow = el("div", "sidebar-filters-actions");
    actRow.appendChild(
      sidebarChipButton("🔒", L("ui.sidebar.vaultTitle"), false, "sidebar-vault", "", function () {
        openSavedVault();
      })
    );
    actRow.appendChild(
      sidebarChipButton("✓", L("ui.sidebar.readAllTitle"), false, "sidebar-read-all", "", function () {
        markAllChatsRead();
      })
    );
    panel.appendChild(actRow);
    return panel;
  }

  function sidebarIconChip(icon, title, active, testId, onClick) {
    var extra =
      testId && testId.indexOf("sidebar-folder-") === 0
        ? " sidebar-folder-icon"
        : testId && testId.indexOf("sidebar-filter-") === 0
          ? " sidebar-filter-icon"
          : "";
    var btn = el("button", "sidebar-icon-chip" + extra + (active ? " active" : ""));
    btn.type = "button";
    btn.title = title;
    btn.setAttribute("aria-label", title);
    btn.setAttribute("data-testid", testId);
    btn.textContent = icon;
    btn.onclick = onClick;
    return btn;
  }

  function sidebarTabButton(icon, labelKey, ruFallback, enFallback, active, testId, onClick) {
    var btn = el("button", "sidebar-tab" + (active ? " active" : ""));
    btn.type = "button";
    var label = uiLabelFallback(labelKey, ruFallback, enFallback);
    btn.title = label;
    btn.setAttribute("aria-label", label);
    btn.setAttribute("data-testid", testId);
    btn.appendChild(el("span", "sidebar-tab-icon", icon));
    btn.appendChild(el("span", "sidebar-tab-label", label));
    btn.onclick = onClick;
    return btn;
  }

  function sidebarChipButton(icon, label, active, testId, extraCls, onClick) {
    var btn = el(
      "button",
      "sidebar-chip" + (active ? " active" : "") + (extraCls ? " " + extraCls : "")
    );
    btn.type = "button";
    btn.title = label;
    btn.setAttribute("aria-label", label);
    btn.setAttribute("data-testid", testId);
    btn.appendChild(el("span", "sidebar-chip-icon", icon));
    btn.appendChild(el("span", "sidebar-chip-label", label));
    btn.onclick = onClick;
    return btn;
  }

  function modalCardHead(titleText, closeBtn) {
    var head = el("div", "settings-head");
    head.appendChild(el("h2", "settings-title", titleText));
    head.appendChild(closeBtn);
    return head;
  }

  function memberRoleLabel(role) {
    var key = "ui.members.role." + role;
    var translated = L(key);
    if (translated && translated !== key) return translated;
    return role;
  }

  function readReceiptUserLabel(userId) {
    if (!userId) return "?";
    if (state.chatMembers && state.chatMembers.length) {
      for (var i = 0; i < state.chatMembers.length; i++) {
        var m = state.chatMembers[i];
        if (m && m.user_id === userId) {
          return m.display_name || m.username || userId.slice(0, 8);
        }
      }
    }
    return userId.slice(0, 8);
  }

  function closeReadReceiptPopup() {
    state.readReceiptPopupMessageId = null;
    render();
  }

  function appendAppTitle(parent) {
    var row = el("div", "app-title-row");
    row.appendChild(el("div", "app-title-logo", "K"));
    var h1 = el("h1", null, L("ui.brand.title"));
    markBrandNoTranslate(h1);
    row.appendChild(h1);
    parent.appendChild(row);
  }

  function orgSlugFromUrl() {
    try {
      var params = new URLSearchParams(window.location.search);
      return params.get("org_slug");
    } catch (e) {
      return null;
    }
  }

  function authPasswordAllowed() {
    if (!state.loginOptions || !state.loginOptions.methods) {
      return true;
    }
    return state.loginOptions.methods.some(function (m) {
      return m && m.type === "password";
    });
  }

  function authRegistrationAllowed() {
    if (!state.loginOptions) {
      return true;
    }
    return !!state.loginOptions.registration_allowed;
  }

  async function fetchLoginOptions() {
    try {
      var slug = orgSlugFromUrl();
      var q = slug ? "?org_slug=" + encodeURIComponent(slug) : "";
      state.loginOptions = await apiJson("/auth/login-options" + q, {
        noAuth: true,
        noRefresh: true,
      });
    } catch (e) {
      state.loginOptions = null;
    }
  }

  function renderAuth() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el("div", "auth-shell");
    shell.appendChild(el("div", "auth-shell-glow auth-shell-glow-a"));
    shell.appendChild(el("div", "auth-shell-glow auth-shell-glow-b"));
    var card = el("div", "auth-card");
    var head = el("div", "auth-card-head");
    var brandRow = el("div", "auth-card-brand");
    var brandLogo = el("div", "auth-brand-logo", "K");
    markBrandNoTranslate(brandLogo);
    brandRow.appendChild(brandLogo);
    var brandText = el("div", "auth-card-brand-text");
    var brandTitle = el("div", "auth-card-brand-title", L("ui.brand.title"));
    markBrandNoTranslate(brandTitle);
    brandText.appendChild(brandTitle);
    brandText.appendChild(el("p", "auth-card-brand-tag", brandTagline()));
    brandRow.appendChild(brandText);
    head.appendChild(brandRow);
    card.appendChild(head);
    var authContent = el("div", "auth-card-content");
    var tabs = el("div", "auth-tabs");
    var showRegister = authRegistrationAllowed();
    var tLogin = el("button", "auth-tab" + (state.authTab === "login" ? " active" : ""), L("auth.login"));
    tLogin.type = "button";
    tLogin.setAttribute("data-testid", "auth-tab-login");
    tLogin.onclick = function () {
      state.authTab = "login";
      state.error = null;
      render();
    };
    tabs.appendChild(tLogin);
    if (showRegister) {
      var tReg = el("button", "auth-tab" + (state.authTab === "register" ? " active" : ""), L("auth.register"));
      tReg.type = "button";
      tReg.setAttribute("data-testid", "auth-tab-register");
      tReg.onclick = function () {
        state.authTab = "register";
        state.error = null;
        render();
      };
      tabs.appendChild(tReg);
    } else if (state.authTab === "register") {
      state.authTab = "login";
    }
    authContent.appendChild(tabs);
    if (state.loginOptions && state.loginOptions.methods) {
      var ssoWrap = el("div", "auth-sso-list");
      state.loginOptions.methods.forEach(function (m) {
        if (!m || m.type === "password" || !m.authorization_url) return;
        var btn = el("button", "btn btn-secondary auth-sso-btn", m.label || m.id);
        btn.type = "button";
        btn.setAttribute("data-testid", "auth-sso-" + m.id);
        btn.onclick = function () {
          window.location.href = m.authorization_url;
        };
        ssoWrap.appendChild(btn);
      });
      if (ssoWrap.childNodes.length) {
        authContent.appendChild(ssoWrap);
      }
    }
    var showPasswordForm =
      (state.authTab === "login" && authPasswordAllowed()) ||
      (state.authTab === "register" && showRegister);
    if (showPasswordForm) {
    var form = el("form", "auth-form");
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
      "btn btn-primary auth-submit",
      state.busy
        ? "…"
        : state.authTab === "login"
          ? L("auth.loginSubmit")
          : L("auth.registerSubmit")
    );
    submit.type = "submit";
    submit.disabled = state.busy;
    submit.setAttribute("data-testid", "auth-submit");
    form.appendChild(submit);
    authContent.appendChild(form);
    }
    card.appendChild(authContent);
    card.appendChild(el("p", "auth-foot", L("auth.hint")));
    shell.appendChild(card);
    mountAppNotice(shell);
    root.appendChild(shell);
  }

  function field(id, label, type, auto, required, minL, maxL) {
    var wrap = el("div", "field auth-field");
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
    stopTtlRenderTicker();
    clearDeferredUiTimers();
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
    state.platformCaps = null;
    state.unreadByChat = {};
    state.userSearchHits = null;
    state.userSearchBusy = false;
    state.chatPreview = {};
    state.typingExpireByChat = {};
    state.replyTo = null;
    state.reactionsByMsg = {};
    state.pinnedMessages = [];
    state.threadHasMore = false;
    state.threadLoading = false;
    state.threadLoadingMore = false;
    state.chatsLoading = false;
    state.uploadProgress = null;
    state.messageSearch = "";
    state.messageSearchHits = null;
    state.composerTtl = "";
    state.forwardPick = null;
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
    state.chatHeaderMembers = null;
    state.chatHeaderMembersChatId = null;
    state.callMode = "jitsi";
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
    uiComposer.wrapComposerSelection(before, after);
  }

  var REACTION_PICKER_EMOJIS = ["👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉", "👀", "✅"];

  async function sendVoiceMessage(blob, durationMs) {
    if (!blob || !state.tokens || !state.selectedId) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var file = new File([blob], "voice.webm", { type: blob.type || "audio/webm" });
      var up = await uploadChatFile(file);
      var body = {
        type: "voice",
        content: up.id,
        duration_ms: durationMs || null,
        reply_to_msg_id: currentReplyToId(),
        client_msg_id: null,
        visibility_ttl_seconds: getComposerTtlSeconds(),
      };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.error = err.message || L("messages.sendVoiceFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  async function sendFileMessage(file) {
    if (!file || !state.tokens || !state.selectedId) return;
    state.busy = true;
    state.error = null;
    render();
    var clientMsgId = uiUx.newClientMsgId();
    try {
      var up = await uploadChatFile(file);
      var msgType = messageTypeForMime((up && up.mime_type) || file.type);
      var myId = jwtSub(state.tokens.access_token);
      var optimistic = uiUx.buildOptimisticMessage({
        clientMsgId: clientMsgId,
        chatId: state.selectedId,
        senderId: myId,
        type: msgType,
        content: up.id,
        replyToMsgId: currentReplyToId(),
        attachmentFileId: up.id,
      });
      state.messages = sortMessagesAsc(state.messages.concat([optimistic]));
      state.shouldScrollThread = true;
      render();
      var body = {
          type: msgType,
          content: up.id,
          reply_to_msg_id: currentReplyToId(),
          client_msg_id: clientMsgId,
          visibility_ttl_seconds: getComposerTtlSeconds(),
        };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
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
      state.messages = uiUx.reconcileOptimisticSend(
        state.messages,
        clientMsgId,
        sent,
        sortMessagesAsc
      );
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.messages = uiUx.reconcileOptimisticSend(
        state.messages,
        clientMsgId,
        null,
        sortMessagesAsc
      );
      state.error = err.message || L("messages.sendFileFailed");
    } finally {
      state.uploadProgress = null;
      state.busy = false;
      render();
    }
  }

  async function sendMessage() {
    var ta = document.getElementById("msgdraft");
    if (!ta || !state.tokens || !state.selectedId) return;
    var text = ta.value.trim();
    if (!text) return;
    var clientMsgId = uiUx.newClientMsgId();
    var myId = jwtSub(state.tokens.access_token);
    var optimistic = uiUx.buildOptimisticMessage({
      clientMsgId: clientMsgId,
      chatId: state.selectedId,
      senderId: myId,
      type: "text",
      content: text,
      replyToMsgId: currentReplyToId(),
    });
    state.messages = sortMessagesAsc(state.messages.concat([optimistic]));
    state.shouldScrollThread = true;
    ta.value = "";
    clearComposerDraftForChat(state.selectedId);
    state.busy = true;
    state.error = null;
    render();
    try {
      var body = {
          type: "text",
          content: text,
          reply_to_msg_id: currentReplyToId(),
          client_msg_id: clientMsgId,
          visibility_ttl_seconds: getComposerTtlSeconds(),
        };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
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
      clearReplyTo();
      state.messages = uiUx.reconcileOptimisticSend(
        state.messages,
        clientMsgId,
        sent,
        sortMessagesAsc
      );
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      state.messages = uiUx.reconcileOptimisticSend(
        state.messages,
        clientMsgId,
        null,
        sortMessagesAsc
      );
      state.error = err.message || L("messages.sendFailed");
    } finally {
      state.busy = false;
      render();
    }
  }

  function callWatermarkText() {
    var env = window.__WEB_CLIENT__ || {};
    if (env.watermarkText) return String(env.watermarkText);
    return state.myDisplayName || jwtSub(state.tokens && state.tokens.access_token) || "";
  }

  async function sendLocationMessage() {
    if (!state.tokens || !state.selectedId || !navigator.geolocation) return;
    state.busy = true;
    state.error = null;
    render();
    try {
      var pos = await new Promise(function (resolve, reject) {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 60000,
        });
      });
      var payload = JSON.stringify({
        lat: pos.coords.latitude,
        lon: pos.coords.longitude,
        label: L("ui.message.locationLabel"),
      });
      var body = {
        type: "location",
        content: payload,
        reply_to_msg_id: currentReplyToId(),
        client_msg_id: null,
        visibility_ttl_seconds: getComposerTtlSeconds(),
      };
      if (state.discussionThreadRootId) {
        body.thread_id = state.discussionThreadRootId;
      }
      var sent = await apiJson("/chats/" + state.selectedId + "/messages", {
        method: "POST",
        jsonBody: body,
      });
      clearReplyTo();
      await afterLocalSend(state.selectedId, sent);
    } catch (err) {
      if (err && err.code === 1) {
        state.error = L("messages.locationDenied");
      } else {
        state.error = (err && err.message) || L("messages.sendLocationFailed");
      }
    } finally {
      state.busy = false;
      render();
    }
  }

  function safeConferenceDisplayTitle(c) {
    if (!c) return "";
    var t = c.title;
    if (typeof t === "string" && t.trim()) return t.trim();
    if (c.room_slug) return String(c.room_slug);
    if (c.conference_id) return String(c.conference_id).slice(0, 8);
    return "";
  }

  function sidebarFilterLabel(fid) {
    var key = "ui.sidebar.filter." + fid;
    var t = L(key);
    return t !== key ? t : fid;
  }

  function appendMinimalCallPanel(shell) {
    if (!state.callPanelOpen) return;
    var panel = el("aside", "call-panel");
    var ph = el("div", "call-panel-head");
    var titleSpan = el("span", "call-panel-title", L("ui.call.title"));
    titleSpan.setAttribute("data-testid", "call-panel-title");
    ph.appendChild(titleSpan);
    panel.appendChild(ph);
    shell.appendChild(panel);
  }

  function renderIntegrationPanel(shell) {
    if (!state.integrationPanel) return;
    var panel = el("aside", "integration-panel");
    panel.setAttribute("data-testid", "integration-panel");
    var ph = el("div", "integration-panel-head");
    var titleSpan = el("span", "integration-panel-title", state.integrationPanel.label || L("ui.sidebar.integrations"));
    titleSpan.setAttribute("data-testid", "integration-panel-title");
    ph.appendChild(titleSpan);
    ph.appendChild(
      iconBtn("✕", L("ui.integration.close"), {
        testId: "integration-panel-close",
        onClick: function () {
          closeIntegrationPanel();
        },
      })
    );
    panel.appendChild(ph);
    var panelContent = el("div", "integration-panel-content");
    var frameWrap = el("div", "integration-panel-frame");
    var iframe = document.createElement("iframe");
    iframe.className = "integration-panel-iframe";
    iframe.title = state.integrationPanel.label || L("ui.sidebar.integrations");
    iframe.setAttribute("data-testid", "integration-panel-iframe");
    iframe.setAttribute(
      "sandbox",
      "allow-scripts allow-same-origin allow-forms allow-popups"
    );
    iframe.referrerPolicy = "no-referrer-when-downgrade";
    iframe.src = state.integrationPanel.url;
    frameWrap.appendChild(iframe);
    panelContent.appendChild(frameWrap);
    panel.appendChild(panelContent);
    shell.appendChild(panel);
  }

  function renderCallPanel(shell) {
    if (!state.callPanelOpen) return;
    state._callMeshLabels = {
      speaking: L("ui.call.badgeSpeaking"),
      sharing: L("ui.call.badgeSharing"),
    };
    var panel = el("aside", "call-panel");
    var ph = el("div", "call-panel-head");
    var callTitleKey =
      state.callMode === "mesh" && state.callMediaMode === "audio" && !state.callCamOn
        ? "ui.call.titleAudio"
        : "ui.call.title";
    var titleSpan = el("span", "call-panel-title", L(callTitleKey));
    titleSpan.setAttribute(
      "data-testid",
      state.callMode === "mesh" && state.callMediaMode === "audio" && !state.callCamOn
        ? "call-panel-title-audio"
        : "call-panel-title"
    );
    ph.appendChild(titleSpan);
    var cl = iconBtn("✕", L("ui.call.collapse"), {
      onClick: function () {
        toggleCallPanel();
      },
    });
    ph.appendChild(cl);
    panel.appendChild(ph);
    var modeBar = el("div", "call-mode-bar");
    function callModeButton(btn, label) {
      btn.className += " call-mode-tab";
      btn.appendChild(el("span", "call-mode-label", label));
      return btn;
    }
    function callModeLabel(key, ruFallback, fallback) {
      return uiLabelFallback(key, ruFallback, fallback);
    }
    var bMesh = callModeButton(iconBtn("📡", callModeLabel("ui.call.modeMesh", "Звонок", "Call"), {
      primary: state.callMode === "mesh",
      testId: "mesh-webrtc-button",
      disabled: state.conferenceBusy,
      onClick: function () {
        switchCallMode("mesh");
      },
    }), callModeLabel("ui.call.modeMesh", "Звонок", "Call"));
    var bJitsi = callModeButton(iconBtn("🎥", callModeLabel("ui.call.modeJitsi", "Встреча", "Meeting"), {
      primary: state.callMode === "jitsi",
      disabled: state.conferenceBusy || state.busy,
      onClick: function () {
        switchCallMode("jitsi");
      },
    }), callModeLabel("ui.call.modeJitsi", "Встреча", "Meeting"));
    modeBar.appendChild(bMesh);
    modeBar.appendChild(bJitsi);
    if (window.KorusUiCallLivekit && KorusUiCallLivekit.groupCallSfuEnabled(state)) {
      modeBar.appendChild(
        callModeButton(iconBtn("☁", callModeLabel("ui.call.modeLivekit", "Эфир", "Live"), {
          primary: state.callMode === "livekit",
          testId: "livekit-sfu-button",
          disabled: state.conferenceBusy || state.callPanelToggleBusy,
          onClick: function () {
            switchCallMode("livekit");
          },
        }), callModeLabel("ui.call.modeLivekit", "Эфир", "Live"))
      );
    }
    panel.appendChild(modeBar);
    var panelContent = el("div", "call-panel-content");
    var callLobby = el("section", "call-lobby");
    var callLiveStage = el("section", "call-live-stage");
    panel.appendChild(panelContent);
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
            safeConferenceDisplayTitle(c) +
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
      callLobby.appendChild(confSec);
      if (window.KorusUiLiveSession) {
        KorusUiLiveSession.renderLiveSection(callLobby, state, {
          el: el,
          iconBtn: iconBtn,
          L: L,
          apiJson: apiJson,
          render: render,
        });
      }
      if (window.KorusUiCallLivekit && state.callMode === "livekit") {
        KorusUiCallLivekit.renderLiveKitSection(callLiveStage, state, {
          el: el,
          iconBtn: iconBtn,
          L: L,
          switchCallMode: switchCallMode,
          render: render,
        });
      }
    }
    if (callLobby.childNodes.length && state.callMode !== "mesh") {
      panelContent.appendChild(callLobby);
    }
    if (state.callMode === "mesh" && !meshCallChatReady()) {
      panelContent.appendChild(el("p", "call-hint call-hint-warn", L("conference.meshNeedsChatHint")));
    }
    if (state.activeConference && conferenceIsTracked(state.activeConference)) {
      var confAdrBar = uiCallAdr.mountConfAdrBar(getPhase5UiCtx(), state.activeConference);
      if (confAdrBar) {
        callLiveStage.appendChild(confAdrBar);
      }
    }
    if (state.callMode === "jitsi" && state.activeConference && state.activeConference.join_url) {
      var jHint = el("p", "call-hint");
      jHint.textContent = L("conference.jitsiHint", {
        host:
          state.mediaCaps && state.mediaCaps.jitsi_base_url
            ? state.mediaCaps.jitsi_base_url
            : "meet.jit.si",
      });
      callLiveStage.appendChild(jHint);
      if (
        conferenceIsTracked(state.activeConference) &&
        state.activeConference.conference_id &&
        state.conferenceParticipantsConfId !== state.activeConference.conference_id
      ) {
        loadConferenceParticipants(state.activeConference.conference_id)
          .then(function () {
            if (
              state.activeConference &&
              state.activeConference.conference_id === state.conferenceParticipantsConfId
            ) {
              scheduleRender();
            }
          })
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
        callLiveStage.appendChild(partSec);
      }
      var jWrap = el("div", "call-jitsi-wrap");
      var iframe = getOrCreateJitsiIframe();
      if (iframe.src !== state.activeConference.join_url) {
        iframe.src = state.activeConference.join_url;
      }
      jWrap.appendChild(iframe);
      callLiveStage.appendChild(jWrap);
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
      panelContent.appendChild(callLiveStage);
      panel.appendChild(jBar);
      shell.appendChild(panel);
      return;
    }
    if (state.callMode !== "mesh") {
      if (callLiveStage.childNodes.length) {
        panelContent.appendChild(callLiveStage);
      }
      shell.appendChild(panel);
      return;
    }
    var stage = el("div", "call-stage call-stage-mesh");
    var mainVid = el("div", "call-main-wrap");
    mainVid.id = "callLocalStage";
    var localName = state.myDisplayName || jwtSub(state.tokens && state.tokens.access_token) || "?";
    var localMeId = jwtSub(state.tokens && state.tokens.access_token);
    if (window.KorusUiCallMesh && typeof KorusUiCallMesh.createAvatarNode === "function") {
      var localAv = KorusUiCallMesh.createAvatarNode(
        localName,
        localMeId || localName,
        "call-local-avatar",
        state.myAvatarUrl || avatarUrlForUser(localMeId)
      );
      localAv.setAttribute("data-testid", "call-local-avatar");
      mainVid.appendChild(localAv);
    }
    var lv = document.createElement("video");
    lv.id = "callLocalVideo";
    lv.className = "call-video";
    lv.autoplay = true;
    lv.playsInline = true;
    lv.muted = true;
    mainVid.appendChild(lv);
    var localBadge = el("div", "rtc-remote-badge rtc-local-badge is-hidden", "");
    mainVid.appendChild(localBadge);
    stage.appendChild(mainVid);
    if (state.callScreenStream) {
      var shareStatus = el("div", "call-screen-status");
      shareStatus.setAttribute("data-testid", "call-screen-status");
      shareStatus.appendChild(el("div", "call-screen-status-title", L("ui.call.screenSharing")));
      shareStatus.appendChild(el("p", "call-screen-status-text", callWatermarkText()));
      shareStatus.appendChild(
        iconBtn("⏹", L("ui.call.stopScreen"), {
          size: "sm",
          testId: "call-screen-status-stop",
          onClick: function () {
            toggleScreenShare();
          },
        })
      );
      stage.appendChild(shareStatus);
    }
    callLiveStage.appendChild(stage);
    var thumbs = el("div", "call-thumbs");
    thumbs.appendChild(el("span", "call-thumbs-label", L("ui.call.thumbs")));
    var c1 = document.createElement("canvas");
    c1.className = "call-thumb-canvas";
    var c2 = document.createElement("canvas");
    c2.className = "call-thumb-canvas";
    thumbs.appendChild(c1);
    thumbs.appendChild(c2);
    callLiveStage.appendChild(thumbs);
    var remotes = el("div", "call-remotes");
    remotes.appendChild(el("div", "call-remotes-title", L("ui.call.remotes")));
    state.rtcPeerIds.forEach(function (pid) {
      var slot = el("div", "rtc-remote-slot");
      slot.id = "rtc-remote-" + pid;
      var labelName =
        window.KorusUiCallMesh ? KorusUiCallMesh.peerDisplayName(state, pid) : pid.slice(0, 8) + "…";
      slot.appendChild(el("div", "rtc-remote-label", labelName));
      if (window.KorusUiCallMesh) {
        slot.appendChild(
          KorusUiCallMesh.createAvatarNode(
            (state.rtcPeerMeta[pid] && state.rtcPeerMeta[pid].displayName) || pid,
            pid,
            null,
            avatarUrlForUser(pid)
          )
        );
      }
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
      var badge = el("div", "rtc-remote-badge is-hidden", "");
      slot.appendChild(badge);
      remotes.appendChild(slot);
    });
    if (!state.rtcPeerIds.length) {
      remotes.appendChild(
        el("div", "call-participant-slot", L("ui.call.aloneInChat"))
      );
    }
    callLiveStage.appendChild(remotes);
    panelContent.appendChild(callLiveStage);
    var bar = el("div", "call-toolbar");
    var bMic = iconBtn(state.callMicOn ? "🎤" : "🔇", state.callMicOn ? L("ui.call.micOn") : L("ui.call.micOff"), {
      primary: state.callMicOn,
      size: "md",
      testId: "call-mic-toggle",
      onClick: function () {
        toggleCallMic();
      },
    });
    var bCam = iconBtn(state.callCamOn ? "📷" : "📷", state.callCamOn ? L("ui.call.camOn") : L("ui.call.camOff"), {
      primary: state.callCamOn,
      size: "md",
      testId: "call-cam-toggle",
      onClick: function () {
        toggleCallCam();
      },
    });
    var bScr = iconBtn("🖥", state.callScreenStream ? L("ui.call.stopScreen") : L("ui.call.screen"), {
      size: "md",
      testId: "call-screen-toggle",
      onClick: function () {
        toggleScreenShare();
      },
    });
    bar.appendChild(bMic);
    bar.appendChild(bCam);
    bar.appendChild(bScr);
    panel.appendChild(bar);
    shell.appendChild(panel);
    setTimeout(function () {
      attachLocalVideo();
      if (window.KorusUiCallMesh) {
        KorusUiCallMesh.syncAllSlots(state);
      }
    }, 0);
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

  function appendSettingsRemindersAndScheduledSections(panel) {
    if (state.myRemindersBusy || state.myScheduledMessagesBusy) {
      panel.appendChild(el("p", "settings-hint", L("ui.common.loading")));
    }
    var remBox = el("div", "settings-reminders");
    remBox.setAttribute("data-testid", "settings-reminders");
    remBox.appendChild(el("h4", "settings-subsection-title", L("ui.settings.pendingReminders")));
    var pendingRem = (state.myReminders || []).filter(function (r) {
      return !r.status || r.status === "pending";
    });
    if (!pendingRem.length) {
      remBox.appendChild(el("p", "settings-hint", L("ui.reminders.emptyList")));
    } else {
      pendingRem.forEach(function (r) {
        var row = el("div", "settings-reminder-row");
        row.appendChild(
          el("span", "settings-reminder-label", chatTitleById(r.chat_id) + " · " + (r.remind_at || ""))
        );
        var acts = el("div", "settings-reminder-actions");
        acts.appendChild(
          iconBtn(L("ui.reminders.openChat"), L("ui.reminders.openChat"), {
            testId: "reminder-open-" + r.id,
            onClick: function () {
              selectChat(r.chat_id);
            },
          })
        );
        acts.appendChild(
          iconBtn(L("ui.reminders.cancel"), L("ui.reminders.cancel"), {
            testId: "reminder-cancel-" + r.id,
            onClick: function () {
              cancelMyReminder(r.id);
            },
          })
        );
        row.appendChild(acts);
        remBox.appendChild(row);
      });
    }
    panel.appendChild(remBox);

    var schedBox = el("div", "settings-scheduled");
    schedBox.setAttribute("data-testid", "settings-scheduled");
    schedBox.appendChild(el("h4", "settings-subsection-title", L("ui.settings.scheduledTitle")));
    var pendingSched = state.myScheduledMessages || [];
    if (!pendingSched.length) {
      schedBox.appendChild(el("p", "settings-hint", L("ui.schedule.emptyList")));
    } else {
      pendingSched.forEach(function (s) {
        var row = el("div", "settings-scheduled-row");
        var preview = (s.content || "").slice(0, 80);
        row.appendChild(
          el(
            "span",
            "settings-scheduled-label",
            L("ui.schedule.rowPreview", {
              chat: chatTitleById(s.chat_id),
              when: s.scheduled_at || "",
            }) + " · " + preview
          )
        );
        row.appendChild(
          iconBtn(L("ui.schedule.cancel"), L("ui.schedule.cancel"), {
            testId: "scheduled-cancel-" + s.id,
            onClick: function () {
              cancelScheduledMessage(s.id);
            },
          })
        );
        schedBox.appendChild(row);
      });
    }
    panel.appendChild(schedBox);
  }

  function notificationPermissionLabel() {
    if (typeof Notification === "undefined") {
      return L("notifications.unsupported");
    }
    if (Notification.permission === "granted") {
      return L("ui.settings.permissionGranted");
    }
    if (Notification.permission === "denied") {
      return L("notifications.denied");
    }
    return L("ui.settings.permissionDefault");
  }

  function appendSettingsDndDurationSelect(panel, opts) {
    opts = opts || {};
    var rowDnd = el("div", "settings-row");
    var dndLabel = document.createElement("label");
    dndLabel.setAttribute("for", "settings-dnd-duration");
    dndLabel.textContent = opts.label || L("ui.settings.dndSchedule");
    rowDnd.appendChild(dndLabel);
    var dndSel = document.createElement("select");
    dndSel.id = "settings-dnd-duration";
    dndSel.name = "dndDuration";
    dndSel.className = "settings-select";
    dndSel.setAttribute("data-testid", "settings-dnd-duration");
    [
      { id: "manual", key: "ui.settings.dndUntilManual" },
      { id: "30m", key: "ui.settings.dndUntil30m" },
      { id: "1h", key: "ui.settings.dndUntil1h" },
      { id: "4h", key: "ui.settings.dndUntil4h" },
      { id: "tomorrow", key: "ui.settings.dndUntilTomorrow" },
    ].forEach(function (optDef) {
      var opt = document.createElement("option");
      opt.value = optDef.id;
      opt.textContent = L(optDef.key);
      if (optDef.id === state.myDndDurationPreset) opt.selected = true;
      dndSel.appendChild(opt);
    });
    dndSel.disabled = state.busy;
    dndSel.onchange = function () {
      if (state.myPresence !== "dnd") {
        state.myDndDurationPreset = dndSel.value;
        updatePresence("dnd");
        return;
      }
      updateDndSchedule(dndSel.value);
    };
    rowDnd.appendChild(dndSel);
    panel.appendChild(rowDnd);
    if (state.myPresence === "dnd" && state.myDndUntil) {
      panel.appendChild(
        el(
          "p",
          "settings-hint",
          L("ui.settings.dndUntilActive", { until: formatInstantLabel(state.myDndUntil) })
        )
      );
    }
  }

  function appendSettingsDndSection(panel) {
    if (state.myPresence !== "dnd") {
      return;
    }
    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.dndSchedule")));
    appendSettingsDndDurationSelect(panel);
  }

  function appendSettingsGeneralPanel(panel) {
    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.appearance")));

    var rowTheme = el("div", "settings-row");
    rowTheme.appendChild(el("span", null, L("ui.settings.theme")));
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
        "btn btn-sm settings-locale-btn" + (currentLocale === code ? " active" : ""),
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
    panel.appendChild(el("p", "settings-hint settings-hint-subtle", L("ui.settings.appearanceKorus")));

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.cache")));
    panel.appendChild(el("p", "settings-hint", L("ui.settings.cacheHint")));
    var rowCache = el("div", "settings-row");
    rowCache.appendChild(el("span", null, L("ui.settings.resetCache")));
    rowCache.appendChild(
      iconBtn("🗑", L("ui.settings.resetCache"), {
        disabled: state.busy,
        onClick: function () {
          resetAppUiCache();
        },
      })
    );
    panel.appendChild(rowCache);

    if ("serviceWorker" in navigator) {
      panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionOffline")));
      panel.appendChild(el("p", "settings-hint", L("ui.settings.offlineHint")));
      panel.appendChild(el("p", "settings-hint", L("ui.offline.quotaHint")));
      var rowOfflineCache = el("div", "settings-row");
      rowOfflineCache.appendChild(el("span", null, L("ui.offline.clearCache")));
      rowOfflineCache.appendChild(
        iconBtn("🗑", L("ui.offline.clearCache"), {
          testId: "settings-offline-clear",
          disabled: state.busy,
          onClick: function () {
            if (global.KorusOfflineCache && global.KorusOfflineCache.clearAll) {
              global.KorusOfflineCache.clearAll().then(function () {
                state.statusMessage = L("ui.offline.clearCacheOk");
                render();
              });
            }
          },
        })
      );
      panel.appendChild(rowOfflineCache);
    }

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionAbout")));
    if (state.serverVersion) {
      var rowVer = el("div", "settings-row");
      rowVer.appendChild(el("span", null, L("ui.settings.apiVersion")));
      rowVer.appendChild(el("span", "settings-value", state.serverVersion));
      panel.appendChild(rowVer);
    }

    panel.appendChild(el("h3", "settings-subtitle settings-subtitle-kbd", L("ui.settings.kbdTitle")));
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
  }

  function appendSettingsProfilePanel(panel) {
    var meId = state.tokens ? jwtSub(state.tokens.access_token) : null;

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionAccount")));

    var avRow = el("div", "settings-row settings-avatar-row");
    avRow.appendChild(
      renderAvatarNode({
        url: state.myAvatarUrl,
        title: state.myDisplayName || state.myUsername || "?",
        userId: meId,
        size: "lg",
        testId: "settings-profile-avatar",
      })
    );
    var avActs = el("div", "settings-avatar-actions");
    var avFile = document.createElement("input");
    avFile.type = "file";
    avFile.accept = "image/*";
    avFile.hidden = true;
    avFile.onchange = function () {
      var f = avFile.files && avFile.files[0];
      avFile.value = "";
      if (f) {
        openAvatarCropThen(f, function (blob) {
          uploadMyAvatar(blob);
        });
      }
    };
    avActs.appendChild(avFile);
    avActs.appendChild(
      iconBtn("🖼", L("ui.profile.avatarChange"), {
        disabled: state.busy,
        testId: "settings-avatar-change",
        onClick: function () {
          avFile.click();
        },
      })
    );
    if (state.myAvatarUrl || state.myAvatarFileId) {
      avActs.appendChild(
        iconBtn("🗑", L("ui.profile.avatarRemove"), {
          disabled: state.busy,
          testId: "settings-avatar-remove",
          onClick: function () {
            removeMyAvatar();
          },
        })
      );
    }
    avRow.appendChild(avActs);
    panel.appendChild(avRow);

    var rowHideAv = el("div", "settings-row settings-avatar-hidden-row");
    var hideLbl = document.createElement("label");
    hideLbl.className = "settings-row-label";
    var hideChk = document.createElement("input");
    hideChk.type = "checkbox";
    hideChk.setAttribute("data-testid", "settings-avatar-hidden");
    hideChk.checked = !!state.myAvatarHidden;
    hideChk.disabled = state.busy;
    hideChk.onchange = function () {
      saveAvatarHidden(hideChk.checked);
    };
    hideLbl.appendChild(hideChk);
    hideLbl.appendChild(document.createTextNode(" " + L("ui.profile.avatarHidden")));
    rowHideAv.appendChild(hideLbl);
    panel.appendChild(rowHideAv);
    panel.appendChild(el("p", "settings-hint", L("ui.profile.avatarHiddenHint")));

    if (state.myUsername) {
      var rowUser = el("div", "settings-row");
      rowUser.appendChild(el("span", null, L("ui.auth.username")));
      rowUser.appendChild(el("span", "settings-value settings-value-mono", state.myUsername));
      panel.appendChild(rowUser);
      panel.appendChild(el("p", "settings-hint", L("ui.settings.usernameHint")));
    }

    var rowProf = el("div", "settings-row settings-row-stack");
    var nameLabel = document.createElement("label");
    nameLabel.setAttribute("for", "settings-display-name");
    nameLabel.className = "settings-row-label";
    nameLabel.textContent = L("ui.settings.name");
    rowProf.appendChild(nameLabel);
    var nameWrap = el("div", "settings-inline-controls");
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
    nameWrap.appendChild(nameInp);
    nameWrap.appendChild(
      iconBtn("💾", L("ui.common.save"), {
        disabled: state.busy,
        testId: "settings-display-name-save",
        onClick: function () {
          saveMyProfile();
        },
      })
    );
    rowProf.appendChild(nameWrap);
    panel.appendChild(rowProf);
    panel.appendChild(el("p", "settings-hint", L("ui.settings.displayNameHint")));

    if (state.myProfileEmail) {
      var rowEmail = el("div", "settings-row");
      rowEmail.appendChild(el("span", null, L("ui.settings.email")));
      rowEmail.appendChild(el("span", "settings-value", state.myProfileEmail));
      panel.appendChild(rowEmail);
    }

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionPresence")));

    var rowPres = el("div", "settings-row");
    var presLabel = document.createElement("label");
    presLabel.setAttribute("for", "settings-presence");
    presLabel.textContent = L("ui.settings.presence");
    rowPres.appendChild(presLabel);
    var presSel = document.createElement("select");
    presSel.id = "settings-presence";
    presSel.name = "presence";
    presSel.className = "settings-select";
    presSel.setAttribute("data-testid", "settings-presence");
    ["online", "away", "dnd", "offline"].forEach(function (st) {
      var opt = document.createElement("option");
      opt.value = st;
      opt.textContent = L(PRESENCE_LABEL_KEYS[st] || st);
      if (st === state.myPresence) opt.selected = true;
      presSel.appendChild(opt);
    });
    presSel.disabled = state.busy;
    presSel.onchange = function () {
      if (presSel.value === "dnd" && state.myPresence !== "dnd") {
        state.myDndDurationPreset = "1h";
      }
      updatePresence(presSel.value);
    };
    rowPres.appendChild(presSel);
    panel.appendChild(rowPres);

    if (state.myPresence === "dnd") {
      var dndHint = el("p", "settings-hint", L("ui.settings.dndSeeNotificationsTab"));
      dndHint.setAttribute("data-testid", "settings-dnd-profile-hint");
      panel.appendChild(dndHint);
    }

    var rowCustom = el("div", "settings-row settings-row-stack");
    var customLabel = document.createElement("label");
    customLabel.setAttribute("for", "settings-custom-status");
    customLabel.className = "settings-row-label";
    customLabel.textContent = L("ui.settings.customStatus");
    rowCustom.appendChild(customLabel);
    var customWrap = el("div", "settings-inline-controls");
    var customInp = document.createElement("input");
    customInp.type = "text";
    customInp.id = "settings-custom-status";
    customInp.className = "settings-text-input";
    customInp.maxLength = 128;
    customInp.placeholder = L("ui.settings.customStatusPlaceholder");
    customInp.value = state.myCustomStatus || "";
    customInp.disabled = state.busy;
    customInp.oninput = function () {
      state.myCustomStatus = customInp.value;
    };
    customWrap.appendChild(customInp);
    customWrap.appendChild(
      iconBtn("💾", L("ui.common.save"), {
        disabled: state.busy,
        testId: "settings-custom-status-save",
        onClick: function () {
          saveCustomStatus();
        },
      })
    );
    rowCustom.appendChild(customWrap);
    panel.appendChild(rowCustom);
    panel.appendChild(el("p", "settings-hint", L("ui.settings.customStatusHint")));

    if (state.blockedUsers && state.blockedUsers.length) {
      panel.appendChild(
        el("h3", "settings-subtitle", L("ui.settings.blockedUsers", { count: state.blockedUsers.length }))
      );
      state.blockedUsers.forEach(function (bu) {
        var rowBu = el("div", "settings-row settings-row-sub");
        var buLabel = bu.display_name || bu.username || bu.user_id;
        rowBu.appendChild(
          renderAvatarNode({
            url: bu.avatar_url || avatarUrlForUser(bu.user_id),
            title: buLabel,
            userId: bu.user_id,
            size: "sm",
          })
        );
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
    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionBrowserNotif")));

    var rowPerm = el("div", "settings-row");
    rowPerm.appendChild(el("span", null, L("ui.settings.permissionStatus")));
    rowPerm.appendChild(el("span", "settings-value", notificationPermissionLabel()));
    panel.appendChild(rowPerm);

    var rowNotif = el("div", "settings-row");
    rowNotif.appendChild(el("span", null, L("ui.settings.notificationPush")));
    rowNotif.appendChild(
      iconBtn(notificationsAllowed() ? "🔕" : "🔔", notificationsAllowed() ? L("ui.common.off") : L("ui.common.on"), {
        testId: "settings-notifications-toggle",
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

    if (!notificationsAllowed()) {
      if (typeof Notification !== "undefined" && Notification.permission === "denied") {
        panel.appendChild(el("p", "settings-hint settings-hint-error", L("notifications.denied")));
      } else if (typeof Notification !== "undefined") {
        panel.appendChild(el("p", "settings-hint", L("notifications.enableHint")));
      }
    }

    var rowSound = el("div", "settings-row");
    rowSound.appendChild(el("span", null, L("ui.settings.sound")));
    rowSound.appendChild(
      iconBtn(state.soundNotify ? "🔇" : "🔊", state.soundNotify ? L("ui.common.off") : L("ui.common.on"), {
        testId: "settings-sound-toggle",
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
          testId: "settings-notification-test",
          onClick: function () {
            testLocalNotification();
          },
        })
      );
      panel.appendChild(rowTestN);
    }

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionDnd")));
    if (state.myPresence === "dnd") {
      appendSettingsDndDurationSelect(panel);
    } else {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.dndOffHint")));
      appendSettingsDndDurationSelect(panel, { label: L("ui.settings.dndQuickEnable") });
    }

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionPush")));

    if (vapidPublicKey()) {
      if (notificationsAllowed()) {
        var rowPush = el("div", "settings-row");
        rowPush.appendChild(el("span", null, L("ui.settings.pushSyncUpdate")));
        rowPush.appendChild(
          iconBtn("↻", L("ui.settings.pushSyncUpdate"), {
            disabled: state.busy,
            testId: "settings-push-resync",
            onClick: function () {
              resyncWebPush();
            },
          })
        );
        panel.appendChild(rowPush);
        panel.appendChild(
          el(
            "p",
            "settings-hint",
            state.webPushRegistered
              ? L("ui.settings.webPushHintRegistered")
              : L("ui.settings.webPushHintDefault")
          )
        );
      } else {
        panel.appendChild(el("p", "settings-hint", L("ui.settings.webPushHintDefault")));
      }
    } else {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.webPushNoVapid")));
    }

    if (state.myDevices && state.myDevices.length) {
      panel.appendChild(el("p", "settings-hint", L("ui.settings.pushDevicesHint")));
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

    panel.appendChild(el("h3", "settings-subtitle", L("ui.settings.sectionPlanner")));
    appendSettingsRemindersAndScheduledSections(panel);
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
    var keyActs = el("div", "settings-inline-controls");
    keyActs.appendChild(
      iconBtn("📤", L("ui.settings.exportKey"), {
        disabled: !state.localKeyPackageMeta,
        onClick: function () {
          exportLocalKeyPackage();
        },
      })
    );
    keyActs.appendChild(
      iconBtn("📥", L("ui.settings.importKey"), {
        onClick: function () {
          importLocalKeyPackage();
        },
      })
    );
    if (state.localKeyPackageMeta) {
      keyActs.appendChild(
        iconBtn("🗑", L("ui.actions.delete"), {
          onClick: function () {
            wipeLocalKeyPackage();
          },
        })
      );
    }
    rowKeyIo.appendChild(keyActs);
    panel.appendChild(rowKeyIo);

    if (state.localKeyPackageMeta) {
      panel.appendChild(
        el(
          "p",
          "settings-hint",
          L("ui.settings.localKeySaved", {
            prefix:
              state.localKeyPackageMeta.public_key_prefix || L("ui.settings.localKeySavedYes"),
          })
        )
      );
    }

    panel.appendChild(el("p", "settings-hint", L("ui.settings.privateKeyHint")));
    uiPhase5Ext.mountFederationDirectory(getPhase5UiCtx(), panel);
    uiPhase5Ext.mountPasskeysSection(getPhase5UiCtx(), panel);
    uiPhase5Ext.mountSipGatewaySection(getPhase5UiCtx(), panel);
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

    var sHead = el("div", "settings-head");
    var sTitle = el("h2", "settings-title", L("ui.settings.title"));
    sTitle.id = "settings-dialog-title";
    sHead.appendChild(sTitle);
    var sClose = iconBtn("✕", L("ui.common.close"), {
      primary: true,
      testId: "settings-close",
      onClick: closeSettingsModal,
    });
    sHead.appendChild(sClose);
    sCard.appendChild(sHead);

    var sContent = el("div", "settings-content");
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
    sContent.appendChild(tablist);

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
    sContent.appendChild(sBody);
    sCard.appendChild(sContent);

    sOv.appendChild(sCard);
    sOv.onclick = function (e) {
      if (e.target === sOv) closeSettingsModal();
    };
    shell.appendChild(sOv);
  }

  function renderMain() {
    var root = document.getElementById("root");
    root.innerHTML = "";
    var shell = el(
      "div",
      "app-shell messenger-shell" +
        (state.callPanelOpen ? " call-open" : "") +
        (state.integrationPanel ? " integration-open" : "") +
        (state.selectedId ? " thread-focus" : "")
    );
    if (state.networkOnline === false) {
      var netBanner = el("div", "network-banner");
      netBanner.setAttribute("data-testid", "network-offline-banner");
      netBanner.textContent = state.threadOfflineCached
        ? L("ui.offline.cachedThread")
        : L("errors.networkOffline");
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
    appendMessageSearchBar(hl, { testId: "message-search-header", paneFocus: "sidebar" });
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
    if (state.tokens && state.myPresence) {
      var presPill = el("button", "presence-pill presence-" + state.myPresence);
      presPill.type = "button";
      presPill.setAttribute("data-testid", "header-presence-pill");
      presPill.title = L("ui.shell.presenceTitle");
      presPill.textContent = L(PRESENCE_LABEL_KEYS[state.myPresence] || state.myPresence);
      presPill.onclick = function () {
        var order = ["online", "away", "dnd", "offline"];
        var idx = order.indexOf(state.myPresence);
        var next = order[(idx + 1) % order.length];
        updatePresence(next);
      };
      hdrR.appendChild(presPill);
    }
    if (state.e2eeKeyCount !== null) {
      hdrR.appendChild(shellE2eeStatusIcon(state.e2eeKeyCount));
    }
    var themeBtn = el(
      "button",
      "btn btn-ghost btn-icon hdr-btn-optional",
      state.appearance === "light" ? "🌙" : "☀️"
    );
    themeBtn.type = "button";
    themeBtn.title =
      state.appearance === "light" ? L("ui.common.darkTheme") : L("ui.common.lightTheme");
    themeBtn.onclick = function () {
      toggleAppearance();
    };
    hdrR.appendChild(themeBtn);
    var notifBtn = el("button", "btn btn-ghost btn-icon hdr-btn-optional", notificationsAllowed() ? "🔔" : "🔕");
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
    hdrR.appendChild(shellWsStatusIcon());
    var lo = iconBtn("🚪", L("common.logout"), {
      testId: "logout",
      onClick: function () {
        logout();
      },
    });
    hdrR.appendChild(lo);
    header.appendChild(hdrR);
    shell.appendChild(header);
    if (
      effectiveMessageSearchScope() === "global" &&
      state.messageSearch.trim().length >= 2
    ) {
      var gPanel = el("div", "global-search-panel");
      if (state.messageSearchBusy) {
        gPanel.appendChild(el("div", "global-search-hint", L("ui.common.searching")));
      } else if (state.messageSearchHits && state.messageSearchHits.length) {
        state.messageSearchHits.forEach(function (hit) {
          var gb = el("button", "global-search-hit");
          gb.type = "button";
          var chatLbl = chatTitleById(hit.chat_id);
          gb.textContent = chatLbl + ": " + formatPreviewText(hit.type, hit.content);
          gb.onclick = function () {
            openGlobalSearchHit(hit);
          };
          gPanel.appendChild(gb);
        });
      } else if (state.messageSearchHits) {
        var gEmpty = el("div", "global-search-empty");
        gEmpty.setAttribute("data-testid", "global-search-empty");
        gEmpty.setAttribute("role", "status");
        gEmpty.appendChild(el("div", "global-search-empty-title", L("ui.search.emptyTitle")));
        gEmpty.appendChild(el("div", "global-search-hint", L("ui.search.emptyHint")));
        gPanel.appendChild(gEmpty);
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
    var main = el(
      "div",
      "messenger" + (state.selectedId ? " has-selection" : "")
    );
    applySidebarWidthCss();
    var side = el("aside", "sidebar");
    side.onmousedown = function () {
      setUiPaneFocus("sidebar");
    };
    var sh = el("div", "sidebar-header");
    var topRow = el("div", "sidebar-top-row");
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
    topRow.appendChild(search);
    topRow.appendChild(
      iconBtn("✚", L("ui.sidebar.newGroup"), {
        primary: true,
        disabled: state.busy,
        cls: "sidebar-new-chat-btn",
        testId: "sidebar-new-group",
        onClick: function () {
          newGroup();
        },
      })
    );
    sh.appendChild(topRow);
    var modeBar = el("div", "sidebar-mode-bar");
    var tabs = el("div", "sidebar-tabs");
    tabs.appendChild(
      sidebarTabButton(
        "💬",
        "ui.sidebar.chats",
        "Чаты",
        "Chats",
        state.sidebarMode === "chats",
        "sidebar-tab-chats",
        function () {
          state.sidebarMode = "chats";
          render();
        }
      )
    );
    tabs.appendChild(
      sidebarTabButton(
        "👥",
        "ui.sidebar.contacts",
        "Контакты",
        "Contacts",
        state.sidebarMode === "contacts",
        "sidebar-tab-contacts",
        function () {
          if (state.sidebarMode === "contacts") return;
          state.sidebarMode = "contacts";
          loadContacts().then(render);
        }
      )
    );
    if (isPlatformFeatureEnabled("integrations.sidebar.open")) {
      tabs.appendChild(
        sidebarTabButton(
          "🧩",
          "ui.sidebar.integrations",
          "Интеграции",
          "Integrations",
          state.sidebarMode === "integrations",
          "sidebar-tab-integrations",
          function () {
            if (state.sidebarMode === "integrations") return;
            state.sidebarMode = "integrations";
            loadIntegrations().then(render);
          }
        )
      );
    } else if (state.sidebarMode === "integrations") {
      state.sidebarMode = "chats";
    }
    modeBar.appendChild(tabs);
    sh.appendChild(modeBar);
    side.appendChild(sh);
    var sidebarContent = el("div", "sidebar-content");
    var qTrim = state.sidebarSearch.trim();
    if (qTrim.length >= 2) {
      var usBlock = el("div", "user-search-block");
      usBlock.appendChild(el("div", "user-search-label", L("ui.sidebar.users")));
      if (state.userSearchBusy) {
        usBlock.appendChild(el("div", "user-search-hint", L("ui.common.searching")));
      } else if (state.userSearchHits && state.userSearchHits.length) {
        state.userSearchHits.forEach(function (u) {
          var row = el("div", "user-search-item-row");
          var label = u.display_name || u.username || u.user_id;
          var main = el("div", "user-search-item-main");
          main.appendChild(
            renderAvatarNode({
              url: u.avatar_url || avatarUrlForUser(u.user_id),
              title: label,
              userId: u.user_id,
              size: "sm",
            })
          );
          var ub = el("button", "user-search-item");
          ub.type = "button";
          ub.disabled = state.busy;
          ub.textContent = label + " · " + (u.username || u.user_id.slice(0, 8));
          ub.onclick = function () {
            openP2pChat(u.user_id);
          };
          main.appendChild(ub);
          row.appendChild(main);
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
      sidebarContent.appendChild(usBlock);
    }
    if (state.sidebarMode === "contacts") {
      var cList = el("div", "chat-list contacts-list");
      if (state.contactsBusy) {
        cList.appendChild(el("div", "chat-list-empty", L("ui.sidebar.loadingContacts")));
      } else if (state.contacts && state.contacts.length) {
        state.contacts.forEach(function (ct) {
          var row = el("div", "contact-item-row");
          var label = ct.display_name || ct.username || ct.id;
          row.appendChild(
            renderAvatarNode({
              url: ct.avatar_url || avatarUrlForUser(ct.id),
              title: label,
              userId: ct.id,
              size: "md",
            })
          );
          var cb = el("button", "chat-item contact-item-btn");
          cb.type = "button";
          cb.disabled = state.busy;
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
      sidebarContent.appendChild(cList);
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
      sidebarContent.appendChild(impBlock);
    } else if (state.sidebarMode === "integrations") {
      var iList = el("div", "chat-list integrations-list");
      var searchRow = el("div", "integrations-market-search");
      var searchInput = el("input", "integrations-search-input");
      searchInput.type = "search";
      searchInput.placeholder = L("ui.sidebar.marketplaceSearch");
      searchInput.value = state.integrationsSearch || "";
      searchInput.setAttribute("data-testid", "integrations-marketplace-search");
      searchInput.oninput = function () {
        state.integrationsSearch = searchInput.value || "";
        render();
      };
      searchRow.appendChild(searchInput);
      iList.appendChild(searchRow);
      var categories = state.integrationsMarketplaceCategories || [];
      if (categories.length) {
        var catBar = el("div", "integrations-category-bar");
        ["all"].concat(categories).forEach(function (cat) {
          var label = cat === "all" ? L("ui.sidebar.marketplaceAll") : cat;
          catBar.appendChild(
            iconBtn(label, label, {
              cls:
                "integrations-category-chip" +
                ((state.integrationsCategory || "all") === cat ? " active" : ""),
              testId: "integrations-cat-" + cat,
              onClick: function () {
                state.integrationsCategory = cat;
                render();
              },
            })
          );
        });
        iList.appendChild(catBar);
      }
      var vitrine = state.integrationsVitrine || [];
      if (vitrine.length) {
        var vitBlock = el("div", "integrations-vitrine");
        vitBlock.appendChild(el("div", "user-search-label", L("ui.sidebar.vitrine")));
        vitrine.forEach(function (tile) {
          var row = el("button", "chat-item integration-vitrine-item");
          row.type = "button";
          row.setAttribute("data-testid", "vitrine-tile-" + (tile.connector_key || tile.id));
          row.textContent = tile.label || tile.connector_key || tile.id;
          if (tile.info_url) {
            row.onclick = function () {
              window.open(tile.info_url, "_blank", "noopener,noreferrer");
            };
          }
          vitBlock.appendChild(row);
        });
        iList.appendChild(vitBlock);
      }
      if (state.platformCaps && state.platformCaps.external_stack) {
        var stack = state.platformCaps.external_stack;
        var keys = Object.keys(stack);
        var passed = keys.filter(function (k) {
          return stack[k] && stack[k].validation_status === "passed";
        }).length;
        var foot = el("div", "chat-list-empty integrations-stack-foot");
        foot.setAttribute("data-testid", "integrations-external-stack-summary");
        foot.textContent = L("ui.sidebar.externalStackSummary")
          .replace("{passed}", String(passed))
          .replace("{total}", String(keys.length));
        iList.appendChild(foot);
      }
      var items = state.integrationsMarketplace && state.integrationsMarketplace.length
        ? state.integrationsMarketplace
        : state.integrations || [];
      var q = (state.integrationsSearch || "").trim().toLowerCase();
      var cat = state.integrationsCategory || "all";
      items = items.filter(function (it) {
        if (cat !== "all" && (it.category || "general") !== cat) return false;
        if (!q) return true;
        var hay = (
          (it.label || "") +
          " " +
          (it.description || "") +
          " " +
          (it.bot_name || "") +
          " " +
          (it.category || "")
        ).toLowerCase();
        return hay.indexOf(q) >= 0;
      });
      if (!items.length) {
        iList.appendChild(
          el(
            "div",
            "chat-list-empty",
            q ? L("ui.marketplace.noResults") : L("ui.sidebar.noIntegrations")
          )
        );
      } else {
        items.forEach(function (it) {
          var row = el("div", "integration-item-row");
          var btn = el("button", "chat-item integration-item");
          btn.type = "button";
          var label = it.label || it.bot_name || it.id;
          if (it.description) {
            btn.appendChild(el("span", "integration-item-label", label));
            btn.appendChild(el("span", "integration-item-desc", it.description));
          } else {
            btn.textContent = label;
          }
          btn.setAttribute("data-testid", "integration-open-" + (it.bot_name || it.id));
          btn.onclick = function () {
            openIntegration(it);
          };
          row.appendChild(btn);
          row.appendChild(
            iconBtn(L("ui.marketplace.open"), L("ui.marketplace.open"), {
              cls: "integration-open-btn",
              testId: "marketplace-open-" + (it.instance_id || it.id || it.bot_name),
              onClick: function () {
                openIntegration(it);
              },
            })
          );
          row.appendChild(
            iconBtn(it.connected ? "✓" : "+", it.connected ? L("ui.marketplace.disconnect") : L("ui.marketplace.connect"), {
              cls: "integration-connect-btn" + (it.connected ? " connected" : ""),
              testId: "marketplace-connect-" + (it.instance_id || it.id || it.bot_name),
              disabled: state.busy,
              onClick: function () {
                if (it.connected) disconnectMarketplaceItem(it);
                else connectMarketplaceItem(it);
              },
            })
          );
          iList.appendChild(row);
        });
      }
      sidebarContent.appendChild(iList);
    } else {
    var list = el("div", "chat-list");
    list.addEventListener("scroll", function () {
      if (list.scrollTop + list.clientHeight >= list.scrollHeight - 48) {
        scheduleChatPreviewHydrateMore();
      }
    });
    if (state.chatsLoading && !state.chats.length) {
      uiUx.mountChatListSkeleton(list, el, 6);
    } else {
    var fc = filteredChats();
    if (fc.length === 0) {
      var emptyKey =
        state.sidebarChatFilter === "unread"
          ? "ui.sidebar.noChatsUnread"
          : state.sidebarChatFilter === "mentions"
            ? "ui.sidebar.noChatsMentions"
            : state.chats.length
              ? "ui.sidebar.noChatsFilter"
              : "ui.sidebar.noChats";
      list.appendChild(el("div", "chat-list-empty", L(emptyKey)));
    }
    fc.forEach(function (c) {
      var unread = state.unreadByChat[c.id] || 0;
      var isUnread = unread > 0 && c.id !== state.selectedId;
      var mentionPending = !!(state.mentionPendingChats && state.mentionPendingChats[c.id]);
      var b = el(
        "button",
        "chat-item" +
          (c.id === state.selectedId ? " active" : "") +
          (isUnread ? " chat-item-has-unread" : "")
      );
      b.type = "button";
      b.setAttribute("data-testid", "chat-item-" + c.id);
      b.onclick = function () {
        openChatById(c.id);
      };
      var row = el("div", "chat-item-row");
      var chatTitle = displayTitleForChat(c, c.id);
      var av = renderAvatarNode({
        url: chatAvatarUrl(c),
        title: chatTitle,
        userId: c.id,
        size: "md",
        testId: "chat-row-avatar",
      });
      if (isUnread) av.classList.add("chat-avatar-unread");
      row.appendChild(av);
      var txt = el("div", "chat-item-text");
      var titleRow = el("div", "chat-item-title-row");
      if (c.type === "channel") {
        titleRow.appendChild(el("span", "chat-type-badge channel", L("ui.chat.channel")));
      }
      titleRow.appendChild(
        el(
          "div",
          "chat-item-title" + (isUnread ? " chat-item-title-unread" : ""),
          displayTitleForChat(c, c.id) +
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
        if (isUnread) prevCls += " chat-preview-unread";
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
      var badges = el("div", "chat-item-badges");
      if (mentionPending && c.id !== state.selectedId) {
        badges.appendChild(el("span", "chat-mention-badge", "@"));
      }
      if (isUnread) {
        badges.appendChild(
          el(
            "span",
            "chat-unread-badge",
            unread > 99 ? "99+" : String(unread)
          )
        );
      }
      if (badges.childNodes.length) row.appendChild(badges);
      b.appendChild(row);
      list.appendChild(b);
    });
    }
    sidebarContent.appendChild(list);
    }
    side.appendChild(sidebarContent);
    if (state.sidebarMode === "chats") {
      side.appendChild(mountSidebarFiltersPanel());
    }
    main.appendChild(side);
    var sideResizer = el("div", "sidebar-resizer");
    sideResizer.setAttribute("data-testid", "sidebar-resizer");
    sideResizer.title = L("ui.sidebar.resizeTitle");
    bindSidebarResizer(sideResizer);
    main.appendChild(sideResizer);
    var thread = el("section", "thread");
    thread.onmousedown = function () {
      if (state.selectedId) setUiPaneFocus("thread");
    };
    if (!state.selectedId) {
      thread.appendChild(el("div", "empty-thread", L("ui.thread.empty")));
    } else {
      var sel = state.chats.find(function (x) {
        return x.id === state.selectedId;
      });
      var th = el("div", "thread-header");
      if (state.selectedId) {
        th.appendChild(
          iconBtn("←", L("ui.shell.backToChats"), {
            cls: "thread-back-btn",
            testId: "thread-back",
            onClick: function () {
              closeMobileThread();
            },
          })
        );
      }
      var thMain = el("div", "thread-header-main");
      var titleRow = el("div", "thread-header-title-row");
      if (sel) {
        titleRow.appendChild(
          renderAvatarNode({
            url: chatAvatarUrl(sel),
            title: displayTitleForChat(sel, state.selectedId),
            userId: state.selectedId,
            size: "md",
            testId: "thread-header-avatar",
          })
        );
      }
      titleRow.appendChild(
        el("div", "thread-title", displayTitleForChat(sel, state.selectedId))
      );
      thMain.appendChild(titleRow);
      if (sel && sel.type === "p2p" && !(sel.title && String(sel.title).trim())) {
        thMain.appendChild(el("div", "thread-subtitle", L("ui.thread.directChat")));
      } else if (sel && sel.type === "group" && sel.member_count) {
        thMain.appendChild(
          el(
            "div",
            "thread-subtitle",
            L("ui.sidebar.membersMeta", { type: sel.type, count: sel.member_count })
          )
        );
      }
      if (state.discussionThreadRootId) {
        var discBar = el("div", "thread-discussion-bar");
        discBar.appendChild(
          iconBtn("←", L("ui.thread.backToChat"), {
            onClick: function () {
              closeDiscussionThread();
            },
          })
        );
        discBar.appendChild(el("span", "thread-discussion-label", L("ui.thread.discussion")));
        thMain.appendChild(discBar);
      }
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
      var thSearchHost = el("div", "thread-header-search-host");
      appendMessageSearchBar(thSearchHost, {
        mobile: true,
        testId: "message-search-thread",
        paneFocus: "thread",
      });
      th.appendChild(thSearchHost);
      var thActs = el("div", "thread-header-actions");
      if (sel && sel.type !== "saved") {
        var moreWrap = el("div", "thread-more-wrap");
        var moreOpen = false;
        var morePop = el("div", "thread-more-pop");
        morePop.style.display = "none";
        morePop.setAttribute("data-testid", "thread-more-pop");
        var moreIcons = el("div", "thread-more-icons composer-more-icons");
        function closeThreadMoreMenu() {
          moreOpen = false;
          morePop.style.display = "none";
        }
        function wrapThreadMoreAction(handler) {
          return function () {
            closeThreadMoreMenu();
            handler();
          };
        }
        function threadMoreBtn(featureKey, buildBtn) {
          if (featureKey && !isPlatformFeatureVisible(featureKey)) return;
          moreIcons.appendChild(buildBtn());
        }
        threadMoreBtn("chat.mute", function () {
          return iconBtn(sel.muted ? "🔊" : "🔇", sel.muted ? L("ui.thread.unmute") : L("ui.thread.mute"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              toggleChatMute();
            }),
          });
        });
        threadMoreBtn("chat.read", function () {
          return iconBtn(sel.personal_filter_active ? "🔎" : "🔍", L("ui.thread.filterTitle"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              togglePersonalFilter();
            }),
          });
        });
        threadMoreBtn("chat.archive", function () {
          return iconBtn(sel.archived ? "📂" : "🗄", L("ui.sidebar.archiveToggle"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              toggleChatArchive();
            }),
          });
        });
        threadMoreBtn("chat.folders", function () {
          return iconBtn("🏷", L("ui.sidebar.folderWork"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              setChatFolder(sel.folder_tag === "work" ? null : "work");
            }),
          });
        });
        threadMoreBtn("chat.folders", function () {
          return iconBtn("👤", L("ui.sidebar.folderPersonal"), {
            testId: "chat-folder-personal",
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              setChatFolder(sel.folder_tag === "personal" ? null : "personal");
            }),
          });
        });
        if (sel.type === "group") {
          threadMoreBtn("chat.members.list", function () {
            return iconBtn("👥", L("ui.common.members"), {
              testId: "chat-members-button",
              disabled: state.busy,
              onClick: wrapThreadMoreAction(function () {
                openMembersModal();
              }),
            });
          });
        }
        var exportThisChat =
          state.exportBusy && state.exportJobChatId === state.selectedId;
        var exportTip = exportThisChat
          ? L("ui.thread.exportCancel")
          : state.exportBusy
            ? L("common.exportBusy")
            : L("ui.thread.export");
        threadMoreBtn("export.job.create", function () {
          return iconBtn(exportThisChat ? "⏹" : "📤", exportTip, {
            testId: "chat-export-button",
            disabled: state.busy || (state.exportBusy && !exportThisChat),
            onClick: wrapThreadMoreAction(function () {
              if (exportThisChat) {
                cancelChatExport();
              } else {
                startChatExport();
              }
            }),
          });
        });
        threadMoreBtn("chat.read", function () {
          return iconBtn("↻", L("ui.thread.refresh"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              refreshCurrentThread();
            }),
          });
        });
        threadMoreBtn("chat.read", function () {
          return iconBtn("🔗", L("ui.thread.copyChatLink"), {
            disabled: state.busy,
            onClick: wrapThreadMoreAction(function () {
              copyChatDeepLink();
            }),
          });
        });
        if (sel.type === "group") {
          threadMoreBtn("productivity.polls.list", function () {
            return iconBtn("📊", L("ui.polls.title"), {
              testId: "poll-create-header",
              disabled: state.busy,
              onClick: wrapThreadMoreAction(function () {
                toggleThreadExtrasTab("polls");
              }),
            });
          });
        }
        var phase5Tools = uiPhase5Ext.mountThreadTools(getPhase5UiCtx());
        if (phase5Tools) {
          while (phase5Tools.firstChild) {
            moreIcons.appendChild(phase5Tools.firstChild);
          }
        }
        if (moreIcons.childNodes.length) {
          morePop.appendChild(moreIcons);
          var bThreadMore = el("button", "btn btn-ghost btn-icon thread-more-toggle");
          bThreadMore.type = "button";
          bThreadMore.title = L("ui.thread.chatMore");
          bThreadMore.setAttribute("data-testid", "thread-more-toggle");
          bThreadMore.textContent = "⋯";
          bThreadMore.onclick = function () {
            moreOpen = !moreOpen;
            morePop.style.display = moreOpen ? "flex" : "none";
          };
          moreWrap.appendChild(bThreadMore);
          moreWrap.appendChild(morePop);
          thActs.appendChild(moreWrap);
        }
      }
      th.appendChild(thActs);
      thread.appendChild(th);
      var threadBody = el("div", "thread-body");
      thread.appendChild(threadBody);
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
        } else if (
          threadLiveConf.conference_id &&
          state.conferenceParticipantsConfId !== threadLiveConf.conference_id
        ) {
          var confChatId = state.selectedId;
          loadConferenceParticipants(threadLiveConf.conference_id)
            .then(function () {
              if (state.selectedId === confChatId) scheduleRender();
            })
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
        threadBody.appendChild(confBanner);
      }
      if (
        effectiveMessageSearchScope() === "chat" &&
        state.messageSearch.trim().length >= 2
      ) {
        if (state.messageSearchBusy) {
          threadBody.appendChild(el("div", "thread-search-hint", L("ui.common.searching")));
        } else if (state.messageSearchHits) {
          var hitsBox = el("div", "thread-search-hits");
          if (!state.messageSearchHits.length) {
            var tEmpty = el("div", "thread-search-empty");
            tEmpty.setAttribute("data-testid", "thread-search-empty");
            tEmpty.setAttribute("role", "status");
            tEmpty.appendChild(el("div", "thread-search-empty-title", L("ui.thread.searchEmptyTitle")));
            tEmpty.appendChild(el("div", "thread-search-hint", L("ui.thread.searchEmptyHint")));
            hitsBox.appendChild(tEmpty);
          } else {
            state.messageSearchHits.forEach(function (hit) {
              var hb = el("button", "thread-search-hit");
              hb.type = "button";
              if (hit.sender_id) {
                var hitRow = el("div", "thread-search-hit-row");
                hitRow.appendChild(
                  renderAvatarNode({
                    url: hit.sender_avatar_url || avatarUrlForUser(hit.sender_id),
                    title: avatarTitleForUser(hit.sender_id),
                    userId: hit.sender_id,
                    size: "sm",
                  })
                );
                var hitTxt = el("span", "thread-search-hit-text");
                hitTxt.textContent = formatPreviewText(hit.type, hit.content);
                hitRow.appendChild(hitTxt);
                hb.appendChild(hitRow);
              } else {
                hb.textContent = formatPreviewText(hit.type, hit.content);
              }
              hb.onclick = function () {
                openSearchHit(hit);
              };
              hitsBox.appendChild(hb);
            });
          }
          threadBody.appendChild(hitsBox);
        }
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
        threadBody.appendChild(pinsBar);
      }
      if (uiThreadExtras) {
        var extrasSection = uiThreadExtras.mountThreadExtras(getThreadExtrasUiCtx());
        if (extrasSection) {
          threadBody.appendChild(extrasSection);
        }
      }
      var msgs = el("div", "messages");
      if (state.threadLoading) {
        msgs.appendChild(uiUx.mountThreadSkeleton(el));
      } else {
      var loadOlder = null;
      if (state.threadHasMore) {
        loadOlder = iconBtn("↑", state.threadLoadingMore ? "…" : L("ui.thread.loadOlder"), {
          cls: "messages-load-more",
          disabled: state.threadLoadingMore,
          onClick: function () {
            loadOlderMessages();
          },
        });
      }
      var myId = jwtSub(state.tokens.access_token);
      var messageArticleCtx = {
        el: el,
        iconBtn: iconBtn,
        L: L,
        myId: myId,
        state: state,
        render: render,
        isMessagePinned: isMessagePinned,
        messageMentionsMe: messageMentionsMe,
        openDiscussionThread: openDiscussionThread,
        openMessageVersions: openMessageVersions,
        showReadReceiptPopup: showReadReceiptPopup,
        messageVisibilityTtlSeconds: messageVisibilityTtlSeconds,
        messageExpiryEpochMs: messageExpiryEpochMs,
        formatInstantLabel: formatInstantLabel,
        formatTimeLeft: formatTimeLeft,
        appendReplyQuoteBlock: appendReplyQuoteBlock,
        isE2eeType: isE2eeType,
        e2eePlainType: e2eePlainType,
        renderMessageContent: renderMessageContent,
        aggregateReactions: aggregateReactions,
        toggleReaction: toggleReaction,
        reactionPickerEmojis: REACTION_PICKER_EMOJIS,
        setReplyTo: setReplyTo,
        copyMessageText: copyMessageText,
        copyMessageDeepLink: copyMessageDeepLink,
        messageAttachmentFileId: messageAttachmentFileId,
        downloadChatFile: downloadChatFile,
        togglePinMessage: togglePinMessage,
        openForwardPicker: openForwardPicker,
        openMessageReminder: openMessageReminder,
        createPublicLinkForFile: createPublicLinkForFile,
        openFilePublicLinksModal: openFilePublicLinksModal,
        deleteOwnFile: deleteOwnFile,
        saveMessageToVault: saveMessageToVault,
        editMessagePrompt: editMessagePrompt,
        deleteMessageConfirm: deleteMessageConfirm,
        quickReactions: QUICK_REACTIONS,
        renderAvatar: renderAvatarNode,
        avatarUrlForUser: avatarUrlForUser,
        avatarTitleForUser: avatarTitleForUser,
        openProfileCard: openProfileCard,
      };
      function buildMessageArticle(m) {
        return uiMessageArticle.buildMessageArticle(m, messageArticleCtx);
      }
      var focusIdx = null;
      if (state.virtualFocusMessageId) {
        for (var fi = 0; fi < state.messages.length; fi++) {
          if (state.messages[fi].id === state.virtualFocusMessageId) {
            focusIdx = fi;
            break;
          }
        }
      }
      var virtualMounted = false;
      if (uiMessageList) {
        virtualMounted = uiMessageList.mountMessageList(msgs, {
          messages: state.messages,
          renderMessage: function (m) {
            return buildMessageArticle(m);
          },
          focusIndex: focusIdx,
          loadMoreEl: loadOlder,
          onScrollNearTop: function () {
            if (state.threadHasMore && !state.threadLoadingMore) {
              loadOlderMessages();
            }
          },
        });
      }
      if (virtualMounted) {
        state.virtualFocusMessageId = null;
      }
      }
      threadBody.appendChild(msgs);
      var threadFoot = el("div", "thread-foot");
      threadFoot.appendChild(
        uiComposer.mountComposer({
          el: el,
          iconBtn: iconBtn,
          L: L,
          state: state,
          isGroupChat: !!(sel && sel.type === "group"),
          replyTo: state.replyTo,
          clearReplyTo: clearReplyTo,
          render: render,
          sendMessage: sendMessage,
          sendLocationMessage: sendLocationMessage,
          sendFileMessage: sendFileMessage,
          sendVoiceMessage: sendVoiceMessage,
          openPollCreate:
            sel && sel.type === "group" && isPlatformFeatureVisible("productivity.polls.create")
              ? openPollCreate
              : null,
          openScheduleSend: isPlatformFeatureVisible("productivity.scheduled_messages.create")
            ? openScheduleSend
            : null,
          openContactShare: isPlatformFeatureVisible("contacts.list") ? openContactShare : null,
          isPlatformFeatureVisible: isPlatformFeatureVisible,
          sendVideoNoteMessage: sendVideoNoteMessage,
          loadComposerDraftForChat: loadComposerDraftForChat,
          saveComposerDraftForChat: saveComposerDraftForChat,
          scheduleSaveComposerDraft: scheduleSaveComposerDraft,
          scheduleTypingNotify: scheduleTypingNotify,
          reactionPickerEmojis: REACTION_PICKER_EMOJIS,
        })
      );
      thread.appendChild(threadFoot);
    }
    main.appendChild(thread);
    shell.appendChild(main);
    renderIntegrationPanel(shell);
    try {
      renderCallPanel(shell);
    } catch (err) {
      console.error("renderCallPanel failed", err);
      state.error = (err && err.message) || L("conference.meshUnavailable");
      appendMinimalCallPanel(shell);
    }
    if (state.settingsOpen) {
      renderSettingsModal(shell);
    }
    if (state.forwardPick) {
      var fOv = el("div", "forward-overlay");
      fOv.setAttribute("data-testid", "forward-overlay");
      var fCard = el("div", "forward-card");
      var fClose = iconBtn("✕", L("ui.common.cancel"), {
        testId: "forward-cancel",
        onClick: function () {
          closeForwardPicker();
        },
      });
      fCard.appendChild(modalCardHead(L("ui.forward.title"), fClose));
      var fContent = el("div", "settings-content forward-card-content");
      fContent.appendChild(el("p", "forward-snippet", state.forwardPick.snippet));
      var fList = el("div", "forward-chat-list");
      state.chats
        .filter(function (c) {
          return c.id !== state.selectedId;
        })
        .forEach(function (c) {
          var fb = el("button", "forward-chat-item");
          fb.type = "button";
          fb.disabled = state.busy;
          var fRow = el("div", "forward-chat-item-row");
          fRow.appendChild(
            renderAvatarNode({
              url: chatAvatarUrl(c),
              title: displayTitleForChat(c, c.id),
              userId: c.id,
              size: "sm",
              alt: L("ui.avatar.altChat", { name: displayTitleForChat(c, c.id) }),
            })
          );
          var fTxt = el("span", "forward-chat-item-label");
          fTxt.textContent =
            (c.title || c.type) +
            " · " +
            L("ui.sidebar.membersMeta", { type: c.type, count: c.member_count });
          fRow.appendChild(fTxt);
          fb.appendChild(fRow);
          fb.onclick = function () {
            forwardMessageTo(c.id);
          };
          fList.appendChild(fb);
        });
      fContent.appendChild(fList);
      fCard.appendChild(fContent);
      fOv.appendChild(fCard);
      fOv.onclick = function (e) {
        if (e.target === fOv) closeForwardPicker();
      };
      shell.appendChild(fOv);
    }
    var profileOv = uiProfileCard.mountProfileCardOverlay({
      state: state,
      myId: jwtSub(state.tokens && state.tokens.access_token),
      el: el,
      L: L,
      iconBtn: iconBtn,
      modalCardHead: modalCardHead,
      renderAvatar: renderAvatarNode,
      avatarUrlForUser: avatarUrlForUser,
      avatarTitleForUser: avatarTitleForUser,
      openP2pChat: openP2pChat,
      blockUser: blockUser,
      closeProfileCard: closeProfileCard,
    });
    if (profileOv) shell.appendChild(profileOv);
    var pollOv = uiPolls.mountPollCreateOverlay(getPollsUiCtx());
    if (pollOv) shell.appendChild(pollOv);
    var schedOv = uiPolls.mountScheduleOverlay(getPollsUiCtx());
    if (schedOv) shell.appendChild(schedOv);
    var remOv = uiPolls.mountReminderOverlay(getPollsUiCtx());
    if (remOv) shell.appendChild(remOv);
    var contactOv = uiPolls.mountContactShareOverlay(getPollsUiCtx());
    if (contactOv) shell.appendChild(contactOv);
    var stickersOv = uiPhase5Ext.mountStickersOverlay(getPhase5UiCtx());
    if (stickersOv) shell.appendChild(stickersOv);
    var aiOv = uiPhase5Ext.mountAiAssistOverlay(getPhase5UiCtx());
    if (aiOv) shell.appendChild(aiOv);
    var phase5Modal = uiCallAdr.mountPhase5Modal(getPhase5UiCtx());
    if (phase5Modal) shell.appendChild(phase5Modal);
    if (state.membersModalOpen) {
      var mOv = el("div", "settings-overlay");
      mOv.setAttribute("data-testid", "members-overlay");
      var mCard = el("div", "settings-card members-card");
      var selChat = currentChat();
      var mTitleText =
        selChat && selChat.type === "group"
          ? L("ui.members.groupTitle", { name: selChat.title || selChat.id.slice(0, 8) })
          : L("ui.members.chatTitle");
      var mClose = iconBtn("✕", L("ui.common.close"), {
        testId: "members-close",
        primary: true,
        onClick: function () {
          closeMembersModal();
        },
      });
      mCard.appendChild(modalCardHead(mTitleText, mClose));
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
          var grpAvFile = document.createElement("input");
          grpAvFile.type = "file";
          grpAvFile.accept = "image/*";
          grpAvFile.hidden = true;
          grpAvFile.onchange = function () {
            var f = grpAvFile.files && grpAvFile.files[0];
            grpAvFile.value = "";
            if (f) {
              openAvatarCropThen(f, function (blob) {
                uploadGroupAvatar(blob);
              });
            }
          };
          mTools.appendChild(grpAvFile);
          mTools.appendChild(
            iconBtn("🖼", L("ui.profile.avatarChange"), {
              disabled: state.busy,
              testId: "group-avatar-change",
              onClick: function () {
                grpAvFile.click();
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
            memberRoleLabel(m.role) +
            (m.banned ? L("ui.members.banned") : "");
          var labelWrap = el("div", "member-row-label-wrap");
          labelWrap.appendChild(
            renderAvatarNode({
              url: m.avatar_url || avatarUrlForUser(m.user_id),
              title: m.display_name || m.username || m.user_id,
              userId: m.user_id,
              size: "sm",
            })
          );
          labelWrap.appendChild(el("span", "member-row-label", label));
          mRow.appendChild(labelWrap);
          if (myRole === "owner" && m.user_id !== meId && m.role !== "owner" && !m.banned) {
            var roleSel = document.createElement("select");
            roleSel.className = "member-role-select";
            ["member", "admin"].forEach(function (r) {
              var opt = document.createElement("option");
              opt.value = r;
              opt.textContent = memberRoleLabel(r);
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
      var mContent = el("div", "settings-content");
      mContent.appendChild(mBody);
      mCard.appendChild(mContent);
      mOv.appendChild(mCard);
      mOv.onclick = function (e) {
        if (e.target === mOv) closeMembersModal();
      };
      shell.appendChild(mOv);
    }
    if (state.fileLinksOpen) {
      var flOv = el("div", "settings-overlay");
      var flCard = el("div", "settings-card members-card");
      var flClose = iconBtn("✕", L("ui.common.close"), {
        primary: true,
        onClick: function () {
          closeFilePublicLinksModal();
        },
      });
      flCard.appendChild(modalCardHead(L("ui.fileLinks.title"), flClose));
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
      var flContent = el("div", "settings-content");
      flContent.appendChild(flBody);
      var flFoot = el("div", "modal-footer settings-foot");
      flFoot.appendChild(
        iconBtn("＋", L("ui.fileLinks.create"), {
          disabled: state.busy || !state.fileLinksFileId,
          onClick: function () {
            createPublicLinkForFile(state.fileLinksFileId);
          },
        })
      );
      flContent.appendChild(flFoot);
      flCard.appendChild(flContent);
      flOv.appendChild(flCard);
      flOv.onclick = function (e) {
        if (e.target === flOv) closeFilePublicLinksModal();
      };
      shell.appendChild(flOv);
    }
    if (state.messageVersionsOpen) {
      var vOv = el("div", "settings-overlay");
      var vCard = el("div", "settings-card members-card");
      var vClose = iconBtn("✕", L("ui.common.close"), {
        primary: true,
        onClick: function () {
          closeMessageVersionsModal();
        },
      });
      vCard.appendChild(modalCardHead(L("ui.versions.title"), vClose));
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
            (formatInstantLabel ? formatInstantLabel(ver.created_at) : new Date(ver.created_at).toLocaleString()) +
            (ver.edited_by ? " · " + ver.edited_by.slice(0, 8) : "");
          vRow.appendChild(head);
          var body = el("pre", "version-row-body", ver.content || "");
          vRow.appendChild(body);
          vBody.appendChild(vRow);
        });
      } else {
        vBody.appendChild(el("p", "settings-hint", L("ui.versions.none")));
      }
      var vContent = el("div", "settings-content");
      vContent.appendChild(vBody);
      vCard.appendChild(vContent);
      vOv.appendChild(vCard);
      vOv.onclick = function (e) {
        if (e.target === vOv) closeMessageVersionsModal();
      };
      shell.appendChild(vOv);
    }
    if (state.readReceiptPopupMessageId) {
      var rrOv = el("div", "settings-overlay");
      rrOv.setAttribute("data-testid", "read-receipt-overlay");
      var rrCard = el("div", "settings-card members-card");
      var rrClose = iconBtn("✕", L("ui.common.close"), {
        testId: "read-receipt-close",
        primary: true,
        onClick: closeReadReceiptPopup,
      });
      rrCard.appendChild(modalCardHead(L("readReceipts.title"), rrClose));
      var rrBody = el("div", "settings-body members-body");
      var rr = (state.readReceiptsByMessage && state.readReceiptsByMessage[state.readReceiptPopupMessageId]) || {};
      var rrIds = Object.keys(rr).sort(function (a, b) {
        return (rr[b] || 0) - (rr[a] || 0);
      });
      if (!rrIds.length) {
        rrBody.appendChild(el("p", "settings-hint", L("readReceipts.none")));
      } else {
        rrIds.forEach(function (uid) {
          var row = el("div", "read-receipt-row");
          row.appendChild(el("span", "read-receipt-user", readReceiptUserLabel(uid)));
          row.appendChild(
            el("span", "read-receipt-time muted", formatInstantLabel(rr[uid]))
          );
          rrBody.appendChild(row);
        });
      }
      var rrContent = el("div", "settings-content");
      rrContent.appendChild(rrBody);
      rrCard.appendChild(rrContent);
      rrOv.appendChild(rrCard);
      rrOv.onclick = function (e) {
        if (e.target === rrOv) closeReadReceiptPopup();
      };
      shell.appendChild(rrOv);
    }
    mountAppNotice(shell);
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
      var composerSnapshot =
        uiComposer.captureComposerState &&
        uiComposer.captureComposerState(document, state.selectedId);
      if (composerSnapshot) {
        saveComposerDraftForChat(composerSnapshot.chatId, composerSnapshot.value);
      }
      renderMain();
      if (uiComposer.restoreComposerState) {
        uiComposer.restoreComposerState(
          document,
          composerSnapshot,
          state.selectedId,
          saveComposerDraftForChat
        );
      }
      if (state.shouldScrollThread) {
        state.shouldScrollThread = false;
        scheduleScrollMessages();
      }
    }
  }

  function stripDeepLinkFromUrl() {
    return uiDeepLinkUtils.stripDeepLinkFromUrl();
  }

  function stashPendingDeepLink(chatId, msgId) {
    uiShellUtils.stashPendingDeepLink(PENDING_CHAT_KEY, PENDING_MSG_KEY, chatId, msgId);
  }

  function consumePendingDeepLink() {
    var fromUrl = stripDeepLinkFromUrl();
    if (fromUrl.chatId || fromUrl.msgId) {
      stashPendingDeepLink(fromUrl.chatId, fromUrl.msgId);
    }
    if (fromUrl.guest) {
      try {
        sessionStorage.setItem(PENDING_GUEST_KEY, fromUrl.guest);
      } catch (e) {}
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

  function consumePendingGuestLink() {
    try {
      var guest = sessionStorage.getItem(PENDING_GUEST_KEY);
      sessionStorage.removeItem(PENDING_GUEST_KEY);
      return guest;
    } catch (e) {
      return null;
    }
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
      await loadPlatformCaps();
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
      var guestTok = consumePendingGuestLink();
      if (guestTok) {
        await redeemGuestLinkFromUrl(guestTok);
      }
      await loadMyPasskeys();
      await loadSipGateway();
      await loadFederationDirectory();
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
    if (window.KorusUiGlobalErrors) {
      window.KorusUiGlobalErrors.installGlobalErrorHandlers({
        onReport: function (kind, msg) {
          console.warn("[korus-ui]", kind, msg);
        },
      });
    }
    applyStyleSet(loadStyleSet());
    state.sidebarWidth = loadSidebarWidth();
    applySidebarWidthCss();
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
      fetchLoginOptions().finally(function () {
        updateDocumentTitle();
        render();
      });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
