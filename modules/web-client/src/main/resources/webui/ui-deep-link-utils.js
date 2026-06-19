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
        !params.has("conf")
      ) {
        return null;
      }
      return {
        chatId: params.has("chat") ? params.get("chat") : null,
        msgId: params.has("msg") ? params.get("msg") : null,
        meet: params.has("meet") ? params.get("meet") : null,
        conf: params.has("conf") ? params.get("conf") : null,
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
      var changed =
        params.has("chat") ||
        params.has("msg") ||
        params.has("meet") ||
        params.has("conf");
      if (params.has("chat")) params.delete("chat");
      if (params.has("msg")) params.delete("msg");
      if (params.has("meet")) params.delete("meet");
      if (params.has("conf")) params.delete("conf");
      if (changed && window.history && window.history.replaceState) {
        var q = params.toString();
        var path = window.location.pathname + (q ? "?" + q : "");
        window.history.replaceState(null, "", path);
      }
      if (!chatId && !msgId && !meet && !conf) {
        var fromHash = parseHashDeepLink();
        if (fromHash) {
          chatId = fromHash.chatId;
          msgId = fromHash.msgId;
          meet = fromHash.meet;
          conf = fromHash.conf;
          clearHashDeepLink();
        }
      }
      return { chatId: chatId, msgId: msgId, meet: meet, conf: conf };
    } catch (e) {
      return { chatId: null, msgId: null, meet: null, conf: null };
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

  global.KorusUiDeepLinkUtils = {
    stripDeepLinkFromUrl: stripDeepLinkFromUrl,
    buildChatUrl: buildChatUrl,
    buildMessageUrl: buildMessageUrl,
    appBaseUrl: appBaseUrl,
  };
})(typeof window !== "undefined" ? window : globalThis);
