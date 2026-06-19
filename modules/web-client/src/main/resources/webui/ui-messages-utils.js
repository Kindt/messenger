(function (global) {
  "use strict";

  function formatPreviewText(type, content, isE2eeType, e2eePlainType, L) {
    var t = type || "text";
    var translate = L || function (key) {
      return key;
    };
    if (isE2eeType(t)) {
      var base = e2eePlainType(t);
      if (base === "image") return translate("ui.message.e2eeImage");
      if (base === "video") return translate("ui.message.e2eeVideo");
      if (base === "file") return translate("ui.message.e2eeFile");
      return translate("ui.message.e2eeEncrypted");
    }
    if (t === "image") return translate("ui.message.image");
    if (t === "video") return translate("ui.message.video");
    if (t === "file") return translate("ui.message.file");
    var text = String(content || "")
      .replace(/[*_`#[\]]/g, "")
      .replace(/\s+/g, " ")
      .trim();
    if (text.length > 72) text = text.slice(0, 72) + "…";
    return text || translate("ui.message.default");
  }

  function formatPreviewForMessage(
    message,
    messageAttachmentKind,
    messageAttachmentFileId,
    formatPreviewTextFn,
    defaultLabel
  ) {
    if (!message) return defaultLabel || "Message";
    if (messageAttachmentKind(message) && messageAttachmentFileId(message)) {
      return formatPreviewTextFn(message.type, "");
    }
    return formatPreviewTextFn(message.type, message.content);
  }

  function sortMessagesAsc(rows) {
    return (rows || []).slice().sort(function (a, b) {
      return new Date(a.created_at) - new Date(b.created_at);
    });
  }

  function findMessageInThread(messages, msgId) {
    if (!msgId || !messages || !messages.length) return null;
    for (var i = 0; i < messages.length; i++) {
      if (messages[i].id === msgId) return messages[i];
    }
    return null;
  }

  function mergeMessageIntoThread(messages, fullMessage) {
    if (!fullMessage || !fullMessage.id) return messages || [];
    var rows = messages || [];
    if (findMessageInThread(rows, fullMessage.id)) {
      return rows.map(function (m) {
        return m.id === fullMessage.id ? fullMessage : m;
      });
    }
    return sortMessagesAsc(rows.concat([fullMessage]));
  }

  function patchMessageInThread(messages, messageId, patch) {
    if (!messageId || !patch) {
      return { messages: messages || [], touched: false };
    }
    var touched = false;
    var nextMessages = (messages || []).map(function (m) {
      if (m.id !== messageId) return m;
      touched = true;
      return Object.assign({}, m, patch);
    });
    return { messages: nextMessages, touched: touched };
  }

  function applyReactionChangeEventRows(rows, change, userId, reaction) {
    var nextRows = (rows || []).slice();
    if (change === "add") {
      var exists = nextRows.some(function (r) {
        return r.user_id === userId && r.reaction === reaction;
      });
      if (!exists) {
        nextRows.push({ user_id: userId, reaction: reaction });
      }
      return nextRows;
    }
    return nextRows.filter(function (r) {
      return !(r.user_id === userId && r.reaction === reaction);
    });
  }

  function messageTypeForMime(mime) {
    if (mime && mime.indexOf("image/") === 0) return "image";
    if (mime && mime.indexOf("video/") === 0) return "video";
    if (mime && mime.indexOf("audio/") === 0) return "audio";
    return "file";
  }

  function revokeBlobUrls(blobUrls) {
    (blobUrls || []).forEach(function (u) {
      try {
        URL.revokeObjectURL(u);
      } catch (e) {}
    });
    return [];
  }

  global.KorusUiMessagesUtils = {
    formatPreviewText: formatPreviewText,
    formatPreviewForMessage: formatPreviewForMessage,
    sortMessagesAsc: sortMessagesAsc,
    findMessageInThread: findMessageInThread,
    mergeMessageIntoThread: mergeMessageIntoThread,
    patchMessageInThread: patchMessageInThread,
    applyReactionChangeEventRows: applyReactionChangeEventRows,
    messageTypeForMime: messageTypeForMime,
    revokeBlobUrls: revokeBlobUrls,
  };
})(typeof window !== "undefined" ? window : globalThis);
