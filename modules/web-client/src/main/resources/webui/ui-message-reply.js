/**
 * Reply quote preview block on message articles (extracted from app.js).
 */
(function (global) {
  "use strict";

  function replyPreviewData(m, ctx) {
    if (!m) return null;
    var preview = m.reply_preview || m.replyPreview;
    if (preview) {
      var mid = preview.message_id || preview.messageId || m.reply_to_msg_id;
      var senderId = preview.sender_id || preview.senderId || null;
      var deleted = !!preview.deleted;
      var snippet = deleted ? null : preview.snippet;
      var encrypted =
        !deleted && (snippet == null || snippet === "") && !!m.reply_to_msg_id;
      return {
        messageId: mid,
        senderId: senderId,
        snippet: snippet,
        deleted: deleted,
        encrypted: encrypted,
      };
    }
    if (!m.reply_to_msg_id) return null;
    var parent = ctx.findMessageInThread(m.reply_to_msg_id);
    return {
      messageId: m.reply_to_msg_id,
      senderId: parent ? parent.sender_id : null,
      snippet: ctx.replySnippetForId(m.reply_to_msg_id),
      deleted: !!(parent && parent.deleted),
      encrypted: !!(parent && ctx.isE2eeType(parent.type)),
    };
  }

  function appendReplyQuoteBlock(art, m, ctx) {
    var rp = replyPreviewData(m, ctx);
    if (!rp || !rp.messageId) return;
    var rq = ctx.el("button", "msg-reply-quote");
    rq.type = "button";
    rq.setAttribute("data-testid", "message-reply-quote");
    var quoteTitle = ctx.L("ui.message.replyQuoteTitle");
    rq.setAttribute("aria-label", quoteTitle);
    rq.title = quoteTitle;
    if (rp.senderId) {
      rq.appendChild(
        ctx.el("span", "msg-reply-quote-sender", ctx.senderLabelForUserId(rp.senderId))
      );
    }
    var sn = ctx.el("span", "msg-reply-quote-snippet");
    if (rp.deleted) {
      sn.textContent = ctx.L("ui.message.deleted");
      sn.classList.add("msg-reply-quote-deleted");
    } else if (rp.encrypted) {
      sn.textContent = ctx.L("chat.encryptedE2eePreview");
    } else {
      sn.textContent = rp.snippet || ctx.L("ui.message.default");
    }
    rq.appendChild(sn);
    rq.onclick = function () {
      if (ctx.scrollToMessageId) {
        ctx.scrollToMessageId(rp.messageId);
      }
    };
    art.appendChild(rq);
  }

  function highlightMessageElement(msgId) {
    if (!msgId) return;
    requestAnimationFrame(function () {
      var target = document.getElementById("msg-" + msgId);
      if (!target) return;
      target.scrollIntoView({ behavior: "smooth", block: "center" });
      target.classList.add("msg-highlight");
      setTimeout(function () {
        target.classList.remove("msg-highlight");
      }, 1200);
    });
  }

  global.KorusUiMessageReply = {
    replyPreviewData: replyPreviewData,
    appendReplyQuoteBlock: appendReplyQuoteBlock,
    highlightMessageElement: highlightMessageElement,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
