/**
 * Message body rendering: attachments, link preview, E2EE decrypt, markdown text.
 */
(function (global) {
  "use strict";

  function appendMessageAttachment(bodyEl, kind, fileId, durationMs, ctx) {
    if (kind === "image") {
      var wrap = document.createElement("div");
      var img = document.createElement("img");
      img.alt = ctx.L("ui.message.image");
      img.loading = "lazy";
      img.decoding = "async";
      if (global.KorusUiUxPerception && global.KorusUiUxPerception.prepareImageAttachmentWrap) {
        global.KorusUiUxPerception.prepareImageAttachmentWrap(wrap, img);
      } else {
        img.className = "msg-attachment-image";
      }
      wrap.appendChild(img);
      bodyEl.appendChild(wrap);
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

  function parseJsonContent(raw) {
    if (!raw || typeof raw !== "string") return null;
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function renderLocationMessage(bodyEl, m, ctx) {
    var loc = parseJsonContent(m.content);
    if (!loc || loc.lat == null || loc.lon == null) {
      bodyEl.appendChild(ctx.el("span", "msg-location-invalid", m.content || ""));
      return;
    }
    var lat = Number(loc.lat);
    var lon = Number(loc.lon);
    var href =
      "https://www.openstreetmap.org/?mlat=" +
      encodeURIComponent(lat) +
      "&mlon=" +
      encodeURIComponent(lon) +
      "#map=16/" +
      lat +
      "/" +
      lon;
    var link = document.createElement("a");
    link.className = "msg-location-link";
    link.href = href;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.setAttribute("data-testid", "message-location-link");
    link.textContent = loc.label || ctx.L("ui.message.locationOpen");
    bodyEl.appendChild(link);
  }

  function renderContactMessage(bodyEl, m, ctx) {
    var contact = parseJsonContent(m.content);
    if (!contact) {
      bodyEl.appendChild(ctx.el("span", "msg-contact-invalid", m.content || ""));
      return;
    }
    var card = ctx.el("div", "msg-contact-card");
    card.setAttribute("data-testid", "message-contact-card");
    if (contact.display_name) {
      card.appendChild(ctx.el("div", "msg-contact-name", contact.display_name));
    }
    if (contact.phone) {
      var phone = document.createElement("a");
      phone.className = "msg-contact-phone";
      phone.href = "tel:" + String(contact.phone).replace(/\s/g, "");
      phone.textContent = contact.phone;
      phone.setAttribute("data-testid", "message-contact-phone");
      card.appendChild(phone);
    }
    if (contact.email) {
      var email = document.createElement("a");
      email.className = "msg-contact-email";
      email.href = "mailto:" + contact.email;
      email.textContent = contact.email;
      email.setAttribute("data-testid", "message-contact-email");
      card.appendChild(email);
    }
    if (!card.childNodes.length) {
      card.appendChild(ctx.el("span", "msg-contact-empty", m.content || ""));
    }
    bodyEl.appendChild(card);
  }

  function renderMessageContent(bodyEl, m, ctx) {
    var t = m.type;
    var attachKind = ctx.messageAttachmentKind(m);
    var fileId = ctx.messageAttachmentFileId(m);
    if (attachKind && fileId) {
      appendMessageAttachment(bodyEl, attachKind, fileId, m.duration_ms, ctx);
      return;
    }
    if (t === "location") {
      renderLocationMessage(bodyEl, m, ctx);
      return;
    }
    if (t === "contact") {
      renderContactMessage(bodyEl, m, ctx);
      return;
    }
    if (t === "gif" || t === "sticker") {
      var mediaUrl = (m.content || "").trim();
      if (/^https?:\/\//i.test(mediaUrl)) {
        var gifImg = document.createElement("img");
        gifImg.className = "msg-gif-image";
        gifImg.src = mediaUrl;
        gifImg.loading = "lazy";
        gifImg.decoding = "async";
        gifImg.alt = t === "sticker" ? ctx.L("ui.message.sticker") : "GIF";
        gifImg.setAttribute("data-testid", "message-gif-image");
        bodyEl.appendChild(gifImg);
        return;
      }
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
