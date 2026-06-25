/**
 * UX perception helpers (FR-165–168): skeleton, optimistic send, upload progress, layout stability.
 */
(function (global) {
  "use strict";

  var SKELETON_STYLE =
    "background:linear-gradient(90deg,var(--panel2) 25%,var(--panel) 50%,var(--panel2) 75%);" +
    "background-size:200% 100%;animation:ux-skeleton-shimmer 1.2s ease-in-out infinite;";

  function injectSkeletonKeyframes(doc) {
    doc = doc || (typeof document !== "undefined" ? document : null);
    if (!doc || doc.getElementById("ux-skeleton-keyframes")) return;
    var style = doc.createElement("style");
    style.id = "ux-skeleton-keyframes";
    style.textContent =
      "@keyframes ux-skeleton-shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}";
    doc.head.appendChild(style);
  }

  function newClientMsgId() {
    return (
      "c_" +
      Date.now().toString(36) +
      "_" +
      Math.random().toString(36).slice(2, 10)
    );
  }

  function buildOptimisticMessage(opts) {
    opts = opts || {};
    var clientMsgId = opts.clientMsgId || newClientMsgId();
    return {
      id: "pending-" + clientMsgId,
      client_msg_id: clientMsgId,
      chat_id: opts.chatId,
      sender_id: opts.senderId,
      type: opts.type || "text",
      content: opts.content || "",
      reply_to_msg_id: opts.replyToMsgId || null,
      attachment_file_id: opts.attachmentFileId || null,
      duration_ms: opts.durationMs != null ? opts.durationMs : null,
      deleted: false,
      created_at: new Date().toISOString(),
      edited_at: null,
      _pending: true,
      _failed: false,
    };
  }

  function isPendingMessage(m) {
    return !!(m && (m._pending || (m.id && String(m.id).indexOf("pending-") === 0)));
  }

  function findOptimisticIndex(messages, clientMsgId) {
    if (!clientMsgId || !messages || !messages.length) return -1;
    for (var i = 0; i < messages.length; i++) {
      var m = messages[i];
      if (m.client_msg_id === clientMsgId || m.id === "pending-" + clientMsgId) {
        return i;
      }
    }
    return -1;
  }

  function reconcileOptimisticSend(messages, clientMsgId, sent, sortMessagesAsc) {
    var rows = (messages || []).slice();
    var idx = findOptimisticIndex(rows, clientMsgId);
    if (idx >= 0 && sent && sent.id) {
      rows[idx] = sent;
      return sortMessagesAsc ? sortMessagesAsc(rows) : rows;
    }
    if (idx >= 0) {
      rows[idx] = Object.assign({}, rows[idx], {
        _pending: false,
        _failed: !sent,
      });
      return rows;
    }
    if (sent && sent.id) {
      if (sortMessagesAsc) {
        return sortMessagesAsc(rows.concat([sent]));
      }
      return rows.concat([sent]);
    }
    return rows;
  }

  function mountSkeletonBlock(elFn, cls, styleExtra) {
    var block = elFn("div", cls || "ux-skeleton-block");
    block.style.cssText = SKELETON_STYLE + (styleExtra || "");
    block.setAttribute("aria-hidden", "true");
    return block;
  }

  function mountChatListSkeleton(listEl, elFn, count) {
    injectSkeletonKeyframes();
    count = count > 0 ? count : 5;
    listEl.innerHTML = "";
    listEl.setAttribute("data-testid", "chat-list-skeleton");
    listEl.setAttribute("aria-busy", "true");
    for (var i = 0; i < count; i++) {
      var row = elFn("div", "ux-skeleton-chat-row");
      row.style.cssText = "display:flex;gap:10px;padding:10px 12px;align-items:center;";
      var av = mountSkeletonBlock(elFn, "ux-skeleton-avatar", "width:40px;height:40px;border-radius:50%;flex-shrink:0;");
      row.appendChild(av);
      var txt = elFn("div", "ux-skeleton-chat-text");
      txt.style.cssText = "flex:1;display:flex;flex-direction:column;gap:6px;";
      txt.appendChild(
        mountSkeletonBlock(elFn, "ux-skeleton-line", "height:12px;width:55%;border-radius:4px;")
      );
      txt.appendChild(
        mountSkeletonBlock(elFn, "ux-skeleton-line", "height:10px;width:80%;border-radius:4px;opacity:0.7;")
      );
      row.appendChild(txt);
      listEl.appendChild(row);
    }
    return listEl;
  }

  function mountThreadSkeleton(elFn) {
    injectSkeletonKeyframes();
    var wrap = elFn("div", "ux-skeleton-thread");
    wrap.setAttribute("data-testid", "thread-skeleton");
    wrap.setAttribute("aria-busy", "true");
    wrap.style.cssText = "padding:16px;display:flex;flex-direction:column;gap:14px;";
    for (var i = 0; i < 4; i++) {
      var row = elFn("div", "ux-skeleton-msg-row");
      var own = i % 2 === 1;
      row.style.cssText =
        "display:flex;" + (own ? "justify-content:flex-end;" : "justify-content:flex-start;");
      var bubble = mountSkeletonBlock(
        elFn,
        "ux-skeleton-msg-bubble",
        "height:" + (48 + (i % 3) * 12) + "px;width:" + (own ? "62%" : "70%") + ";border-radius:12px;"
      );
      row.appendChild(bubble);
      wrap.appendChild(row);
    }
    return wrap;
  }

  function mountUploadProgressBar(elFn, pct, L) {
    var pctNum = Math.max(0, Math.min(100, Math.round(Number(pct) || 0)));
    var row = elFn("div", "composer-upload-progress");
    row.setAttribute("data-testid", "composer-upload-progress");
    row.setAttribute("role", "status");
    var label = elFn("span", "composer-upload-progress-label", pctNum + "%");
    label.setAttribute("data-testid", "composer-upload-progress-label");
    row.appendChild(label);
    var track = elFn("div", "composer-upload-progress-track");
    track.style.cssText =
      "flex:1;height:6px;border-radius:3px;background:var(--panel2);overflow:hidden;margin-left:8px;";
    var fill = elFn("div", "composer-upload-progress-fill");
    fill.style.cssText =
      "height:100%;width:" +
      pctNum +
      "%;background:var(--accent, #3b82f6);transition:width 0.15s ease;";
    fill.setAttribute("data-testid", "composer-upload-progress-fill");
    track.appendChild(fill);
    row.appendChild(track);
    row.style.cssText = "display:flex;align-items:center;padding:4px 0 8px;font-size:0.85rem;";
    var hintKey = "ui.ux.uploading";
    var hint = L ? L(hintKey) : hintKey;
    if (hint === hintKey) hint = "Uploading…";
    row.title = hint + " " + pctNum + "%";
    return row;
  }

  function prepareImageAttachmentWrap(wrapEl, imgEl) {
    if (!wrapEl || !imgEl) return;
    wrapEl.className = "msg-attachment-image-wrap";
    wrapEl.style.minHeight = "120px";
    wrapEl.style.maxWidth = "min(100%, 320px)";
    wrapEl.style.aspectRatio = "4 / 3";
    wrapEl.style.background = "var(--panel2)";
    wrapEl.style.borderRadius = "8px";
    wrapEl.style.overflow = "hidden";
    wrapEl.style.display = "flex";
    wrapEl.style.alignItems = "center";
    wrapEl.style.justifyContent = "center";
    wrapEl.setAttribute("data-testid", "message-image-wrap");
    imgEl.className = "msg-attachment-image";
    imgEl.style.width = "100%";
    imgEl.style.height = "100%";
    imgEl.style.objectFit = "contain";
    imgEl.style.opacity = "0";
    imgEl.style.transition = "opacity 0.2s ease";
    bindImageLayoutStable(imgEl, wrapEl);
  }

  function bindImageLayoutStable(imgEl, wrapEl) {
    if (!imgEl) return;
    imgEl.addEventListener("load", function () {
      imgEl.style.opacity = "1";
      imgEl.classList.add("msg-attachment-image-loaded");
      if (wrapEl && imgEl.naturalWidth > 0 && imgEl.naturalHeight > 0) {
        wrapEl.style.aspectRatio = imgEl.naturalWidth + " / " + imgEl.naturalHeight;
        wrapEl.style.minHeight = "0";
      }
    });
    imgEl.addEventListener("error", function () {
      if (wrapEl) wrapEl.style.minHeight = "48px";
    });
  }

  function uploadFileWithProgress(opts) {
    opts = opts || {};
    var file = opts.file;
    var url = opts.url;
    var getAccessToken = opts.getAccessToken || function () {
      return null;
    };
    var onProgress = opts.onProgress;
    if (!file || !url) {
      return Promise.reject(new Error("uploadFileWithProgress: missing file or url"));
    }
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open("POST", url, true);
      var token = getAccessToken();
      if (token) {
        xhr.setRequestHeader("Authorization", "Bearer " + token);
      }
      xhr.upload.onprogress = function (ev) {
        if (!ev.lengthComputable || typeof onProgress !== "function") return;
        onProgress(Math.round((ev.loaded / ev.total) * 100));
      };
      xhr.onload = function () {
        var text = xhr.responseText || "";
        var parsed = null;
        if (text) {
          try {
            parsed = JSON.parse(text);
          } catch (e) {
            parsed = null;
          }
        }
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(parsed);
          return;
        }
        var msg =
          parsed && typeof parsed === "object" && parsed.message
            ? String(parsed.message)
            : xhr.statusText || "Upload failed";
        reject(new Error(msg));
      };
      xhr.onerror = function () {
        reject(new Error("Upload failed"));
      };
      var fd = new FormData();
      fd.append("file", file, file.name || "file");
      xhr.send(fd);
    });
  }

  global.KorusUiUxPerception = {
    newClientMsgId: newClientMsgId,
    buildOptimisticMessage: buildOptimisticMessage,
    isPendingMessage: isPendingMessage,
    reconcileOptimisticSend: reconcileOptimisticSend,
    mountChatListSkeleton: mountChatListSkeleton,
    mountThreadSkeleton: mountThreadSkeleton,
    mountUploadProgressBar: mountUploadProgressBar,
    prepareImageAttachmentWrap: prepareImageAttachmentWrap,
    bindImageLayoutStable: bindImageLayoutStable,
    uploadFileWithProgress: uploadFileWithProgress,
    injectSkeletonKeyframes: injectSkeletonKeyframes,
  };
})(typeof globalThis !== "undefined" ? globalThis : globalThis);
