/**
 * Deep-link URL parsing and building (chat/msg/meet/conf query + hash fallback).
 */
(function (global) {
  "use strict";

  function parseHashDeepLink() {
    try {
      var hash = (window.location.hash || "").replace(/^#/, "");
      if (!hash) return null;
      var params = new URLSearchParams(hash);
      if (
        !params.has("chat") &&
        !params.has("msg") &&
        !params.has("meet") &&
        !params.has("conf") &&
        !params.has("mesh_session")
      ) {
        return null;
      }
      return {
        chatId: params.has("chat") ? params.get("chat") : null,
        msgId: params.has("msg") ? params.get("msg") : null,
        meet: params.has("meet") ? params.get("meet") : null,
        conf: params.has("conf") ? params.get("conf") : null,
        meshSession: params.has("mesh_session") ? params.get("mesh_session") : null,
        meshMode: params.has("mesh_mode") ? params.get("mesh_mode") : null,
      };
    } catch (e) {
      return null;
    }
  }

  function clearHashDeepLink() {
    try {
      if (window.location.hash && window.history && window.history.replaceState) {
        window.history.replaceState(
          null,
          "",
          window.location.pathname + window.location.search
        );
      }
    } catch (e) {}
  }

  function stripDeepLinkFromUrl() {
    try {
      var params = new URLSearchParams(window.location.search);
      var chatId = params.has("chat") ? params.get("chat") : null;
      var msgId = params.has("msg") ? params.get("msg") : null;
      var meet = params.has("meet") ? params.get("meet") : null;
      var conf = params.has("conf") ? params.get("conf") : null;
      var meshSession = params.has("mesh_session") ? params.get("mesh_session") : null;
      var meshMode = params.has("mesh_mode") ? params.get("mesh_mode") : null;
      var guest = params.has("guest") ? params.get("guest") : null;
      var changed =
        params.has("chat") ||
        params.has("msg") ||
        params.has("meet") ||
        params.has("conf") ||
        params.has("mesh_session") ||
        params.has("mesh_mode") ||
        params.has("guest");
      if (params.has("chat")) params.delete("chat");
      if (params.has("msg")) params.delete("msg");
      if (params.has("meet")) params.delete("meet");
      if (params.has("conf")) params.delete("conf");
      if (params.has("mesh_session")) params.delete("mesh_session");
      if (params.has("mesh_mode")) params.delete("mesh_mode");
      if (params.has("guest")) params.delete("guest");
      if (changed && window.history && window.history.replaceState) {
        var q = params.toString();
        var path = window.location.pathname + (q ? "?" + q : "");
        window.history.replaceState(null, "", path);
      }
      if (!chatId && !msgId && !meet && !conf && !guest && !meshSession) {
        var fromHash = parseHashDeepLink();
        if (fromHash) {
          chatId = fromHash.chatId;
          msgId = fromHash.msgId;
          meet = fromHash.meet;
          conf = fromHash.conf;
          meshSession = fromHash.meshSession;
          meshMode = fromHash.meshMode;
          clearHashDeepLink();
        }
      }
      return {
        chatId: chatId,
        msgId: msgId,
        meet: meet,
        conf: conf,
        guest: guest,
        meshSession: meshSession,
        meshMode: meshMode,
      };
    } catch (e) {
      return { chatId: null, msgId: null, meet: null, conf: null, guest: null };
    }
  }

  function appBaseUrl() {
    return window.location.origin + window.location.pathname;
  }

  function buildChatUrl(chatId) {
    if (!chatId) return appBaseUrl();
    return appBaseUrl() + "?chat=" + encodeURIComponent(chatId);
  }

  function buildMessageUrl(chatId, messageId) {
    if (!chatId || !messageId) return buildChatUrl(chatId);
    return buildChatUrl(chatId) + "&msg=" + encodeURIComponent(messageId);
  }

  function syncChatUrl(chatId, messageId) {
    try {
      if (!window.history || !window.history.replaceState) return;
      var url = messageId ? buildMessageUrl(chatId, messageId) : buildChatUrl(chatId);
      var cur = window.location.pathname + window.location.search;
      var target = url.replace(window.location.origin, "");
      if (cur !== target) {
        window.history.replaceState({ chatId: chatId, msgId: messageId || null }, "", target);
      }
    } catch (e) {}
  }

  function buildMeshCallUrl(chatId, sessionId, mediaMode) {
    if (!chatId || !sessionId) return buildChatUrl(chatId);
    var mode = mediaMode === "video" ? "video" : "audio";
    return (
      buildChatUrl(chatId) +
      "&mesh_session=" +
      encodeURIComponent(sessionId) +
      "&mesh_mode=" +
      mode
    );
  }

  global.KorusUiDeepLinkUtils = {
    stripDeepLinkFromUrl: stripDeepLinkFromUrl,
    buildChatUrl: buildChatUrl,
    buildMessageUrl: buildMessageUrl,
    buildMeshCallUrl: buildMeshCallUrl,
    appBaseUrl: appBaseUrl,
    syncChatUrl: syncChatUrl,
  };
})(typeof window !== "undefined" ? window : globalThis);
