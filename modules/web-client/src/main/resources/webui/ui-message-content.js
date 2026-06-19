/**
 * Message body rendering: attachments, link preview, E2EE decrypt, markdown text.
 */
(function (global) {
  "use strict";

  function appendMessageAttachment(bodyEl, kind, fileId, durationMs, ctx) {
    if (kind === "image") {
      var img = document.createElement("img");
      img.className = "msg-attachment-image";
      img.alt = ctx.L("ui.message.image");
      bodyEl.appendChild(img);
      ctx.attachAuthenticatedImage(fileId, img);
      return;
    }
    if (kind === "voice" || kind === "audio") {
      var audio = document.createElement("audio");
      audio.className = "msg-voice-player";
      audio.controls = true;
      audio.setAttribute("data-testid", "message-voice-player");
      if (durationMs) {
        audio.title = Math.round(durationMs / 1000) + "s";
      }
      bodyEl.appendChild(audio);
      ctx.attachAuthenticatedAudio(fileId, audio);
      return;
    }
    var label = kind === "video" ? ctx.L("ui.message.video") : ctx.L("ui.message.file");
    var btn = ctx.iconBtn("⬇", label, {
      cls: "msg-attachment-dl",
      testId: "message-file-download",
      onClick: function () {
        ctx.downloadChatFile(fileId).catch(function (err) {
          ctx.state.error = err.message || ctx.L("files.downloadFailedShort");
          ctx.render();
        });
      },
    });
    bodyEl.appendChild(btn);
  }

  function renderMessageContent(bodyEl, m, ctx) {
    var t = m.type;
    var attachKind = ctx.messageAttachmentKind(m);
    var fileId = ctx.messageAttachmentFileId(m);
    if (attachKind && fileId) {
      appendMessageAttachment(bodyEl, attachKind, fileId, m.duration_ms, ctx);
      return;
    }
    if (m.link_preview && m.link_preview.url) {
      var lp = ctx.el("div", "msg-link-preview");
      var lpTitle = m.link_preview.title || m.link_preview.url;
      lp.appendChild(ctx.el("div", "msg-link-preview-title", lpTitle));
      lp.appendChild(ctx.el("div", "msg-link-preview-url", m.link_preview.url));
      bodyEl.appendChild(lp);
    }
    if (ctx.isE2eeType(t)) {
      var chatId = m.chat_id || ctx.state.selectedId;
      var p = ctx.el(
        "p",
        "msg-e2ee-body",
        ctx.isMlsCapabilitiesActive() ? ctx.L("e2ee.decryptingMls") : ctx.L("e2ee.decrypting")
      );
      bodyEl.appendChild(p);
      ctx.loadE2eePlaintext(chatId, m.id).then(function (text) {
        if (text) {
          p.textContent = text;
          p.className = "msg-e2ee-body msg-e2ee-decrypted";
        } else if (ctx.isMlsCapabilitiesActive()) {
          p.textContent = ctx.L("e2ee.encryptedMlsPreview");
        } else {
          p.textContent = ctx.L("e2ee.encryptedE2eePreview");
        }
      });
      return;
    }
    bodyEl.innerHTML = ctx.safeMarkdown(m.content || "");
  }

  global.KorusUiMessageContent = {
    appendMessageAttachment: appendMessageAttachment,
    renderMessageContent: renderMessageContent,
  };
})(typeof window !== "undefined" ? window : globalThis);
