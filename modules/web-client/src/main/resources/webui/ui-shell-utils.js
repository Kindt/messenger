(function (global) {
  "use strict";

  function loadStyleSet(styleKey, themeKey, palette) {
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
  }

  function saveStyleSet(styleKey, themeKey, appearance, palette) {
    try {
      localStorage.setItem(
        styleKey,
        JSON.stringify({ appearance: appearance, palette: palette })
      );
      localStorage.setItem(themeKey, appearance);
    } catch (e) {}
  }

  function applyStyleSet(doc, set, palette) {
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
  }

  function syncNotifyPref(notifKey, soundNotifKey) {
    return {
      notifyPref:
        localStorage.getItem(notifKey) === "1" &&
        typeof Notification !== "undefined" &&
        Notification.permission === "granted",
      soundNotify: localStorage.getItem(soundNotifKey) === "1",
    };
  }

  function draftStorageKey(prefix, chatId) {
    return prefix + chatId;
  }

  function loadComposerDraftForChat(prefix, chatId) {
    if (!chatId) return "";
    try {
      return localStorage.getItem(draftStorageKey(prefix, chatId)) || "";
    } catch (e) {
      return "";
    }
  }

  function saveComposerDraftForChat(prefix, chatId, text) {
    if (!chatId) return;
    try {
      var key = draftStorageKey(prefix, chatId);
      if (text && String(text).trim()) {
        localStorage.setItem(key, text);
      } else {
        localStorage.removeItem(key);
      }
    } catch (e) {}
  }

  function clearComposerDraftForChat(prefix, chatId) {
    if (!chatId) return;
    try {
      localStorage.removeItem(draftStorageKey(prefix, chatId));
    } catch (e) {}
  }

  function composerDraftPreview(prefix, chatId) {
    var draft = loadComposerDraftForChat(prefix, chatId);
    if (!draft || !String(draft).trim()) return "";
    var text = String(draft).trim().replace(/\s+/g, " ");
    if (text.length > 48) text = text.slice(0, 48) + "…";
    return text;
  }

  function loadLastPublicLink(storageKey) {
    try {
      var raw = sessionStorage.getItem(storageKey);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (parsed && parsed.file_id && parsed.link_id) return parsed;
    } catch (e) {}
    return null;
  }

  function saveLastPublicLink(storageKey, link) {
    try {
      if (link && link.file_id && link.link_id) {
        sessionStorage.setItem(storageKey, JSON.stringify(link));
      } else {
        sessionStorage.removeItem(storageKey);
      }
    } catch (e) {}
  }

  function stashPendingDeepLink(chatStorageKey, msgStorageKey, chatId, msgId) {
    try {
      if (chatId) sessionStorage.setItem(chatStorageKey, chatId);
      if (msgId) sessionStorage.setItem(msgStorageKey, msgId);
    } catch (e) {}
  }

  function readAndClearPendingDeepLink(chatStorageKey, msgStorageKey) {
    var chatId = null;
    var msgId = null;
    try {
      chatId = sessionStorage.getItem(chatStorageKey);
      msgId = sessionStorage.getItem(msgStorageKey);
      if (chatId) sessionStorage.removeItem(chatStorageKey);
      if (msgId) sessionStorage.removeItem(msgStorageKey);
    } catch (e) {}
    return { chatId: chatId, msgId: msgId };
  }

  global.KorusUiShellUtils = {
    loadStyleSet: loadStyleSet,
    saveStyleSet: saveStyleSet,
    applyStyleSet: applyStyleSet,
    syncNotifyPref: syncNotifyPref,
    draftStorageKey: draftStorageKey,
    loadComposerDraftForChat: loadComposerDraftForChat,
    saveComposerDraftForChat: saveComposerDraftForChat,
    clearComposerDraftForChat: clearComposerDraftForChat,
    composerDraftPreview: composerDraftPreview,
    loadLastPublicLink: loadLastPublicLink,
    saveLastPublicLink: saveLastPublicLink,
    stashPendingDeepLink: stashPendingDeepLink,
    readAndClearPendingDeepLink: readAndClearPendingDeepLink,
  };
})(typeof window !== "undefined" ? window : globalThis);
