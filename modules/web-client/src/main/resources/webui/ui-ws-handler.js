/**
 * WebSocket inbound message dispatch (extracted from app.js handleWsIncoming).
 */
(function (global) {
  "use strict";

  function parseWsPayload(raw) {
    if (raw == null) {
      return null;
    }
    var s = typeof raw === "string" ? raw : String(raw);
    if (s.length < 2 || s.charCodeAt(0) !== 123) {
      return null;
    }
    return JSON.parse(s);
  }

  function handleWsIncoming(ev, ctx) {
    try {
      var data = parseWsPayload(ev && ev.data);
      if (!data) {
        return;
      }
      if (ctx.isCallSessionEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.handleCallSessionEvent(data);
        return;
      }
      if (data && data.type === "rtc_signal") {
        ctx.sendHeartbeatThrottled();
        ctx.handleRtcEnvelope(data);
        return;
      }
      if (ctx.isTypingEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.noteTyping(data.chat_id, data.user_id);
        ctx.scheduleTypingSidebarRefresh();
        return;
      }
      if (ctx.isPresenceEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyPresenceEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isAvatarEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyAvatarEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isChatAvatarEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyChatAvatarEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isReadReceiptEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyReadReceiptEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isMessageChangeEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyMessageChangeEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isReactionChangeEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyReactionChangeEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (ctx.isPinChangeEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyPinChangeEvent(data);
        return;
      }
      if (ctx.isMentionEvent(data)) {
        ctx.sendHeartbeatThrottled();
        var myId = ctx.jwtSub(ctx.state.tokens.access_token);
        if (myId && data.mentioned_user_id === myId) {
          ctx.maybeNotifyMention(data);
          ctx.markMentionPending(data.chat_id);
          if (data.chat_id === ctx.state.selectedId) {
            ctx.scheduleRender();
          } else {
            ctx.bumpUnread(data.chat_id);
            ctx.scheduleRender();
          }
        }
        return;
      }
      if (ctx.isConferenceChangeEvent(data)) {
        ctx.sendHeartbeatThrottled();
        ctx.applyConferenceChangeEvent(data);
        ctx.scheduleRender();
        return;
      }
      if (global.KorusUiLiveSession && global.KorusUiLiveSession.isLiveSessionChangeEvent(data)) {
        ctx.sendHeartbeatThrottled();
        global.KorusUiLiveSession.applyLiveSessionChangeEvent(ctx.state, data, {
          onCreated: function (evt) {
            ctx.state.statusMessage = evt.title
              ? ctx.L("live.createdNamed", { title: evt.title })
              : ctx.L("live.createdDefault");
          },
          onEnded: function () {
            ctx.state.statusMessage = ctx.L("live.ended");
          },
        });
        ctx.scheduleRender();
        return;
      }
      if (!ctx.isMessageSendEvent(data)) return;
      ctx.sendHeartbeatThrottled();
      ctx.setChatPreviewFromSendEvent(data);
      if (data.chatId !== ctx.state.selectedId) {
        ctx.maybeNotifyMessage(data);
        var senderId = ctx.jwtSub(ctx.state.tokens.access_token);
        if (!senderId || data.senderId !== senderId) ctx.bumpUnread(data.chatId);
        ctx.scheduleRender();
        return;
      }
      if (document.hidden) {
        ctx.maybeNotifyMessage(data);
      }
      ctx.ingestIncomingMessage(data.chatId, data.messageId, data)
        .then(function () {
          return ctx.markChatRead(data.chatId);
        })
        .then(function () {
          ctx.state.shouldScrollThread = true;
          ctx.scheduleRender();
        })
        .catch(function () {
          ctx.loadThread(data.chatId, ctx.THREAD_SOFT_RELOAD)
            .then(function () {
              return ctx.markChatRead(data.chatId);
            })
            .then(function () {
              ctx.state.shouldScrollThread = true;
              ctx.scheduleRender();
            })
            .catch(function () {});
        });
    } catch (e) {
      /* ignore malformed WS payload */
    }
  }

  global.KorusUiWsHandler = {
    handleWsIncoming: handleWsIncoming,
    parseWsPayload: parseWsPayload,
  };
})(typeof globalThis !== "undefined" ? globalThis : globalThis);
