/**
 * WebSocket client lifecycle for Korus webui (extracted from app.js).
 */
(function (global) {
  "use strict";

  function createWsClient(options) {
    options = options || {};
    var getState = options.getState || function () {
      return {};
    };
    var getAccessToken = options.getAccessToken || function () {
      return null;
    };
    var hasSession = options.hasSession || function () {
      return false;
    };
    var buildWsUrl = options.buildWsUrl || function () {
      return "";
    };
    var onOpen = options.onOpen || function () {};
    var onMessage = options.onMessage || function () {};
    var onStateChange = options.onStateChange || function () {};
    var onBeforeClose = options.onBeforeClose || function () {};
    var transport = global.KorusUiTransportUtils;

    function clearReconnect(st) {
      if (st.wsReconnectTimer) {
        clearTimeout(st.wsReconnectTimer);
        st.wsReconnectTimer = null;
      }
    }

    function scheduleReconnect(st) {
      clearReconnect(st);
      if (st.wsManualClose || !hasSession()) {
        return;
      }
      st.wsState = "offline";
      onStateChange("offline");
      var delay = transport.nextWsReconnectDelay(st.wsReconnectAttempt);
      st.wsReconnectAttempt += 1;
      st.wsReconnectTimer = setTimeout(function () {
        st.wsReconnectTimer = null;
        if (st.wsManualClose || !hasSession()) {
          return;
        }
        open();
      }, delay);
    }

    function replaceSocket(st) {
      if (!st.ws) {
        return;
      }
      st.wsReplacing = true;
      try {
        st.ws.close();
      } catch (e) {}
      st.ws = null;
      st.wsReplacing = false;
    }

    function close() {
      var st = getState();
      st.wsManualClose = true;
      clearReconnect(st);
      st.wsReconnectAttempt = 0;
      onBeforeClose();
      replaceSocket(st);
      st.wsState = "off";
      onStateChange("off");
    }

    function open(opts) {
      opts = opts || {};
      var st = getState();
      if (
        !opts.force &&
        st.ws &&
        (st.ws.readyState === WebSocket.OPEN || st.ws.readyState === WebSocket.CONNECTING)
      ) {
        return;
      }
      clearReconnect(st);
      replaceSocket(st);
      var token = getAccessToken();
      if (!token) {
        return;
      }
      st.wsManualClose = false;
      st.wsState = "connecting";
      onStateChange("connecting");
      var ws = new WebSocket(buildWsUrl(token));
      st.ws = ws;
      ws.onopen = function () {
        st.wsReconnectAttempt = 0;
        st.wsState = "open";
        onStateChange("open");
        onOpen();
      };
      ws.onerror = function () {
        st.wsState = "error";
        onStateChange("error");
      };
      ws.onclose = function () {
        if (st.wsReplacing) {
          return;
        }
        st.ws = null;
        if (st.wsManualClose || !hasSession()) {
          st.wsState = "off";
          onStateChange("off");
        } else {
          scheduleReconnect(st);
        }
      };
      ws.onmessage = function (ev) {
        onMessage(ev);
      };
    }

    function reconnectNow() {
      var st = getState();
      clearReconnect(st);
      st.wsReconnectAttempt = 0;
      open({ force: true });
    }

    return {
      open: open,
      close: close,
      clearReconnect: function () {
        clearReconnect(getState());
      },
      reconnectNow: reconnectNow,
    };
  }

  global.KorusUiWsClient = {
    createWsClient: createWsClient,
  };
})(typeof window !== "undefined" ? window : globalThis);
