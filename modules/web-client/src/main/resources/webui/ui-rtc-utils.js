(function (global) {
  "use strict";

  function sendRtcSignal(ws, chatId, payload) {
    if (!ws || ws.readyState !== WebSocket.OPEN || !chatId || !payload) return false;
    ws.send(
      JSON.stringify({
        type: "rtc_signal",
        chatId: chatId,
        payload: payload,
      })
    );
    return true;
  }

  function sendRtcHangups(ws, chatId, peerIds) {
    if (!ws || ws.readyState !== WebSocket.OPEN || !chatId) return;
    (peerIds || []).forEach(function (peerId) {
      if (!peerId) return;
      sendRtcSignal(ws, chatId, { kind: "hangup", targetUserId: peerId });
    });
  }

  global.KorusUiRtcUtils = {
    sendRtcSignal: sendRtcSignal,
    sendRtcHangups: sendRtcHangups,
  };
})(typeof window !== "undefined" ? window : globalThis);
