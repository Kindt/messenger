/**
 * Single message article DOM builder (extracted from app.js renderMain).
 * ctx carries app callbacks and state slices — keeps app.js orchestration thin.
 */
(function (global) {
  "use strict";

  function buildMessageArticle(m, ctx) {
    var art = ctx.el(
      "article",
      "msg" +
        (ctx.myId && m.sender_id === ctx.myId ? " own" : "") +
        (ctx.isMessagePinned(m.id) ? " pinned" : "") +
        (m.deleted ? " deleted" : "") +
        (ctx.messageMentionsMe(m) ? " msg-mention-me" : "")
    );
    art.id = "msg-" + m.id;
    var meta = ctx.el("div", "msg-meta");
    meta.appendChild(
      document.createTextNode(
        ctx.myId && m.sender_id === ctx.myId
          ? ctx.L("ui.thread.you")
          : m.sender_id.slice(0, 8)
      )
    );
    var ts = ctx.el("span");
    ts.className = "msg-ts";
    ts.textContent = new Date(m.created_at).toLocaleString();
    meta.appendChild(ts);
    if (m.thread_reply_count && m.thread_reply_count > 0 && !ctx.state.discussionThreadRootId) {
      var tBadge = ctx.el("button", "msg-thread-badge", m.thread_reply_count + " ↩");
      tBadge.type = "button";
      tBadge.title = ctx.L("ui.thread.openDiscussion");
      tBadge.onclick = function (ev) {
        ev.stopPropagation();
        ctx.openDiscussionThread(m.id);
      };
      meta.appendChild(tBadge);
    }
    if (m.edited_at) {
      var ed = ctx.el("button", "msg-edited");
      ed.type = "button";
      ed.textContent = ctx.L("ui.message.editedShort");
      ed.title = ctx.L("ui.message.editHistoryTitle");
      ed.onclick = function () {
        ctx.openMessageVersions(m);
      };
      meta.appendChild(ed);
    }
    if (ctx.myId && m.sender_id === ctx.myId) {
      var rr = ctx.state.readReceiptsByMessage[m.id];
      var rrCount = rr ? Object.keys(rr).length : 0;
      if (rrCount > 0) {
        var rrEl = ctx.el("span", "msg-read-receipt-double-check", " ✓✓");
        rrEl.title = ctx.L("readReceipts.title") + ": " + rrCount;
        rrEl.style.cursor = "pointer";
        rrEl.onclick = function (ev) {
          ev.stopPropagation();
          ctx.showReadReceiptPopup(m.id);
        };
        meta.appendChild(rrEl);
      }
    }
    var ttlSeconds = ctx.messageVisibilityTtlSeconds(m);
    var expiresAt = ctx.messageExpiryEpochMs(m);
    var isExpired = expiresAt != null && Date.now() >= expiresAt;
    if (ttlSeconds) {
      var ttlLbl = ctx.el("span");
      ttlLbl.className = "msg-ttl msg-ttl-indicator" + (isExpired ? " msg-ttl-expired" : "");
      if (isExpired) {
        ttlLbl.textContent = ctx.L("ui.message.ttlExpiredLabel");
        ttlLbl.title = ctx.L("ui.message.ttlExpiredTitle");
      } else {
        var leftSeconds = Math.max(1, Math.ceil((expiresAt - Date.now()) / 1000));
        ttlLbl.textContent = " · ⏱ " + ctx.formatTimeLeft(leftSeconds);
        ttlLbl.title = ctx.L("ui.message.ttlExpiresIn", {
          time: ctx.formatTimeLeft(leftSeconds),
        });
      }
      meta.appendChild(ttlLbl);
    }
    art.appendChild(meta);
    if (m.reply_to_msg_id) {
      ctx.appendReplyQuoteBlock(art, m);
    }
    if (!m.deleted && (m.type !== "text" || ctx.isE2eeType(m.type))) {
      var typeLbl = ctx.isE2eeType(m.type)
        ? "e2ee · " + ctx.e2eePlainType(m.type)
        : m.type;
      art.appendChild(
        ctx.el(
          "div",
          "msg-type" + (ctx.isE2eeType(m.type) ? " msg-type-e2ee" : ""),
          typeLbl
        )
      );
    }
    var body = ctx.el("div", "msg-body md");
    if (m.deleted || isExpired) {
      body.className = "msg-body msg-deleted-body";
      body.textContent = isExpired
        ? ctx.L("ui.message.unavailableTtl")
        : ctx.L("ui.message.deleted");
    } else {
      ctx.renderMessageContent(body, m);
    }
    art.appendChild(body);
    var agg = ctx.aggregateReactions(m.id, ctx.myId);
    var emojis = Object.keys(agg);
    if (emojis.length) {
      var reactBar = ctx.el("div", "msg-reactions");
      emojis.forEach(function (em) {
        var chip = ctx.el(
          "button",
          "msg-reaction-chip" + (agg[em].mine ? " mine" : ""),
          em + " " + agg[em].count
        );
        chip.type = "button";
        chip.onclick = function () {
          ctx.toggleReaction(m.id, em).catch(function (err) {
            ctx.state.error = err.message || ctx.L("messages.reactionFailed");
            ctx.render();
          });
        };
        reactBar.appendChild(chip);
      });
      art.appendChild(reactBar);
    }
    if (!m.deleted) {
      var addReact = ctx.iconBtn("+", ctx.L("ui.message.addReaction"), {
        testId: "message-reaction-picker-btn",
        onClick: function (ev) {
          ev.stopPropagation();
          var pop = ctx.el("div", "msg-reaction-picker");
          ctx.reactionPickerEmojis.forEach(function (em) {
            var b = ctx.el("button", "msg-reaction-picker-item", em);
            b.type = "button";
            b.onclick = function () {
              ctx.toggleReaction(m.id, em).catch(function (err) {
                ctx.state.error = err.message || ctx.L("messages.reactionFailed");
                ctx.render();
              });
              if (pop.parentNode) pop.parentNode.removeChild(pop);
            };
            pop.appendChild(b);
          });
          document.body.appendChild(pop);
          var rect = ev.target.getBoundingClientRect();
          pop.style.position = "fixed";
          pop.style.left = Math.max(8, rect.left) + "px";
          pop.style.top = rect.bottom + 4 + "px";
          setTimeout(function () {
            document.addEventListener(
              "click",
              function closePop() {
                if (pop.parentNode) pop.parentNode.removeChild(pop);
              },
              { once: true }
            );
          }, 0);
        },
      });
      var actions = ctx.el("div", "msg-actions");
      actions.appendChild(addReact);
      actions.appendChild(
        ctx.iconBtn("↩", ctx.L("ui.actions.reply"), {
          testId: "message-reply-button",
          onClick: function () {
            ctx.setReplyTo(m);
          },
        })
      );
      if (
        m.type === "text" ||
        ctx.isE2eeType(m.type) ||
        (m.content && m.content.trim())
      ) {
        actions.appendChild(
          ctx.iconBtn("📋", ctx.L("ui.actions.copy"), {
            onClick: function () {
              ctx.copyMessageText(m);
            },
          })
        );
      }
      actions.appendChild(
        ctx.iconBtn("🔗", ctx.L("ui.message.messageLinkTitle"), {
          testId: "message-link-button",
          onClick: function () {
            ctx.copyMessageDeepLink(m);
          },
        })
      );
      var attachId = ctx.messageAttachmentFileId(m);
      if (attachId) {
        actions.appendChild(
          ctx.iconBtn("⬇", ctx.L("ui.common.download"), {
            onClick: function () {
              ctx.downloadChatFile(attachId).catch(function (err) {
                ctx.state.error = err.message || ctx.L("files.downloadFailedShort");
                ctx.render();
              });
            },
          })
        );
      }
      actions.appendChild(
        ctx.iconBtn(
          ctx.isMessagePinned(m.id) ? "📍" : "📌",
          ctx.isMessagePinned(m.id) ? ctx.L("ui.message.unpin") : ctx.L("ui.message.pin"),
          {
            onClick: function () {
              ctx.togglePinMessage(m).catch(function (err) {
                ctx.state.error = err.message || ctx.L("messages.pinFailed");
                ctx.render();
              });
            },
          }
        )
      );
      actions.appendChild(
        ctx.iconBtn("↪", ctx.L("ui.actions.forward"), {
          testId: "message-forward-button",
          onClick: function () {
            ctx.openForwardPicker(m);
          },
        })
      );
      if (ctx.myId && m.sender_id === ctx.myId && ctx.messageAttachmentFileId(m)) {
        var fileId = ctx.messageAttachmentFileId(m);
        actions.appendChild(
          ctx.iconBtn("🌐", ctx.L("ui.message.pubLinkTitle"), {
            onClick: function () {
              ctx.createPublicLinkForFile(fileId);
            },
          })
        );
        actions.appendChild(
          ctx.iconBtn("🔗", ctx.L("ui.message.linksTitle"), {
            onClick: function () {
              ctx.openFilePublicLinksModal(fileId);
            },
          })
        );
        actions.appendChild(
          ctx.iconBtn("🗑", ctx.L("ui.actions.deleteFile"), {
            onClick: function () {
              ctx.deleteOwnFile(fileId);
            },
          })
        );
      }
      if (ctx.state.savedChatId && ctx.state.selectedId !== ctx.state.savedChatId) {
        actions.appendChild(
          ctx.iconBtn("🔒", ctx.L("ui.actions.toVault"), {
            onClick: function () {
              ctx.saveMessageToVault(m).catch(function (err) {
                ctx.state.error = err.message || ctx.L("saved.saveFailed");
                ctx.render();
              });
            },
          })
        );
      }
      if (ctx.myId && m.sender_id === ctx.myId && m.type === "text") {
        actions.appendChild(
          ctx.iconBtn("✎", ctx.L("ui.actions.edit"), {
            testId: "message-edit-button",
            onClick: function () {
              ctx.editMessagePrompt(m).catch(function (err) {
                ctx.state.error = err.message || ctx.L("messages.editFailed");
                ctx.render();
              });
            },
          })
        );
        actions.appendChild(
          ctx.iconBtn("🗑", ctx.L("ui.actions.delete"), {
            testId: "message-delete-button",
            onClick: function () {
              ctx.deleteMessageConfirm(m).catch(function (err) {
                ctx.state.error = err.message || ctx.L("messages.deleteFailed");
                ctx.render();
              });
            },
          })
        );
      }
      ctx.quickReactions.forEach(function (em) {
        var br = ctx.el("button", "btn btn-ghost btn-sm msg-react-btn", em);
        br.type = "button";
        br.title = ctx.L("ui.message.reactionTitle", { emoji: em });
        br.onclick = function () {
          ctx.toggleReaction(m.id, em).catch(function (err) {
            ctx.state.error = err.message || ctx.L("messages.reactionAddFailed");
            ctx.render();
          });
        };
        actions.appendChild(br);
      });
      art.appendChild(actions);
    }
    return art;
  }

  global.KorusUiMessageArticle = {
    buildMessageArticle: buildMessageArticle,
  };
})(typeof window !== "undefined" ? window : globalThis);
