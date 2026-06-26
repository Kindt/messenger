/**
 * WebSocket event shape guards (extracted from app.js).
 */
(function (global) {
  "use strict";

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

  function isMentionEvent(o) {
    return o && typeof o === "object" && o.type === "mention" && typeof o.message_id === "string";
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

  function isTypingEvent(o) {
    return (
      o &&
      typeof o === "object" &&
      typeof o.chat_id === "string" &&
      typeof o.user_id === "string" &&
      typeof o.ts === "number" &&
      !o.messageId &&
      o.type !== "read_receipt" &&
      o.type !== "presence"
    );
  }

  function isPresenceEvent(o) {
    return o && o.type === "presence" && o.user_id;
  }

  function isAvatarEvent(o) {
    return o && o.type === "avatar" && typeof o.user_id === "string";
  }

  function isChatAvatarEvent(o) {
    return o && o.type === "chat.avatar" && typeof o.chat_id === "string";
  }

  function isReadReceiptEvent(o) {
    return o && o.type === "read_receipt" && o.chat_id && o.user_id;
  }

  global.KorusUiWsEvents = {
    isMessageSendEvent: isMessageSendEvent,
    isMessageChangeEvent: isMessageChangeEvent,
    isReactionChangeEvent: isReactionChangeEvent,
    isMentionEvent: isMentionEvent,
    isPinChangeEvent: isPinChangeEvent,
    isConferenceChangeEvent: isConferenceChangeEvent,
    isTypingEvent: isTypingEvent,
    isPresenceEvent: isPresenceEvent,
    isAvatarEvent: isAvatarEvent,
    isChatAvatarEvent: isChatAvatarEvent,
    isReadReceiptEvent: isReadReceiptEvent,
  };
})(typeof window !== "undefined" ? window : globalThis);
