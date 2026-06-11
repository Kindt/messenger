(function (global) {
  "use strict";

  function createExportUtils(deps) {
    var getState = deps.getState;
    var setState = deps.setState;
    var apiJson = deps.apiJson;
    var apiFetch = deps.apiFetch;
    var scheduleRender = deps.scheduleRender;
    var exportPollGenerationRef = deps.exportPollGenerationRef;

    function pollExportUntilReady(jobId, chatId) {
      var state = getState();
      if (!chatId) chatId = state.selectedId;
      var attempts = 0;
      var gen = ++exportPollGenerationRef.value;
      return new Promise(function (resolve, reject) {
        function tick() {
          if (gen !== exportPollGenerationRef.value) {
            reject(new Error("Экспорт отменён"));
            return;
          }
          attempts++;
          apiJson("/chats/" + chatId + "/export/" + jobId, { method: "GET" })
            .then(function (st) {
              if (gen !== exportPollGenerationRef.value) {
                reject(new Error("Экспорт отменён"));
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
                reject(new Error("Экспорт завершился с ошибкой"));
                return;
              }
              if (status === "export_cancelled") {
                reject(new Error("Экспорт отменён"));
                return;
              }
              if (attempts >= 60) {
                reject(new Error("Таймаут ожидания экспорта"));
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
                    ? "в очереди"
                    : status === "processing"
                      ? "обработка"
                      : status || "ожидание";
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
      a.remove();
      URL.revokeObjectURL(url);
    }

    async function previewExportAttachments(chatId, jobId) {
      try {
        var manifest = await apiJson("/chats/" + chatId + "/export/" + jobId + "/attachments", {
          method: "GET",
        });
        if (manifest && manifest.files && manifest.files.length) {
          setState({
            statusMessage: "Export attachments: " + manifest.files.length + " file(s)",
          });
        }
      } catch (e) {}
    }

    async function startChatExport() {
      var state = getState();
      if (!state.selectedId || state.exportBusy) {
        if (state.exportBusy) {
          setState({ statusMessage: "Уже выполняется другой экспорт" });
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
        if (!jobId) throw new Error("Сервер не вернул job_id");
        setState({
          exportJobId: jobId,
          exportJobChatId: chatId,
          statusMessage:
            "Экспорт запущен — можно перейти в другой чат; скачивание начнётся автоматически",
        });
        scheduleRender();
        pollExportUntilReady(jobId, chatId)
          .then(function () {
            setState({ statusMessage: "Экспорт готов, архив скачан" });
          })
          .catch(function (e) {
            var msg = (e && e.message) || "Экспорт не удался";
            if (msg.indexOf("отмен") !== -1) {
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
          error: e.message || "Экспорт не удался",
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
        setState({ statusMessage: "Экспорт отменён" });
      } catch (e) {
        setState({ error: e.message || "Не удалось отменить экспорт" });
      } finally {
        setState({
          busy: false,
          exportJobId: null,
          exportJobChatId: null,
          exportProgressLabel: null,
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
