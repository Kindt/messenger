/**
 * Authenticated file download / image/audio attach for chat messages.
 */
(function (global) {
  "use strict";

  async function fetchFileMetadata(fileId, ctx) {
    try {
      return await ctx.apiJson("/files/" + fileId, { method: "GET" });
    } catch (e) {
      return null;
    }
  }

  function whenVisible(el, run) {
    if (typeof IntersectionObserver === "undefined") {
      run();
      return;
    }
    var observer = new IntersectionObserver(
      function (entries) {
        for (var i = 0; i < entries.length; i++) {
          if (entries[i].isIntersecting) {
            observer.disconnect();
            run();
            return;
          }
        }
      },
      { rootMargin: "240px" }
    );
    observer.observe(el);
  }

  async function loadAuthenticatedImageBlob(fileId, imgEl, ctx) {
    try {
      var meta = await fetchFileMetadata(fileId, ctx);
      if (meta && meta.filename) {
        imgEl.alt = meta.filename;
        imgEl.title = meta.filename;
      }
      var res = await ctx.apiFetch("/files/" + fileId + "/download", {
        method: "GET",
        headers: { Accept: "*/*" },
      });
      if (!res.ok) return;
      var blob = await res.blob();
      var u = URL.createObjectURL(blob);
      ctx.state.blobUrls.push(u);
      imgEl.src = u;
      imgEl.style.cursor = "pointer";
      imgEl.title = ctx.L("files.openInNewTab");
      imgEl.onclick = function () {
        if (imgEl.src) window.open(imgEl.src, "_blank", "noopener,noreferrer");
      };
      imgEl.onerror = function () {
        imgEl.alt = ctx.L("files.imageLoadFailed");
        imgEl.classList.add("msg-attachment-image-error");
      };
    } catch (e) {
      /* attachment preview optional */
    }
  }

  function attachAuthenticatedImage(fileId, imgEl, ctx) {
    whenVisible(imgEl, function () {
      loadAuthenticatedImageBlob(fileId, imgEl, ctx);
    });
  }

  async function attachAuthenticatedAudio(fileId, audioEl, ctx) {
    try {
      var res = await ctx.apiFetch("/files/" + fileId + "/download", {
        method: "GET",
        headers: { Accept: "*/*" },
      });
      if (!res.ok) return;
      var blob = await res.blob();
      var u = URL.createObjectURL(blob);
      ctx.state.blobUrls.push(u);
      audioEl.src = u;
    } catch (e) {
      /* voice preview optional */
    }
  }

  async function openChatMessageForFile(fileId, ctx) {
    if (!fileId || !ctx.state.tokens) return;
    ctx.state.busy = true;
    ctx.state.error = null;
    ctx.state.settingsOpen = false;
    ctx.render();
    try {
      var ref = await ctx.apiJson("/files/" + fileId + "/message-ref", { method: "GET" });
      if (!ref || !ref.chat_id || !ref.message_id) {
        throw new Error(ctx.L("files.messageForFileNotFound"));
      }
      await ctx.openChatById(ref.chat_id);
      await ctx.scrollToMessageId(ref.message_id);
    } catch (e) {
      ctx.state.error = e.message || ctx.L("messages.jumpFailed");
      ctx.state.settingsOpen = true;
    } finally {
      ctx.state.busy = false;
      ctx.render();
    }
  }

  async function downloadChatFile(fileId, ctx) {
    var res = await ctx.apiFetch("/files/" + fileId + "/download", {
      method: "GET",
      headers: { Accept: "*/*" },
    });
    if (!res.ok) {
      throw new Error(ctx.L("files.downloadFailed"));
    }
    var cd = res.headers.get("Content-Disposition") || "";
    var filename = "file";
    var m = /filename="([^"]+)"/i.exec(cd);
    if (m) filename = m[1];
    var blob = await res.blob();
    var u = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = u;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(u);
  }

  global.KorusUiFileAttach = {
    attachAuthenticatedImage: attachAuthenticatedImage,
    attachAuthenticatedAudio: attachAuthenticatedAudio,
    openChatMessageForFile: openChatMessageForFile,
    downloadChatFile: downloadChatFile,
    fetchFileMetadata: fetchFileMetadata,
  };
})(typeof globalThis !== "undefined" ? globalThis : globalThis);
