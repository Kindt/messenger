(function (global) {
  "use strict";

  /**
   * Legacy E2EE plaintext load + MLS client decrypt orchestration (no plaintext-preview when MLS active).
   */
  function createUiE2eeUtils(deps) {
    var getState = deps.getState;
    var setStateField = deps.setStateField;
    var apiJson = deps.apiJson;
    var isMlsActive = deps.isMlsActive;
    var mlsClientDecrypt = deps.mlsClientDecrypt;
    var findCachedMessage = deps.findCachedMessage;

    async function loadE2eePlaintext(chatId, msgId) {
      var state = getState();
      if (!chatId || !msgId) return null;
      if (!state.e2eePlaintextCache) {
        state.e2eePlaintextCache = {};
      }
      if (state.e2eePlaintextCache[msgId]) {
        return state.e2eePlaintextCache[msgId];
      }
      if (isMlsActive()) {
        var msg = findCachedMessage(chatId, msgId);
        if (msg && msg.content) {
          var clientPlain = await mlsClientDecrypt(msg.content, chatId);
          if (clientPlain) {
            setStateField("e2eePlaintextCache", msgId, clientPlain);
            return clientPlain;
          }
        }
        return null;
      }
      try {
        var r = await apiJson(
          "/chats/" + chatId + "/messages/" + msgId + "/plaintext-preview",
          { method: "GET" }
        );
        var text = r && r.plaintext ? String(r.plaintext) : null;
        if (text) setStateField("e2eePlaintextCache", msgId, text);
        return text;
      } catch (e) {
        return null;
      }
    }

    return {
      loadE2eePlaintext: loadE2eePlaintext,
    };
  }

  global.KorusUiE2eeUtils = createUiE2eeUtils;
})(typeof window !== "undefined" ? window : globalThis);
