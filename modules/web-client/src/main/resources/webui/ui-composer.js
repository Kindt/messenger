/**
 * Message composer form (reply bar, format, attach, voice, TTL, draft).
 */
(function (global) {
  "use strict";

  var VOICE_MAX_MS = 120000;
  var VIDEO_NOTE_MAX_MS = 60000;

  function wrapComposerSelection(before, after) {
    var ta = document.getElementById("msgdraft");
    if (!ta) return;
    var s = ta.selectionStart;
    var e = ta.selectionEnd;
    var val = ta.value;
    var sel = val.slice(s, e);
    ta.value = val.slice(0, s) + before + sel + after + val.slice(e);
    ta.focus();
    ta.selectionStart = s + before.length;
    ta.selectionEnd = s + before.length + sel.length;
  }

  function bindComposerDrop(comp, onFileDrop) {
    comp.addEventListener("dragover", function (e) {
      e.preventDefault();
      comp.classList.add("composer-dragover");
    });
    comp.addEventListener("dragleave", function (e) {
      if (!comp.contains(e.relatedTarget)) {
        comp.classList.remove("composer-dragover");
      }
    });
    comp.addEventListener("drop", function (e) {
      e.preventDefault();
      comp.classList.remove("composer-dragover");
      var f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
      if (f && onFileDrop) {
        onFileDrop(f);
      }
    });
  }

  function mountComposer(ctx) {
    var comp = ctx.el("form", "composer");
    comp.onsubmit = function (e) {
      e.preventDefault();
      ctx.sendMessage();
    };
    if (ctx.replyTo) {
      var rbar = ctx.el("div", "composer-reply-bar");
      rbar.setAttribute("data-testid", "composer-reply-bar");
      var rInner = ctx.el("div", "composer-reply-inner");
      if (ctx.replyTo.senderLabel) {
        rInner.appendChild(
          ctx.el("span", "composer-reply-sender", ctx.replyTo.senderLabel + ":")
        );
      }
      rInner.appendChild(
        ctx.el(
          "span",
          "composer-reply-text",
          ctx.replyTo.snippet || ctx.L("ui.message.default")
        )
      );
      rbar.appendChild(rInner);
      var rCancel = ctx.el("button", "btn btn-ghost btn-sm", "✕");
      rCancel.type = "button";
      rCancel.title = ctx.L("ui.thread.cancelReply");
      rCancel.onclick = function () {
        ctx.clearReplyTo();
        ctx.render();
      };
      rbar.appendChild(rCancel);
      comp.appendChild(rbar);
    }
    var fmt = ctx.el("div", "composer-format");
    var bBold = ctx.el("button", "btn btn-ghost btn-icon", "B");
    bBold.type = "button";
    bBold.title = ctx.L("ui.thread.bold");
    bBold.onclick = function () {
      wrapComposerSelection("**", "**");
    };
    var bIt = ctx.el("button", "btn btn-ghost btn-icon", "I");
    bIt.type = "button";
    bIt.title = ctx.L("ui.thread.italic");
    bIt.onclick = function () {
      wrapComposerSelection("*", "*");
    };
    var bCode = ctx.el("button", "btn btn-ghost btn-icon", "</>");
    bCode.type = "button";
    bCode.title = ctx.L("ui.thread.code");
    bCode.onclick = function () {
      wrapComposerSelection("`", "`");
    };
    fmt.appendChild(bBold);
    fmt.appendChild(bIt);
    fmt.appendChild(bCode);
    var filePick = document.createElement("input");
    filePick.type = "file";
    filePick.id = "msgFilePick";
    filePick.setAttribute("data-testid", "file-attach-input");
    filePick.style.display = "none";
    filePick.accept = "image/*,video/*,*/*";
    var maxMb =
      ctx.state.mediaCaps && ctx.state.mediaCaps.max_upload_bytes
        ? Math.round(ctx.state.mediaCaps.max_upload_bytes / (1024 * 1024))
        : 0;
    var bFile = ctx.iconBtn(
      "📎",
      maxMb
        ? ctx.L("ui.thread.attachFileMax", { mb: maxMb })
        : ctx.L("ui.thread.attachFile"),
      {
        testId: "file-attach",
        disabled: ctx.state.busy,
        onClick: function () {
          filePick.click();
        },
      }
    );
    filePick.onchange = function () {
      if (filePick.files && filePick.files[0]) {
        ctx.sendFileMessage(filePick.files[0]);
      }
      filePick.value = "";
    };
    fmt.appendChild(bFile);
    var voiceState = { recorder: null, chunks: [], startedAt: 0 };
    var bVoice = ctx.iconBtn("🎙", ctx.L("ui.thread.voiceRecord"), {
      testId: "voice-record-btn",
      disabled: ctx.state.busy || !window.MediaRecorder,
      onClick: function () {
        if (voiceState.recorder) {
          voiceState.recorder.stop();
          return;
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          ctx.state.error = ctx.L("messages.voiceNotSupported");
          ctx.render();
          return;
        }
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
          voiceState.chunks = [];
          voiceState.startedAt = Date.now();
          var rec = new MediaRecorder(stream);
          voiceState.recorder = rec;
          bVoice.classList.add("active");
          rec.ondataavailable = function (ev) {
            if (ev.data && ev.data.size) voiceState.chunks.push(ev.data);
          };
          rec.onstop = function () {
            stream.getTracks().forEach(function (t) {
              t.stop();
            });
            voiceState.recorder = null;
            bVoice.classList.remove("active");
            var durationMs = Date.now() - voiceState.startedAt;
            if (durationMs > VOICE_MAX_MS) durationMs = VOICE_MAX_MS;
            var blob = new Blob(voiceState.chunks, { type: "audio/webm" });
            if (blob.size > 0) {
              ctx.sendVoiceMessage(blob, durationMs).catch(function (err) {
                ctx.state.error = err.message || ctx.L("messages.sendVoiceFailed");
                ctx.render();
              });
            }
          };
          rec.start();
          setTimeout(function () {
            if (voiceState.recorder) voiceState.recorder.stop();
          }, VOICE_MAX_MS);
        }).catch(function () {
          ctx.state.error = ctx.L("messages.voiceMicDenied");
          ctx.render();
        });
      },
    });
    fmt.appendChild(bVoice);
    if (ctx.sendVideoNoteMessage && navigator.mediaDevices && window.MediaRecorder) {
      var videoState = { recorder: null, chunks: [], startedAt: 0, stream: null };
      var bVideoNote = ctx.iconBtn("🎬", ctx.L("ui.phase5.videoNote"), {
        testId: "video-note-btn",
        disabled: ctx.state.busy,
        onClick: function () {
          if (videoState.recorder) {
            videoState.recorder.stop();
            return;
          }
          navigator.mediaDevices
            .getUserMedia({ video: true, audio: true })
            .then(function (stream) {
              videoState.chunks = [];
              videoState.startedAt = Date.now();
              videoState.stream = stream;
              var rec = new MediaRecorder(stream);
              videoState.recorder = rec;
              bVideoNote.classList.add("active");
              rec.ondataavailable = function (ev) {
                if (ev.data && ev.data.size) videoState.chunks.push(ev.data);
              };
              rec.onstop = function () {
                stream.getTracks().forEach(function (t) {
                  t.stop();
                });
                videoState.recorder = null;
                videoState.stream = null;
                bVideoNote.classList.remove("active");
                var durationMs = Date.now() - videoState.startedAt;
                if (durationMs > VIDEO_NOTE_MAX_MS) durationMs = VIDEO_NOTE_MAX_MS;
                var blob = new Blob(videoState.chunks, { type: "video/webm" });
                if (blob.size > 0) {
                  ctx.sendVideoNoteMessage(blob, durationMs).catch(function (err) {
                    ctx.state.error = err.message || ctx.L("ui.phase5.videoNoteFailed");
                    ctx.render();
                  });
                }
              };
              rec.start();
              setTimeout(function () {
                if (videoState.recorder) videoState.recorder.stop();
              }, VIDEO_NOTE_MAX_MS);
            })
            .catch(function () {
              ctx.state.error = ctx.L("ui.phase5.videoMicDenied");
              ctx.render();
            });
        },
      });
      fmt.appendChild(bVideoNote);
    }
    if (navigator.geolocation && ctx.sendLocationMessage) {
      fmt.appendChild(
        ctx.iconBtn("📍", ctx.L("ui.thread.sendLocation"), {
          testId: "composer-send-location",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.sendLocationMessage();
          },
        })
      );
    }
    if (ctx.openPollCreate) {
      fmt.appendChild(
        ctx.iconBtn("📊", ctx.L("ui.thread.createPoll"), {
          testId: "composer-create-poll",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.openPollCreate();
          },
        })
      );
    }
    if (ctx.openScheduleSend) {
      fmt.appendChild(
        ctx.iconBtn("🕐", ctx.L("ui.thread.scheduleSend"), {
          testId: "composer-schedule-send",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.openScheduleSend();
          },
        })
      );
    }
    if (ctx.openContactShare) {
      fmt.appendChild(
        ctx.iconBtn("👤", ctx.L("ui.thread.shareContact"), {
          testId: "composer-share-contact",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.openContactShare();
          },
        })
      );
    }
    fmt.appendChild(filePick);
    fmt.appendChild(ctx.el("span", "composer-md-hint", ctx.L("ui.thread.markdownHint")));
    comp.appendChild(fmt);
    var ttlRow = ctx.el("div", "composer-ttl-row");
    ttlRow.appendChild(ctx.el("label", "composer-ttl-label", ctx.L("ui.thread.autoDelete")));
    var ttlSel = document.createElement("select");
    ttlSel.id = "composerTtl";
    ttlSel.className = "composer-ttl-select";
    [
      { v: "", l: ctx.L("ui.thread.ttlNone") },
      { v: "60", l: ctx.L("ui.thread.ttl1min") },
      { v: "3600", l: ctx.L("ui.thread.ttl1hour") },
      { v: "86400", l: ctx.L("ui.thread.ttl24h") },
    ].forEach(function (opt) {
      var o = document.createElement("option");
      o.value = opt.v;
      o.textContent = opt.l;
      if (ctx.state.composerTtl === opt.v) o.selected = true;
      ttlSel.appendChild(o);
    });
    ttlSel.onchange = function () {
      ctx.state.composerTtl = ttlSel.value;
    };
    ttlRow.appendChild(ttlSel);
    comp.appendChild(ttlRow);
    var ta = ctx.el("textarea");
    ta.id = "msgdraft";
    ta.setAttribute("data-testid", "message-composer");
    ta.rows = 3;
    ta.placeholder = ctx.L("ui.thread.composerPlaceholder");
    ta.value = ctx.loadComposerDraftForChat(ctx.state.selectedId);
    ta.oninput = function () {
      ctx.scheduleSaveComposerDraft();
      ctx.scheduleTypingNotify();
    };
    ta.onkeydown = function (e) {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        ctx.sendMessage();
      }
    };
    comp.appendChild(ta);
    var sb = ctx.iconBtn("➤", ctx.L("ui.thread.send"), {
      primary: true,
      cls: "composer-send-btn",
      submit: true,
      disabled: ctx.state.busy,
    });
    comp.appendChild(sb);
    bindComposerDrop(comp, function (file) {
      if (ctx.state.selectedId && !ctx.state.busy) {
        ctx.sendFileMessage(file);
      }
    });
    return comp;
  }

  global.KorusUiComposer = {
    mountComposer: mountComposer,
    wrapComposerSelection: wrapComposerSelection,
    bindComposerDrop: bindComposerDrop,
    VOICE_MAX_MS: VOICE_MAX_MS,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
