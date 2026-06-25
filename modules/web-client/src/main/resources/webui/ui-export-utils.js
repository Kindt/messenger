(function (global) {
  "use strict";

  function createExportUtils(deps) {
    var getState = deps.getState;
    var setState = deps.setState;
    var apiJson = deps.apiJson;
    var apiFetch = deps.apiFetch;
    var scheduleRender = deps.scheduleRender;
    var exportPollGenerationRef = deps.exportPollGenerationRef;
    var L =
      deps.L ||
      function (key) {
        return global.KorusI18n ? global.KorusI18n.t(key) : key;
      };

    function pollExportUntilReady(jobId, chatId) {
      var state = getState();
      if (!chatId) chatId = state.selectedId;
      var attempts = 0;
      var gen = ++exportPollGenerationRef.value;
      return new Promise(function (resolve, reject) {
        function tick() {
          if (gen !== exportPollGenerationRef.value) {
            reject(new Error(L("ui.export.cancelled")));
            return;
          }
          attempts++;
          apiJson("/chats/" + chatId + "/export/" + jobId, { method: "GET" })
            .then(function (st) {
              if (gen !== exportPollGenerationRef.value) {
                reject(new Error(L("ui.export.cancelled")));
                return;
              }
              var status = st && st.status;
              if (status === "export_v1" || status === "stub_written") {
                return previewExportAttachments(chatId, jobId)
                  .then(function () {
                    return downloadExportArtifact(chatId, jobId);
                  })
                  .then(resolve)
                  .catch(reject);
              }
              if (status === "export_failed") {
                reject(new Error(L("ui.export.failedJob")));
                return;
              }
              if (status === "export_cancelled") {
                reject(new Error(L("ui.export.cancelled")));
                return;
              }
              if (attempts >= 60) {
                reject(new Error(L("ui.export.timeout")));
                return;
              }
              var s = getState();
              if (
                s.exportJobId === jobId &&
                s.exportJobChatId === chatId &&
                gen === exportPollGenerationRef.value
              ) {
                var statusLabel =
                  status === "queued"
                    ? L("ui.export.statusQueued")
                    : status === "processing"
                      ? L("ui.export.statusProcessing")
                      : L("ui.export.statusWaiting");
                setState({ exportProgressLabel: statusLabel + " (" + attempts + "/60)" });
                scheduleRender();
              }
              setTimeout(tick, 2000);
            })
            .catch(reject);
        }
        tick();
      });
    }

    async function downloadExportArtifact(chatId, jobId, part) {
      var suffix = part ? "?part=" + encodeURIComponent(part) : "";
      var res = await apiFetch("/chats/" + chatId + "/export/" + jobId + "/download" + suffix);
      if (!res.ok) throw new Error("Export download failed");
      var blob = await res.blob();
      var url = URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      var ext = part === "json" ? ".json" : part === "manifest" ? "-manifest.json" : ".zip";
      a.download = "korus-export-" + chatId.slice(0, 8) + ext;
      a.setAttribute("data-testid", "export-download-link");
      document.body.appendChild(a);
      a.click();
      setTimeout(function () {
        a.remove();
        URL.revokeObjectURL(url);
      }, 1000);
    }

    async function previewExportAttachments(chatId, jobId) {
      try {
        var manifest = await apiJson("/chats/" + chatId + "/export/" + jobId + "/attachments", {
          method: "GET",
        });
        if (manifest && manifest.files && manifest.files.length) {
          setState({
            statusMessage: L("ui.export.attachmentsPreview").replace(
              "{count}",
              String(manifest.files.length)
            ),
          });
        }
      } catch (e) {}
    }

    async function startChatExport() {
      var state = getState();
      if (!state.selectedId || state.exportBusy) {
        if (state.exportBusy) {
          setState({ statusMessage: L("ui.export.alreadyRunning") });
          scheduleRender();
        }
        return;
      }
      var chatId = state.selectedId;
      setState({
        exportBusy: true,
        exportProgressLabel: null,
        error: null,
        statusMessage: null,
      });
      scheduleRender();
      try {
        var accepted = await apiJson("/chats/" + chatId + "/export", { method: "POST" });
        var jobId = accepted && accepted.job_id;
        if (!jobId) throw new Error(L("ui.export.noJobId"));
        setState({
          exportJobId: jobId,
          exportJobChatId: chatId,
          statusMessage: L("ui.export.started"),
        });
        scheduleRender();
        pollExportUntilReady(jobId, chatId)
          .then(function () {
            setState({ statusMessage: L("ui.export.ready") });
          })
          .catch(function (e) {
            var msg = (e && e.message) || L("ui.export.failed");
            if (msg === L("ui.export.cancelled")) {
              setState({ statusMessage: msg });
            } else {
              setState({ error: msg });
            }
          })
          .finally(function () {
            setState({
              exportBusy: false,
              exportJobId: null,
              exportJobChatId: null,
              exportProgressLabel: null,
            });
            scheduleRender();
          });
      } catch (e) {
        setState({
          error: e.message || L("ui.export.failed"),
          exportBusy: false,
          exportProgressLabel: null,
        });
        scheduleRender();
      }
    }

    async function cancelChatExport() {
      var state = getState();
      if (!state.exportBusy || !state.exportJobId || !state.exportJobChatId) return;
      var jobId = state.exportJobId;
      var chatId = state.exportJobChatId;
      setState({ busy: true, error: null });
      scheduleRender();
      try {
        await apiFetch("/chats/" + chatId + "/export/" + jobId, { method: "DELETE" });
        exportPollGenerationRef.value++;
        setState({ statusMessage: L("ui.export.cancelled") });
      } catch (e) {
        setState({ error: e.message || L("ui.export.cancelFailed") });
      } finally {
        setState({
          busy: false,
          exportJobId: null,
          exportJobChatId: null,
          exportBusy: false,
        });
        scheduleRender();
      }
    }

    return {
      startChatExport: startChatExport,
      cancelChatExport: cancelChatExport,
      pollExportUntilReady: pollExportUntilReady,
      downloadExportArtifact: downloadExportArtifact,
      previewExportAttachments: previewExportAttachments,
    };
  }

  global.KorusUiExportUtils = createExportUtils;
})(typeof window !== "undefined" ? window : globalThis);
