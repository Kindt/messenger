(function () {
  const API = "/api/v1";
  const LS_KEY = "admin_console_token";
  const LS_REFRESH = "admin_console_refresh_token";

  /** Последняя сущность для PATCH ретенции: org или chat + UUID. */
  let retentionPatchTarget = null;
  /** UUID org для PATCH auth-policy. */
  let authPolicyTargetOrgId = null;
  /** Последний manifest для перерисовки nav при смене locale. */
  let cachedManifestSections = null;

  const el = (id) => document.getElementById(id);
  const LT = (text) => (window.AdminI18n ? AdminI18n.text(text) : text);
  const L = (key, params) => (window.AdminI18n ? AdminI18n.t(key, params) : key);
  function adminFmt(template, params) {
    var s = LT(template);
    if (!params) {
      return s;
    }
    Object.keys(params).forEach(function (k) {
      s = s.split("{" + k + "}").join(String(params[k]));
    });
    return s;
  }

  function clearAuthStorage() {
    sessionStorage.removeItem(LS_KEY);
    sessionStorage.removeItem(LS_REFRESH);
  }

  function updateLogoutButton() {
    const btn = el("btnLogout");
    const hasToken = !!token();
    if (btn) {
      btn.hidden = !hasToken;
    }
    if (window.AdminUi) {
      AdminUi.setAuthenticated(hasToken);
    }
  }

  function resetPanelAfterLogout() {
    const nav = el("sectionList");
    if (nav) {
      nav.innerHTML = "";
    }
    const hint = el("panelHint");
    if (hint) {
      hint.hidden = false;
    }
    const header = el("panelHeader");
    if (header) {
      header.hidden = true;
    }
    const orgBar = el("orgContextBar");
    if (orgBar) {
      orgBar.hidden = true;
    }
    const summary = el("panelSummary");
    if (summary) {
      summary.hidden = true;
      summary.innerHTML = "";
    }
    const pre = el("panelContent");
    if (pre) {
      pre.hidden = true;
      pre.textContent = "";
      pre.classList.remove("is-collapsed");
    }
    if (window.AdminUi) {
      AdminUi.showJsonBlock(false);
      AdminUi.setPanelHeader(null);
      AdminUi.setActiveSectionId(null);
    }
    const ver = el("apiVersionLabel");
    if (ver) {
      ver.hidden = true;
      ver.textContent = "";
    }
  }

  function formatBytes(n) {
    if (n == null || isNaN(n)) {
      return "—";
    }
    const u = ["B", "KiB", "MiB", "GiB"];
    let v = Number(n);
    let i = 0;
    while (v >= 1024 && i < u.length - 1) {
      v /= 1024;
      i++;
    }
    return (i === 0 ? v : v.toFixed(1)) + " " + u[i];
  }

  function formatDuration(ms) {
    if (ms == null || isNaN(ms)) {
      return "—";
    }
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    const d = Math.floor(h / 24);
    if (d > 0) {
      return d + " d " + (h % 24) + " h";
    }
    if (h > 0) {
      return h + " h " + (m % 60) + " min";
    }
    if (m > 0) {
      return m + " min " + (s % 60) + " s";
    }
    return s + " s";
  }

  function renderAdminKpiCards(container, cards) {
    container.querySelector(".stats-grid")?.remove();
    const grid = document.createElement("div");
    grid.className = "stats-grid";
    cards.forEach(function (item) {
      const card = document.createElement("div");
      card.className = "stat-card";
      const lbl = document.createElement("div");
      lbl.className = "stat-card-label";
      lbl.textContent = item.label;
      const val = document.createElement("div");
      val.className = "stat-card-value";
      val.textContent = item.value;
      card.appendChild(lbl);
      card.appendChild(val);
      grid.appendChild(card);
    });
    const cap = container.querySelector(".muted.small");
    if (cap && cap.nextSibling) {
      container.insertBefore(grid, cap.nextSibling);
    } else {
      container.appendChild(grid);
    }
  }

  function renderCoreStatsSummary(stats, container) {
    container.innerHTML = "";
    container.hidden = false;
    const badge = window.AdminUi && AdminUi.statusBadge;
    const grid = document.createElement("div");
    grid.className = "stats-grid";
    const addCard = (label, valueNode) => {
      const card = document.createElement("div");
      card.className = "stat-card";
      const lbl = document.createElement("div");
      lbl.className = "stat-card-label";
      lbl.textContent = label;
      const val = document.createElement("div");
      val.className = "stat-card-value";
      if (typeof valueNode === "string") {
        val.textContent = valueNode;
      } else {
        val.appendChild(valueNode);
      }
      card.appendChild(lbl);
      card.appendChild(val);
      grid.appendChild(card);
    };
    const table = document.createElement("table");
    const addRow = (label, value) => {
      const tr = document.createElement("tr");
      const th = document.createElement("th");
      th.textContent = label;
      const td = document.createElement("td");
      if (value instanceof Node) {
        td.appendChild(value);
      } else {
        td.textContent = value;
      }
      tr.appendChild(th);
      tr.appendChild(td);
      table.appendChild(tr);
    };
    const j = stats.jvm || {};
    const d = stats.dependencies || {};
    const c = stats.counts || {};
    addCard(LT("Версия API"), String(stats.api_version || "—"));
    addCard(LT("Uptime JVM"), formatDuration(j.uptime_ms));
    addCard(
      LT("PostgreSQL"),
      badge
        ? badge(!!d.database_ok, d.database_ok ? LT("ok") : LT("нет"))
        : d.database_ok
          ? LT("ok")
          : LT("недоступна")
    );
    addCard(
      LT("Redis"),
      badge
        ? badge(!!d.redis_ok, d.redis_ok ? LT("ok") : LT("нет"))
        : d.redis_ok
          ? LT("ok")
          : LT("недоступен")
    );
    addCard(
      LT("NATS"),
      badge
        ? badge(!!d.nats_ok, d.nats_ok ? LT("ok") : LT("нет"))
        : d.nats_ok
          ? LT("ok")
          : LT("нет соединения")
    );
    container.appendChild(grid);
    addRow(
      LT("Heap (used / max)"),
      formatBytes(j.heap_used_bytes) + " / " + formatBytes(j.heap_max_bytes)
    );
    addRow(LT("Процессоры"), String(j.processors != null ? j.processors : "—"));
    if (c.counts_available) {
      addRow(LT("Пользователи"), String(c.users));
      addRow(LT("Чаты"), String(c.chats));
      addRow(LT("Сообщения"), String(c.messages));
    } else {
      addRow(LT("Счётчики БД"), LT("недоступны"));
    }
    const ec = stats.export_compliance || {};
    if (ec.available) {
      addRow(LT("Экспорт: задачи (всего)"), String(ec.jobs_total));
      addRow(
        LT("Экспорт: queued / processing"),
        String(ec.jobs_queued) + " / " + String(ec.jobs_processing)
      );
      if (ec.jobs_processing_stale > 0) {
        addRow(
          LT("Экспорт: processing stale"),
          String(ec.jobs_processing_stale) + " (>" + String(ec.processing_stale_minutes) + " мин)"
        );
      }
      addRow(
        LT("Экспорт: завершено / ошибка"),
        String(ec.jobs_completed) + " / " + String(ec.jobs_failed)
      );
      addRow(LT("Экспорт: отменено"), String(ec.jobs_cancelled));
      addRow(LT("Аудит export.* (7 дн.)"), String(ec.audit_export_events_7d));
      addRow(LT("Аудит export cancel (7 дн.)"), String(ec.audit_export_cancelled_7d));
    } else {
      addRow(LT("Экспорт (compliance)"), LT("недоступен"));
    }
    container.appendChild(table);
    const cap = document.createElement("p");
    cap.className = "muted small";
    cap.textContent = LT("Полный JSON ниже.");
    container.appendChild(cap);
  }

  function renderGdprDisclosuresTable(disclosures, container) {
    if (!Array.isArray(disclosures) || disclosures.length === 0) {
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap";
    const table = document.createElement("table");
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    ["id", "category", "included", "note"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    disclosures.forEach((row) => {
      const tr = document.createElement("tr");
      const inc = row.included === true;
      [
        row.id || "—",
        row.category || "—",
        inc ? "да" : "нет",
        row.note || ""
      ].forEach((val) => {
        const td = document.createElement("td");
        td.textContent = val;
        tr.appendChild(td);
      });
      if (inc) {
        tr.classList.add("gdpr-included");
      }
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    container.appendChild(wrap);
  }

  function renderExportJobsTable(list, container) {
    if (!list || !Array.isArray(list.jobs) || list.jobs.length === 0) {
      return;
    }
    const note = document.createElement("p");
    note.className = "json-panel-note";
    const filterHint = list.status_filter ? " filter=" + list.status_filter : "";
    const chatHint = list.chat_id_filter ? " chat=" + list.chat_id_filter : "";
    note.textContent =
      "Задачи export_jobs (" + String(list.job_count) + ")" + filterHint + chatHint + ":";
    container.appendChild(note);
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap export-jobs-table";
    const table = document.createElement("table");
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    const withChat = list.jobs.length > 0 && list.jobs[0].chat_id != null;
    const cols = withChat
      ? ["job_id", "chat_id", "status", "format", "created_at"]
      : ["job_id", "status", "format", "created_at"];
    cols.forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    list.jobs.forEach((j) => {
      const tr = document.createElement("tr");
      tr.style.cursor = "pointer";
      tr.title = "Подставить chat_id и job_id";
      tr.addEventListener("click", () => {
        const inp = document.getElementById("exportInspectJobId");
        if (inp && j.job_id) {
          inp.value = j.job_id;
        }
        const chatInp = document.getElementById("exportSuggestChatId");
        if (chatInp && j.chat_id) {
          chatInp.value = j.chat_id;
        }
      });
      const rowVals = withChat
        ? [j.job_id, j.chat_id, j.status, j.output_format, j.created_at]
        : [j.job_id, j.status, j.output_format, j.created_at];
      rowVals.forEach((val) => {
        const td = document.createElement("td");
        td.textContent = val != null ? String(val) : "";
        tr.appendChild(td);
      });
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    container.appendChild(wrap);
  }

  function renderExportAttachmentsTable(att, container) {
    if (!att || !att.zip_bundle || !Array.isArray(att.files) || att.files.length === 0) {
      return;
    }
    const note = document.createElement("p");
    note.className = "json-panel-note";
    note.textContent =
      "Вложения export (страница " +
      String(att.file_count) +
      " из " +
      String(att.total_count) +
      ", offset=" +
      String(att.offset) +
      "):";
    container.appendChild(note);
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap";
    const table = document.createElement("table");
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    ["file_id", "filename", "mime_type", "size_bytes", "sha256"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    att.files.forEach((f) => {
      const tr = document.createElement("tr");
      [f.file_id, f.filename, f.mime_type, String(f.size_bytes), f.sha256].forEach((val) => {
        const td = document.createElement("td");
        td.textContent = val != null ? String(val) : "";
        tr.appendChild(td);
      });
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    container.appendChild(wrap);
  }

  function renderExportComplianceGuide(guide, container, pre) {
    container.innerHTML = "";
    const ec = guide.export_compliance || {};
    const table = document.createElement("table");
    const addRow = (label, value) => {
      const tr = document.createElement("tr");
      const th = document.createElement("th");
      th.textContent = label;
      const td = document.createElement("td");
      td.textContent = value;
      tr.appendChild(th);
      tr.appendChild(td);
      table.appendChild(tr);
    };
    if (ec.available) {
      addRow(LT("Задачи export_jobs"), String(ec.jobs_total));
      addRow(LT("queued / processing"), String(ec.jobs_queued) + " / " + String(ec.jobs_processing));
      if (ec.jobs_processing_stale > 0) {
        addRow(
          LT("processing stale"),
          String(ec.jobs_processing_stale) + " (>" + String(ec.processing_stale_minutes) + " мин)"
        );
      }
      addRow(LT("завершено / ошибка"), String(ec.jobs_completed) + " / " + String(ec.jobs_failed));
      addRow(LT("отменено"), String(ec.jobs_cancelled));
      addRow(LT("Аудит export.* (7 дн.)"), String(ec.audit_export_events_7d));
      addRow(LT("аудит cancel (7 дн.)"), String(ec.audit_export_cancelled_7d));
    } else {
      addRow(LT("Счётчики"), LT("недоступны"));
    }
    container.appendChild(table);

    const cp = guide.completeness_policy;
    if (cp && Array.isArray(cp.required_fields)) {
      const hPol = document.createElement("p");
      hPol.className = "json-panel-note";
      hPol.textContent =
        "Политика полноты (EXPORT_REQUIRED_FIELDS" +
        (cp.strict ? ", STRICT" : "") +
        "):";
      container.appendChild(hPol);
      const polWrap = document.createElement("div");
      polWrap.className = "json-table-wrap";
      const polTable = document.createElement("table");
      const pHead = document.createElement("thead");
      const pHr = document.createElement("tr");
      ["field", "required"].forEach((h) => {
        const th = document.createElement("th");
        th.textContent = h;
        pHr.appendChild(th);
      });
      pHead.appendChild(pHr);
      polTable.appendChild(pHead);
      const pBody = document.createElement("tbody");
      cp.required_fields.forEach((field) => {
        const tr = document.createElement("tr");
        const tdField = document.createElement("td");
        tdField.textContent = field;
        const tdReq = document.createElement("td");
        tdReq.textContent = LT("да");
        tr.appendChild(tdField);
        tr.appendChild(tdReq);
        pBody.appendChild(tr);
      });
      polTable.appendChild(pBody);
      polWrap.appendChild(polTable);
      container.appendChild(polWrap);
      if (cp.strict) {
        const strictNote = document.createElement("p");
        strictNote.className = "json-panel-note";
        strictNote.textContent = LT(
          "EXPORT_COMPLETENESS_STRICT=true — неполный пакет → export_failed."
        );
        container.appendChild(strictNote);
      }
    }

    const hGdpr = document.createElement("p");
    hGdpr.className = "json-panel-note";
    hGdpr.textContent = "Справочник gdprDisclosures (шаблон для export.json):";
    container.appendChild(hGdpr);
    renderGdprDisclosuresTable(guide.gdpr_disclosures_reference, container);

    const envList = guide.env_checklist;
    if (Array.isArray(envList) && envList.length > 0) {
      const hEnv = document.createElement("p");
      hEnv.className = "json-panel-note";
      hEnv.textContent = "Переменные окружения (полный compliance-пакет):";
      container.appendChild(hEnv);
      const envWrap = document.createElement("div");
      envWrap.className = "json-table-wrap";
      const envTable = document.createElement("table");
      const eHead = document.createElement("thead");
      const eHr = document.createElement("tr");
      ["env", "purpose", "default"].forEach((h) => {
        const th = document.createElement("th");
        th.textContent = h;
        eHr.appendChild(th);
      });
      eHead.appendChild(eHr);
      envTable.appendChild(eHead);
      const eBody = document.createElement("tbody");
      envList.forEach((row) => {
        const tr = document.createElement("tr");
        [row.env || "", row.purpose || "", row.default_value || ""].forEach((val) => {
          const td = document.createElement("td");
          td.textContent = val;
          tr.appendChild(td);
        });
        eBody.appendChild(tr);
      });
      envTable.appendChild(eBody);
      envWrap.appendChild(envTable);
      container.appendChild(envWrap);
    }

    const smokeList = guide.smoke_commands;
    if (Array.isArray(smokeList) && smokeList.length > 0) {
      const hSmoke = document.createElement("p");
      hSmoke.className = "json-panel-note";
      hSmoke.textContent = LT("Smoke-команды (локальный стенд):");
      container.appendChild(hSmoke);
      const smokeWrap = document.createElement("div");
      smokeWrap.className = "export-smoke-commands";
      smokeList.forEach((row) => {
        const block = document.createElement("div");
        block.className = "export-smoke-cmd-block";
        const title = document.createElement("p");
        title.className = "small";
        title.textContent = row.title || "smoke";
        block.appendChild(title);
        const cmd = row.command_ps || row.command_sh || "";
        const preCmd = document.createElement("pre");
        preCmd.className = "export-smoke-cmd-pre";
        preCmd.textContent = cmd;
        block.appendChild(preCmd);
        const btn = document.createElement("button");
        btn.type = "button";
        btn.textContent = "копировать";
        btn.addEventListener("click", () => {
          const text = cmd;
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(
              () => {
                btn.textContent = "скопировано";
                setTimeout(() => {
                  btn.textContent = "копировать";
                }, 1500);
              },
              () => {
                btn.textContent = "ошибка";
              }
            );
          } else {
            btn.textContent = LT("Ctrl+C из pre");
          }
        });
        block.appendChild(btn);
        smokeWrap.appendChild(block);
      });
      container.appendChild(smokeWrap);
    }

    const cap = document.createElement("p");
    cap.className = "muted small";
    cap.textContent = "Полный JSON ниже. Аудит: пресеты в разделе «Аудит».";
    container.appendChild(cap);
    pre.textContent = JSON.stringify(guide, null, 2);
  }

  function normalizePanelPath(p) {
    return p.startsWith("/") ? p : "/" + p;
  }

  function auditEventsFetchPath() {
    const params = new URLSearchParams();
    let lim = 50;
    const limEl = document.getElementById("auditLimit");
    if (limEl && String(limEl.value).trim()) {
      const n = parseInt(String(limEl.value).trim(), 10);
      if (!isNaN(n)) {
        lim = Math.min(500, Math.max(1, n));
      }
    }
    params.set("limit", String(lim));
    const a = document.getElementById("auditFilterAction");
    const t = document.getElementById("auditFilterResourceType");
    const r = document.getElementById("auditFilterResourceId");
    if (a && a.value.trim()) {
      params.set("action", a.value.trim());
    }
    if (t && t.value.trim()) {
      params.set("resource_type", t.value.trim());
    }
    if (r && r.value.trim()) {
      params.set("resource_id", r.value.trim());
    }
    return "/admin/audit-events?" + params.toString();
  }

  function appendAuditToolbar(container, onApply) {
    const legacyAction = document.getElementById("auditFilterAction");
    if (legacyAction && !document.getElementById("auditLimit")) {
      const row = legacyAction.closest(".admin-toolbar");
      if (row && row.parentNode) {
        row.parentNode.removeChild(row);
      }
    }
    if (document.getElementById("auditLimit")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "admin-toolbar";
    box.setAttribute("data-testid", "admin-audit-toolbar");
    const mk = (id, label, ph) => {
      const l = document.createElement("label");
      l.className = "small";
      l.textContent = label;
      const inp = document.createElement("input");
      inp.type = "text";
      inp.id = id;
      inp.placeholder = ph;
      l.appendChild(inp);
      return l;
    };
    box.appendChild(mk("auditFilterAction", "action", "message.retention…"));
    box.appendChild(mk("auditFilterResourceType", "resource_type", "message"));
    box.appendChild(mk("auditFilterResourceId", "resource_id", "UUID"));
    const limLbl = document.createElement("label");
    limLbl.className = "small";
    limLbl.textContent = LT("limit");
    const limInp = document.createElement("input");
    limInp.type = "number";
    limInp.id = "auditLimit";
    limInp.min = "1";
    limInp.max = "500";
    limInp.value = "50";
    limInp.placeholder = "50";
    limLbl.appendChild(limInp);
    box.appendChild(limLbl);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.setAttribute("data-testid", "admin-audit-apply");
    btn.textContent = "Загрузить";
    btn.addEventListener("click", () => onApply());
    box.appendChild(btn);
    container.appendChild(box);
    appendExportAuditPresets(container, onApply);
  }

  function appendExportAuditPresets(container, onApply) {
    if (document.getElementById("exportAuditPresets")) {
      return;
    }
    const row = document.createElement("div");
    row.id = "exportAuditPresets";
    row.className = "admin-toolbar export-audit-presets";
    const cap = document.createElement("span");
    cap.className = "small muted";
    cap.textContent = LT("Export:");
    row.appendChild(cap);
    const presets = [
      { label: "requested", action: "export.requested", resourceType: "export_job", resourceId: "" },
      { label: "downloaded", action: "export.downloaded", resourceType: "export_job", resourceId: "" },
      { label: "suggested", action: "export.suggested", resourceType: "chat", resourceId: "" },
      { label: "auto_queued", action: "export.auto_queued", resourceType: "export_job", resourceId: "" },
      { label: "skip", action: "export.auto_queue_skipped", resourceType: "chat", resourceId: "" },
      { label: "admin_view", action: "export.admin_inspected", resourceType: "export_job", resourceId: "" },
      { label: "admin_dl", action: "export.admin_downloaded", resourceType: "export_job", resourceId: "" },
      { label: "cancelled", action: "export.cancelled", resourceType: "export_job", resourceId: "" },
      { label: "admin_cancel", action: "export.admin_cancelled", resourceType: "export_job", resourceId: "" }
    ];
    presets.forEach((p) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "small";
      btn.textContent = p.label;
      btn.addEventListener("click", () => {
        const a = document.getElementById("auditFilterAction");
        const t = document.getElementById("auditFilterResourceType");
        const r = document.getElementById("auditFilterResourceId");
        if (a) {
          a.value = p.action;
        }
        if (t) {
          t.value = p.resourceType;
        }
        if (r) {
          r.value = p.resourceId;
        }
        onApply();
      });
      row.appendChild(btn);
    });
    container.appendChild(row);
  }

  function appendJsonPanelReload(container, onReload) {
    container.querySelectorAll(".json-panel-reload").forEach((n) => n.remove());
    const wrap = document.createElement("div");
    wrap.className = "admin-toolbar json-panel-reload";
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = "Обновить";
    btn.addEventListener("click", () => {
      Promise.resolve(onReload()).catch(() => {});
    });
    wrap.appendChild(btn);
    container.appendChild(wrap);
  }

  function appendOrgCreateToolbar(container, onCreated) {
    if (document.getElementById("newOrgName")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "admin-toolbar";
    const lbl = document.createElement("label");
    lbl.className = "small";
    lbl.textContent = "Имя";
    const inp = document.createElement("input");
    inp.type = "text";
    inp.id = "newOrgName";
    inp.placeholder = "организация";
    lbl.appendChild(inp);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = "Создать";
    const msg = document.createElement("span");
    msg.id = "createOrgMsg";
    msg.className = "muted small";
    btn.addEventListener("click", async () => {
      msg.textContent = "";
      const name = inp.value.trim();
      if (!name) {
        msg.textContent = "Введите имя";
        return;
      }
      try {
        await apiFetch("/admin/organizations", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: name }),
        });
        inp.value = "";
        msg.textContent = "Создано.";
        await onCreated();
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });
    box.appendChild(lbl);
    box.appendChild(btn);
    box.appendChild(msg);
    container.appendChild(box);
  }

  function appendOrgDeleteToolbar(container, onDeleted) {
    if (document.getElementById("deleteOrgId")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "admin-toolbar";
    const lbl = document.createElement("label");
    lbl.className = "small";
    lbl.textContent = LT("UUID");
    const inp = document.createElement("input");
    inp.type = "text";
    inp.id = "deleteOrgId";
    inp.placeholder = "uuid организации";
    lbl.appendChild(inp);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = "Удалить";
    const msg = document.createElement("span");
    msg.id = "deleteOrgMsg";
    msg.className = "muted small";
    btn.addEventListener("click", async () => {
      msg.textContent = "";
      const id = inp.value.trim();
      if (!id) {
        msg.textContent = "Введите UUID";
        return;
      }
      if (!window.confirm("Удалить организацию " + id + "?")) {
        return;
      }
      try {
        await apiFetch("/admin/organizations/" + encodeURIComponent(id), { method: "DELETE" });
        inp.value = "";
        msg.textContent = "Удалено.";
        await onDeleted();
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });
    box.appendChild(lbl);
    box.appendChild(btn);
    box.appendChild(msg);
    container.appendChild(box);
  }

  function appendOrgLogoToolbar(container, onUpdated) {
    if (document.getElementById("orgLogoOrgId")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "admin-toolbar";
    const lOrg = document.createElement("label");
    lOrg.className = "small";
    lOrg.textContent = LT("org_id");
    const inOrg = document.createElement("input");
    inOrg.type = "text";
    inOrg.id = "orgLogoOrgId";
    inOrg.placeholder = "UUID организации";
    lOrg.appendChild(inOrg);
    const lLogo = document.createElement("label");
    lLogo.className = "small";
    lLogo.textContent = "logo_file_id";
    const inLogo = document.createElement("input");
    inLogo.type = "text";
    inLogo.id = "orgLogoFileId";
    inLogo.placeholder = "UUID файла или пусто";
    lLogo.appendChild(inLogo);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = "Логотип";
    btn.setAttribute("data-testid", "admin-org-logo-patch");
    const msg = document.createElement("span");
    msg.id = "orgLogoMsg";
    msg.className = "muted small";
    btn.addEventListener("click", async () => {
      msg.textContent = "";
      const oid = inOrg.value.trim();
      if (!oid) {
        msg.textContent = "Нужен org_id";
        return;
      }
      const body = { logo_file_id: inLogo.value.trim() || null };
      try {
        const data = await apiFetch("/admin/organizations/" + encodeURIComponent(oid), {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        pre.textContent = JSON.stringify(data, null, 2);
        msg.textContent = "Готово.";
        await onUpdated();
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });
    box.appendChild(lOrg);
    box.appendChild(lLogo);
    box.appendChild(btn);
    box.appendChild(msg);
    container.appendChild(box);
  }

  function cellPreview(v) {
    if (v === null || v === undefined) {
      return "";
    }
    const s = typeof v === "object" ? JSON.stringify(v) : String(v);
    return s.length > 120 ? s.slice(0, 117) + "…" : s;
  }

  function renderArrayTable(rows, container) {
    if (!Array.isArray(rows) || rows.length === 0) {
      const p = document.createElement("p");
      p.className = "muted small json-panel-note";
      p.textContent = "Нет строк для таблицы.";
      container.appendChild(p);
      return;
    }
    if (!rows.every((x) => x && typeof x === "object" && !Array.isArray(x))) {
      const p = document.createElement("p");
      p.className = "muted small json-panel-note";
      p.textContent = "Таблица только для массива объектов; см. JSON ниже.";
      container.appendChild(p);
      return;
    }
    const keySet = new Set();
    rows.slice(0, 80).forEach((o) => {
      Object.keys(o).forEach((k) => keySet.add(k));
    });
    const keys = Array.from(keySet).slice(0, 14);
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap";
    const table = document.createElement("table");
    table.className = "json-panel-table";
    const thead = document.createElement("thead");
    const trh = document.createElement("tr");
    keys.forEach((k) => {
      const th = document.createElement("th");
      th.textContent = k;
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    table.appendChild(thead);
    const tbody = document.createElement("tbody");
    const maxRows = Math.min(rows.length, 100);
    for (let i = 0; i < maxRows; i++) {
      const tr = document.createElement("tr");
      const o = rows[i];
      keys.forEach((k) => {
        const td = document.createElement("td");
        td.textContent = cellPreview(o[k]);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);
    wrap.appendChild(table);
    container.appendChild(wrap);
    if (rows.length > maxRows) {
      const note = document.createElement("p");
      note.className = "muted small json-panel-note";
      note.textContent = "Показаны первые " + maxRows + " из " + rows.length + " строк.";
      container.appendChild(note);
    }
  }

  function fillRetentionPatchForm(data) {
    const setNum = (id, val) => {
      const inp = document.getElementById(id);
      if (!inp) {
        return;
      }
      inp.value = val == null ? "" : String(val);
    };
    const setChk = (id, val) => {
      const inp = document.getElementById(id);
      if (!inp) {
        return;
      }
      inp.checked = !!val;
    };
    if (!data || typeof data !== "object") {
      return;
    }
    setNum("retentionPatchHotBody", data.hot_message_body_max_age_days);
    setNum("retentionPatchHotMeta", data.hot_metadata_min_age_days);
    setChk("retentionPatchArchMeta", data.archive_metadata_enabled);
    setChk("retentionPatchDeep", data.deep_archive_enabled);
    setChk("retentionPatchLegal", data.legal_hold);
  }

  function readRetentionPatchRequestBody() {
    const bodyEl = document.getElementById("retentionPatchHotBody");
    const metaEl = document.getElementById("retentionPatchHotMeta");
    const archEl = document.getElementById("retentionPatchArchMeta");
    const deepEl = document.getElementById("retentionPatchDeep");
    const legalEl = document.getElementById("retentionPatchLegal");
    if (!bodyEl || !metaEl || !archEl || !deepEl || !legalEl) {
      return null;
    }
    const parseOptInt = (inp) => {
      const s = String(inp.value).trim();
      if (s === "") {
        return null;
      }
      const n = parseInt(s, 10);
      if (isNaN(n)) {
        throw new Error("Некорректное число дней: " + s);
      }
      return n;
    };
    return {
      hot_message_body_max_age_days: parseOptInt(bodyEl),
      hot_metadata_min_age_days: parseOptInt(metaEl),
      archive_metadata_enabled: archEl.checked,
      deep_archive_enabled: deepEl.checked,
      legal_hold: legalEl.checked,
    };
  }

  function appendRetentionPatchForm(summary) {
    if (document.getElementById("retentionPatchSubmit")) {
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "retention-patch-block";
    wrap.setAttribute("data-testid", "admin-retention-patch-form");
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "Сохранить PATCH для последней загруженной org или chat. Пустые поля дней → null. Три флага — из чекбоксов.";
    wrap.appendChild(hint);
    const row = document.createElement("div");
    row.className = "admin-toolbar retention-patch-fields";
    const mkNum = (id, label) => {
      const l = document.createElement("label");
      l.className = "small";
      l.textContent = label;
      const inp = document.createElement("input");
      inp.type = "number";
      inp.min = "0";
      inp.id = id;
      inp.placeholder = "пусто = null";
      l.appendChild(inp);
      return l;
    };
    const mkChk = (id, label) => {
      const l = document.createElement("label");
      l.className = "small";
      const inp = document.createElement("input");
      inp.type = "checkbox";
      inp.id = id;
      if (id === "retentionPatchLegal") {
        inp.setAttribute("data-testid", "admin-retention-patch-legal");
      }
      l.appendChild(inp);
      l.appendChild(document.createTextNode(" " + label));
      return l;
    };
    row.appendChild(mkNum("retentionPatchHotBody", "hot_body_days"));
    row.appendChild(mkNum("retentionPatchHotMeta", "hot_meta_days"));
    row.appendChild(mkChk("retentionPatchArchMeta", "archive_metadata"));
    row.appendChild(mkChk("retentionPatchDeep", "deep_archive"));
    row.appendChild(mkChk("retentionPatchLegal", "legal_hold"));
    const btn = document.createElement("button");
    btn.type = "button";
    btn.id = "retentionPatchSubmit";
    btn.setAttribute("data-testid", "admin-retention-patch-submit");
    btn.textContent = "Применить PATCH";
    const msg = document.createElement("span");
    msg.id = "retentionPatchMsg";
    msg.className = "muted small";
    btn.addEventListener("click", async () => {
      msg.textContent = "";
      if (!retentionPatchTarget) {
        msg.textContent = "Сначала загрузите политику (org или chat).";
        return;
      }
      let bodyJson;
      try {
        bodyJson = readRetentionPatchRequestBody();
      } catch (e) {
        msg.textContent = e.message || String(e);
        return;
      }
      if (!bodyJson) {
        msg.textContent = "Форма не найдена.";
        return;
      }
      const path =
        retentionPatchTarget.kind === "org"
          ? "/admin/organizations/" + encodeURIComponent(retentionPatchTarget.id) + "/retention"
          : "/admin/chats/" + encodeURIComponent(retentionPatchTarget.id) + "/retention";
      try {
        const data = await apiFetch(path, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(bodyJson),
        });
        const pre = el("panelContent");
        if (pre) {
          pre.textContent = JSON.stringify(data, null, 2);
        }
        const sc = document.getElementById("retentionLastFetch");
        if (sc) {
          sc.textContent = "Последний запрос: PATCH " + API + path;
        }
        const sum = el("panelSummary");
        if (sum) {
          sum.querySelector(".json-table-wrap")?.remove();
          sum.querySelectorAll(".json-panel-note").forEach((n) => n.remove());
          renderFlatObjectTable(data, sum);
        }
        fillRetentionPatchForm(data);
        msg.textContent = "Сохранено.";
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });
    row.appendChild(btn);
    row.appendChild(msg);
    wrap.appendChild(row);
    summary.appendChild(wrap);
  }

  function fillAuthPolicyForm(data) {
    const setChk = (id, val) => {
      const inp = document.getElementById(id);
      if (inp) {
        inp.checked = !!val;
      }
    };
    const ta = document.getElementById("authPolicyProvidersJson");
    if (!data || typeof data !== "object") {
      return;
    }
    setChk("authPolicyAllowLocal", data.allow_local_password);
    setChk("authPolicyAllowReg", data.allow_self_registration);
    setChk("authPolicyApplyKc", data.apply_to_keycloak !== false);
    if (ta) {
      ta.value = JSON.stringify(data.providers || [], null, 2);
    }
    const pid = document.getElementById("authPolicyTestProviderId");
    if (pid) {
      pid.value = "";
    }
  }

  function readAuthPolicyPatchBody() {
    const allowLocal = document.getElementById("authPolicyAllowLocal");
    const allowReg = document.getElementById("authPolicyAllowReg");
    const applyKc = document.getElementById("authPolicyApplyKc");
    const ta = document.getElementById("authPolicyProvidersJson");
    if (!allowLocal || !allowReg || !applyKc || !ta) {
      return null;
    }
    let providers;
    const raw = ta.value.trim();
    if (raw === "") {
      providers = [];
    } else {
      try {
        providers = JSON.parse(raw);
        if (!Array.isArray(providers)) {
          throw new Error("providers должен быть JSON-массивом");
        }
      } catch (e) {
        throw new Error("Некорректный JSON providers: " + (e.message || String(e)));
      }
    }
    return {
      allow_local_password: allowLocal.checked,
      allow_self_registration: allowReg.checked,
      apply_to_keycloak: applyKc.checked,
      providers: providers,
    };
  }

  function appendAuthPolicyForm(summary) {
    if (document.getElementById("authPolicyPatchSubmit")) {
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "auth-policy-block";
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "PATCH /admin/orgs/{orgId}/auth-policy — флаги и providers (JSON). apply_to_keycloak синхронизирует Keycloak.";
    wrap.appendChild(hint);
    const flags = document.createElement("div");
    flags.className = "admin-toolbar auth-policy-fields";
    const mkChk = (id, label, checked) => {
      const l = document.createElement("label");
      l.className = "small";
      const inp = document.createElement("input");
      inp.type = "checkbox";
      inp.id = id;
      if (checked) {
        inp.checked = true;
      }
      l.appendChild(inp);
      l.appendChild(document.createTextNode(" " + label));
      return l;
    };
    flags.appendChild(mkChk("authPolicyAllowLocal", "allow_local_password", true));
    flags.appendChild(mkChk("authPolicyAllowReg", "allow_self_registration", false));
    flags.appendChild(mkChk("authPolicyApplyKc", "apply_to_keycloak", true));
    wrap.appendChild(flags);
    const provHint = document.createElement("p");
    provHint.className = "muted small";
    provHint.textContent = LT("providers — JSON-массив или добавьте строку ниже:");
    wrap.appendChild(provHint);
    const ta = document.createElement("textarea");
    ta.id = "authPolicyProvidersJson";
    ta.rows = 8;
    ta.className = "auth-policy-providers-json";
    ta.placeholder = "[]";
    ta.spellcheck = false;
    wrap.appendChild(ta);
    const addRow = document.createElement("div");
    addRow.className = "admin-toolbar";
    const lType = document.createElement("label");
    lType.className = "small";
    lType.textContent = LT("type");
    const selType = document.createElement("select");
    selType.id = "authPolicyAddType";
    ["ldap", "oidc", "saml"].forEach((t) => {
      const o = document.createElement("option");
      o.value = t;
      o.textContent = t;
      selType.appendChild(o);
    });
    lType.appendChild(selType);
    const lAlias = document.createElement("label");
    lAlias.className = "small";
    lAlias.textContent = LT("alias");
    const inAlias = document.createElement("input");
    inAlias.type = "text";
    inAlias.id = "authPolicyAddAlias";
    inAlias.placeholder = "corp-ldap";
    lAlias.appendChild(inAlias);
    const lName = document.createElement("label");
    lName.className = "small";
    lName.textContent = LT("display_name");
    const inName = document.createElement("input");
    inName.type = "text";
    inName.id = "authPolicyAddDisplayName";
    inName.placeholder = "Корпоративный вход";
    lName.appendChild(inName);
    const lEn = document.createElement("label");
    lEn.className = "small";
    const inEn = document.createElement("input");
    inEn.type = "checkbox";
    inEn.id = "authPolicyAddEnabled";
    inEn.checked = true;
    lEn.appendChild(inEn);
    lEn.appendChild(document.createTextNode(" " + LT("enabled")));
    const addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.textContent = "Добавить провайдер";
    const addMsg = document.createElement("span");
    addMsg.className = "muted small";
    addBtn.addEventListener("click", () => {
      addMsg.textContent = "";
      const alias = inAlias.value.trim();
      if (!alias) {
        addMsg.textContent = "Укажите alias";
        return;
      }
      let list = [];
      const raw = ta.value.trim();
      if (raw) {
        try {
          list = JSON.parse(raw);
          if (!Array.isArray(list)) {
            throw new Error("не массив");
          }
        } catch (e) {
          addMsg.textContent = "Исправьте JSON в textarea";
          return;
        }
      }
      const displayName = inName.value.trim() || alias;
      list.push({
        id: alias,
        type: selType.value,
        alias: alias,
        display_name: displayName,
        priority: 0,
        enabled: inEn.checked,
        settings: {},
      });
      ta.value = JSON.stringify(list, null, 2);
      addMsg.textContent = "Добавлено.";
    });
    addRow.appendChild(lType);
    addRow.appendChild(lAlias);
    addRow.appendChild(lName);
    addRow.appendChild(lEn);
    addRow.appendChild(addBtn);
    addRow.appendChild(addMsg);
    wrap.appendChild(addRow);
    const actions = document.createElement("div");
    actions.className = "admin-toolbar";
    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.id = "authPolicyPatchSubmit";
    saveBtn.textContent = "Сохранить PATCH";
    const saveMsg = document.createElement("span");
    saveMsg.id = "authPolicyPatchMsg";
    saveMsg.className = "muted small";
    saveBtn.addEventListener("click", async () => {
      saveMsg.textContent = "";
      if (!authPolicyTargetOrgId) {
        saveMsg.textContent = "Сначала загрузите политику организации.";
        return;
      }
      let bodyJson;
      try {
        bodyJson = readAuthPolicyPatchBody();
      } catch (e) {
        saveMsg.textContent = e.message || String(e);
        return;
      }
      if (!bodyJson) {
        saveMsg.textContent = "Форма не найдена.";
        return;
      }
      const path = "/admin/orgs/" + encodeURIComponent(authPolicyTargetOrgId) + "/auth-policy";
      try {
        const data = await apiFetch(path, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(bodyJson),
        });
        const pre = el("panelContent");
        if (pre) {
          pre.textContent = JSON.stringify(data, null, 2);
        }
        const sc = document.getElementById("authPolicyLastFetch");
        if (sc) {
          sc.textContent = "Последний запрос: PATCH " + API + path;
        }
        const sum = el("panelSummary");
        if (sum) {
          sum.querySelector(".json-table-wrap")?.remove();
          sum.querySelectorAll(".json-panel-note").forEach((n) => n.remove());
          renderFlatObjectTable(data, sum);
        }
        fillAuthPolicyForm(data);
        saveMsg.textContent = "Сохранено.";
      } catch (e) {
        saveMsg.textContent = e.message || String(e);
      }
    });
    const lTestProv = document.createElement("label");
    lTestProv.className = "small";
    lTestProv.textContent = LT("provider_id (опц.)");
    const inTestProv = document.createElement("input");
    inTestProv.type = "text";
    inTestProv.id = "authPolicyTestProviderId";
    inTestProv.placeholder = "пусто = первый enabled ldap";
    lTestProv.appendChild(inTestProv);
    const testBtn = document.createElement("button");
    testBtn.type = "button";
    testBtn.textContent = "Тест LDAP";
    const testMsg = document.createElement("span");
    testMsg.id = "authPolicyTestMsg";
    testMsg.className = "muted small";
    testBtn.addEventListener("click", async () => {
      testMsg.textContent = "";
      if (!authPolicyTargetOrgId) {
        testMsg.textContent = "Сначала загрузите политику организации.";
        return;
      }
      const path = "/admin/orgs/" + encodeURIComponent(authPolicyTargetOrgId) + "/auth-policy/test";
      const providerId = inTestProv.value.trim();
      const body = providerId ? { provider_id: providerId } : {};
      try {
        const data = await apiFetch(path, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        testMsg.textContent = data && data.ok ? "OK: " + data.message : "Fail: " + (data && data.message ? data.message : "?");
      } catch (e) {
        testMsg.textContent = e.message || String(e);
      }
    });
    actions.appendChild(saveBtn);
    actions.appendChild(saveMsg);
    actions.appendChild(lTestProv);
    actions.appendChild(testBtn);
    actions.appendChild(testMsg);
    wrap.appendChild(actions);
    summary.appendChild(wrap);
  }

  function renderBrandingPanel(summary, pre, cap) {
    const palettes = ["korus", "vtb", "alfa", "rzd", "sfr", "sberbank"];
    const tokenKeys = ["--accent", "--bg", "--text"];
    const previewStyleId = "adminBrandingPreviewStyle";
    let lastLoaded = null;

    const row = document.createElement("div");
    row.className = "admin-toolbar";
    row.setAttribute("data-testid", "admin-branding-toolbar");

    const scopeLabel = document.createElement("label");
    scopeLabel.className = "small";
    scopeLabel.textContent = L("admin.branding.scopeLabel");
    const scopeSelect = document.createElement("select");
    scopeSelect.id = "brandingScope";
    [
      { value: "global", text: L("admin.branding.scopeGlobal") },
      { value: "org", text: L("admin.branding.scopeOrg") },
    ].forEach((o) => {
      const opt = document.createElement("option");
      opt.value = o.value;
      opt.textContent = o.text;
      scopeSelect.appendChild(opt);
    });
    scopeLabel.appendChild(scopeSelect);
    row.appendChild(scopeLabel);

    const paletteLabel = document.createElement("label");
    paletteLabel.className = "small";
    paletteLabel.textContent = L("admin.branding.paletteLabel");
    const paletteSelect = document.createElement("select");
    paletteSelect.id = "brandingPalette";
    palettes.forEach((palette) => {
      const opt = document.createElement("option");
      opt.value = palette;
      opt.textContent = palette;
      paletteSelect.appendChild(opt);
    });
    paletteLabel.appendChild(paletteSelect);
    row.appendChild(paletteLabel);

    const titleLabel = document.createElement("label");
    titleLabel.className = "small";
    titleLabel.textContent = L("admin.branding.titleLabel");
    const titleInput = document.createElement("input");
    titleInput.type = "text";
    titleInput.id = "brandingBrandTitle";
    titleInput.placeholder = L("admin.branding.titlePlaceholder");
    titleLabel.appendChild(titleInput);
    row.appendChild(titleLabel);

    const orgInfo = document.createElement("span");
    orgInfo.className = "muted small";
    orgInfo.id = "brandingOrgInfo";
    row.appendChild(orgInfo);

    summary.appendChild(row);

    const tokenGrid = document.createElement("div");
    tokenGrid.className = "form-grid";
    const tokenInputs = {};
    tokenKeys.forEach((tokenKey) => {
      const label = document.createElement("label");
      label.className = "field";
      const capEl = document.createElement("span");
      capEl.className = "field-label";
      capEl.textContent = tokenKey;
      const input = document.createElement("input");
      input.type = "text";
      input.id = "brandingToken" + tokenKey.replace(/[^a-z0-9]/gi, "");
      input.placeholder = L("admin.branding.tokenPlaceholder");
      label.appendChild(capEl);
      label.appendChild(input);
      tokenGrid.appendChild(label);
      tokenInputs[tokenKey] = input;
    });
    summary.appendChild(tokenGrid);

    const cssLabel = document.createElement("label");
    cssLabel.className = "field";
    const cssCaption = document.createElement("span");
    cssCaption.className = "field-label";
    cssCaption.textContent = L("admin.branding.customCssLabel");
    const cssArea = document.createElement("textarea");
    cssArea.id = "brandingCustomCss";
    cssArea.rows = 8;
    cssArea.className = "json-editor";
    cssArea.placeholder = "/* " + L("admin.branding.customCssPlaceholder") + " */";
    cssArea.spellcheck = false;
    cssLabel.appendChild(cssCaption);
    cssLabel.appendChild(cssArea);
    summary.appendChild(cssLabel);

    const demoRow = document.createElement("div");
    demoRow.className = "admin-toolbar";
    const demoLabel = document.createElement("label");
    demoLabel.className = "small";
    const demoCheckbox = document.createElement("input");
    demoCheckbox.type = "checkbox";
    demoCheckbox.id = "brandingDemoSkinsEnabled";
    demoLabel.appendChild(demoCheckbox);
    demoLabel.appendChild(document.createTextNode(" " + L("admin.branding.demoSkinsLabel")));
    demoRow.appendChild(demoLabel);
    summary.appendChild(demoRow);

    const actions = document.createElement("div");
    actions.className = "admin-toolbar";
    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.textContent = L("admin.branding.save");
    saveBtn.setAttribute("data-testid", "admin-branding-save");
    const resetBtn = document.createElement("button");
    resetBtn.type = "button";
    resetBtn.textContent = L("admin.branding.reset");
    resetBtn.setAttribute("data-testid", "admin-branding-reset");
    const previewBtn = document.createElement("button");
    previewBtn.type = "button";
    previewBtn.textContent = L("admin.branding.preview");
    previewBtn.setAttribute("data-testid", "admin-branding-preview");
    const msg = document.createElement("span");
    msg.className = "muted small";
    msg.id = "brandingMsg";
    actions.appendChild(saveBtn);
    actions.appendChild(resetBtn);
    actions.appendChild(previewBtn);
    actions.appendChild(msg);
    summary.appendChild(actions);

    function orgIdFromContext() {
      const orgEl = document.getElementById("globalOrgId");
      return orgEl ? orgEl.value.trim() : "";
    }

    function currentScope() {
      return scopeSelect.value === "org" ? "org" : "global";
    }

    function resolveTarget(showErrors) {
      if (currentScope() === "org") {
        const orgId = orgIdFromContext();
        if (!orgId) {
          if (showErrors) {
            msg.textContent = L("admin.branding.orgRequired");
          }
          return null;
        }
        return {
          path: "/admin/branding/orgs/" + encodeURIComponent(orgId),
          kind: "org",
          orgId: orgId,
        };
      }
      return { path: "/admin/branding/platform", kind: "global", orgId: null };
    }

    function normalizeForm() {
      const payload = {
        palette: paletteSelect.value,
        token_overrides: {},
        custom_css: cssArea.value.trim() || null,
        brand_title: titleInput.value.trim() || null,
      };
      tokenKeys.forEach((tokenKey) => {
        const v = tokenInputs[tokenKey].value.trim();
        if (v) {
          payload.token_overrides[tokenKey] = v;
        }
      });
      if (currentScope() === "global") {
        payload.demo_skins_enabled = !!demoCheckbox.checked;
      }
      return payload;
    }

    function fillForm(data) {
      const obj = data && typeof data === "object" ? data : {};
      paletteSelect.value = obj.palette && palettes.includes(obj.palette) ? obj.palette : "korus";
      tokenKeys.forEach((tokenKey) => {
        tokenInputs[tokenKey].value =
          obj.token_overrides && typeof obj.token_overrides === "object"
            ? String(obj.token_overrides[tokenKey] || "")
            : "";
      });
      cssArea.value = obj.custom_css || "";
      titleInput.value = obj.brand_title || "";
      demoCheckbox.checked = !!obj.demo_skins_enabled;
      lastLoaded = obj;
    }

    function applyPreview(payload) {
      const root = document.documentElement;
      root.setAttribute("data-palette", payload.palette || "korus");
      tokenKeys.forEach((tokenKey) => {
        const v = payload.token_overrides && payload.token_overrides[tokenKey];
        if (v) {
          root.style.setProperty(tokenKey, v);
        } else {
          root.style.removeProperty(tokenKey);
        }
      });
      let styleEl = document.getElementById(previewStyleId);
      if (!styleEl) {
        styleEl = document.createElement("style");
        styleEl.id = previewStyleId;
        document.head.appendChild(styleEl);
      }
      styleEl.textContent = payload.custom_css || "";
      const brandTitleNode = document.querySelector(".brand-title");
      if (brandTitleNode) {
        if (!brandTitleNode.dataset.defaultTitle) {
          brandTitleNode.dataset.defaultTitle = brandTitleNode.textContent || "";
        }
        brandTitleNode.textContent =
          payload.brand_title && payload.brand_title.trim()
            ? payload.brand_title.trim()
            : brandTitleNode.dataset.defaultTitle;
      }
      msg.textContent = L("admin.branding.previewApplied");
    }

    function refreshScopeUi() {
      const target = resolveTarget(false);
      if (target && target.kind === "org") {
        orgInfo.textContent = L("admin.branding.orgCurrent", { org: target.orgId });
      } else {
        orgInfo.textContent = L("admin.branding.globalCurrent");
      }
      demoRow.hidden = currentScope() !== "global";
    }

    async function loadCurrent() {
      msg.textContent = "";
      const target = resolveTarget(true);
      refreshScopeUi();
      if (!target) {
        pre.textContent = L("admin.branding.orgRequired");
        return;
      }
      try {
        const data = await apiFetch(target.path);
        lastLoaded = data || null;
        fillForm(data || {});
        if (cap) {
          cap.textContent = "Источник: GET " + API + target.path;
        }
        pre.textContent = JSON.stringify(data, null, 2);
      } catch (e) {
        pre.textContent = "Ошибка: " + (e.message || String(e));
        msg.textContent = e.message || String(e);
      }
    }

    scopeSelect.addEventListener("change", () => {
      loadCurrent();
    });

    previewBtn.addEventListener("click", () => {
      applyPreview(normalizeForm());
    });

    saveBtn.addEventListener("click", async () => {
      msg.textContent = "";
      const target = resolveTarget(true);
      if (!target) {
        return;
      }
      const payload = normalizeForm();
      try {
        const data = await apiFetch(target.path, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        lastLoaded = data || null;
        fillForm(data || payload);
        if (cap) {
          cap.textContent = "Источник: PUT " + API + target.path;
        }
        pre.textContent = JSON.stringify(data, null, 2);
        msg.textContent = L("admin.branding.saved");
      } catch (e) {
        msg.textContent = e.message || String(e);
        pre.textContent = "Ошибка: " + (e.message || String(e));
      }
    });

    resetBtn.addEventListener("click", async () => {
      await loadCurrent();
      if (lastLoaded) {
        applyPreview({
          palette: lastLoaded.palette || "korus",
          token_overrides: lastLoaded.token_overrides || {},
          custom_css: lastLoaded.custom_css || "",
          brand_title: lastLoaded.brand_title || "",
        });
      }
      msg.textContent = L("admin.branding.resetDone");
    });

    refreshScopeUi();
    loadCurrent();
  }

  function renderFlatObjectTable(obj, container) {
    if (!obj || typeof obj !== "object" || Array.isArray(obj)) {
      const p = document.createElement("p");
      p.className = "muted small json-panel-note";
      p.textContent = "Сводка только для плоского объекта; см. JSON ниже.";
      container.appendChild(p);
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap";
    const table = document.createElement("table");
    table.className = "json-panel-table";
    const tbody = document.createElement("tbody");
    const keys = Object.keys(obj).sort();
    keys.forEach((k) => {
      const tr = document.createElement("tr");
      const th = document.createElement("th");
      th.textContent = k;
      const td = document.createElement("td");
      const v = obj[k];
      if (v !== null && typeof v === "object") {
        td.textContent = cellPreview(v);
      } else {
        td.textContent = v === null || v === undefined ? "" : String(v);
      }
      tr.appendChild(th);
      tr.appendChild(td);
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    wrap.appendChild(table);
    container.appendChild(wrap);
  }

  function token() {
    return sessionStorage.getItem(LS_KEY) || localStorage.getItem(LS_KEY) || "";
  }

  function setToken(t) {
    sessionStorage.setItem(LS_KEY, t);
  }

  async function apiFetch(path, options) {
    const headers = Object.assign({ Accept: "application/json" }, options && options.headers);
    const tok = token();
    if (tok) {
      headers.Authorization = "Bearer " + tok;
    }
    const res = await fetch(API + path, Object.assign({}, options, { headers }));
    const text = await res.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch (_) {}
    if (!res.ok) {
      if (res.status === 401) {
        clearAuthStorage();
        updateLogoutButton();
      }
      const msg = body && body.message ? body.message : res.status + " " + res.statusText;
      throw new Error(msg);
    }
    return body;
  }

  async function apiDownload(path, filename) {
    const headers = {};
    const tok = token();
    if (tok) {
      headers.Authorization = "Bearer " + tok;
    }
    const res = await fetch(API + path, { headers });
    if (!res.ok) {
      if (res.status === 401) {
        clearAuthStorage();
        updateLogoutButton();
      }
      let msg = res.status + " " + res.statusText;
      try {
        const err = await res.json();
        if (err && err.message) {
          msg = err.message;
        }
      } catch (_) {}
      throw new Error(msg);
    }
    const blob = await res.blob();
    const name =
      filename ||
      (res.headers.get("Content-Disposition") || "").replace(/^.*filename="?([^";]+)"?.*$/, "$1") ||
      "export-download.bin";
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(a.href);
  }

  async function refreshAuthStatus() {
    if (!token()) {
      return;
    }
    try {
      const sess = await apiFetch("/admin/session");
      el("authStatus").textContent = L("admin.signedIn", {
        user: sess.username || sess.user_id || "?",
      });
    } catch (_) {
      el("authStatus").textContent = LT("Токен сохранён; не удалось загрузить /admin/session.");
    }
  }

  async function login() {
    const username = el("username").value.trim();
    const password = el("password").value;
    el("authStatus").textContent = "";
    const res = await fetch(API + "/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ username, password }),
    });
    const text = await res.text();
    let body = text ? JSON.parse(text) : {};
    if (!res.ok) {
      el("authStatus").textContent = body.message || LT("Ошибка входа");
      return;
    }
    setToken(body.access_token);
    const rt = body.refresh_token || body.refreshToken;
    if (rt) {
      sessionStorage.setItem(LS_REFRESH, rt);
    } else {
      sessionStorage.removeItem(LS_REFRESH);
    }
    updateLogoutButton();
    await refreshAuthStatus();
    await loadManifest();
  }

  function renderSections(sections, preserveActiveSectionId) {
    if (window.AdminUi) {
      AdminUi.renderGroupedNav(
        sections,
        (s, li) => selectSection(s, li),
        preserveActiveSectionId
      );
      return;
    }
    const ul = el("sectionList");
    ul.innerHTML = "";
    sections.forEach((s, idx) => {
      const li = document.createElement("li");
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = s.title;
      btn.addEventListener("click", () => selectSection(s, li));
      li.appendChild(btn);
      ul.appendChild(li);
      if (idx === 0) {
        selectSection(s, li);
      }
    });
  }

  async function selectSection(section, liNode) {
    if (window.AdminUi) {
      AdminUi.setActiveNav(liNode);
      AdminUi.setPanelHeader(section);
      AdminUi.setActiveSectionId(section.id);
      AdminUi.setOnOrgApply(() => selectSection(section, liNode));
    } else {
      document.querySelectorAll(".nav-list li, .sidebar li").forEach((n) => n.classList.remove("active"));
      if (liNode) {
        liNode.classList.add("active");
      }
    }
    const pre = el("panelContent");
    const summary = el("panelSummary");
    summary.hidden = true;
    summary.innerHTML = "";
    pre.hidden = false;
    pre.classList.remove("is-collapsed");
    if (window.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
    pre.textContent = "Загрузка…";
    try {
      if (section.kind === "core_stats" && section.data_path) {
        let p = section.data_path;
        if (!p.startsWith("/")) {
          p = "/" + p;
        }
        const statsPath = p;
        summary.hidden = false;
        summary.innerHTML = "";
        const reloadStats = async () => {
          try {
            pre.textContent = "Загрузка…";
            const stats = await apiFetch(statsPath);
            renderCoreStatsSummary(stats, summary);
            pre.textContent = JSON.stringify(stats, null, 2);
            appendJsonPanelReload(summary, reloadStats);
          } catch (e) {
            pre.textContent = "Ошибка: " + e.message;
          }
        };
        await reloadStats();
      } else if (section.kind === "fleet_grid" && section.data_path) {
        let p = section.data_path;
        if (!p.startsWith("/")) {
          p = "/" + p;
        }
        summary.hidden = false;
        summary.innerHTML = "";
        if (window.AdminPanels && AdminPanels.mountFleetGrid) {
          AdminPanels.mountFleetGrid(summary, pre, {
            summary: summary,
            pre: pre,
            apiFetch: apiFetch,
          });
        } else {
          const data = await apiFetch(p);
          pre.textContent = JSON.stringify(data, null, 2);
        }
      } else if (
        window.AdminPanels &&
        AdminPanels.tryMount(section, {
          summary: summary,
          pre: pre,
          apiFetch: apiFetch,
          getOrgId: window.AdminUi ? () => AdminUi.getOrgId() : () => "",
          renderFlatObjectTable: renderFlatObjectTable,
        })
      ) {
        /* panels.js */
      } else if (
        section.kind === "json_panel" &&
        (section.data_path ||
          section.id === "core-retention" ||
          section.id === "core-user-organization" ||
          section.id === "core-auth-policy" ||
          section.id === "core-ui-branding")
      ) {
        summary.hidden = false;
        summary.innerHTML = "";
        const cap = document.createElement("p");
        cap.className = "muted small";
        summary.appendChild(cap);

        async function paintTableAndJson(data, fetchPathUsed) {
          cap.textContent = "Источник: GET " + API + fetchPathUsed;
          summary.querySelector(".json-table-wrap")?.remove();
          summary.querySelectorAll(".json-panel-note").forEach((n) => n.remove());
          summary.querySelector(".stats-grid")?.remove();
          if (section.id === "core-read-receipts" && data && typeof data.total_rows === "number") {
            renderAdminKpiCards(summary, [
              { label: "Строк read receipts", value: String(data.total_rows) },
            ]);
          } else if (section.id === "core-e2ee-mls" && data && typeof data === "object") {
            renderAdminKpiCards(summary, [
              { label: "MLS groups", value: String(data.mls_group_count ?? "—") },
              { label: "Pending migrations", value: String(data.pending_migrations_count ?? "—") },
              { label: "MLS status", value: String(data.mls_status ?? "—") },
              {
                label: "OpenMLS native",
                value: data.openmls_native_binding ? "да" : "нет",
              },
            ]);
          } else if (section.id === "core-purge-status" && data && typeof data === "object") {
            renderAdminKpiCards(summary, [
              { label: "Очищено строк", value: String(data.total_purged ?? "—") },
              { label: "Ошибок", value: String(data.errors_count ?? "—") },
              { label: "В очереди", value: String(data.pending_count ?? "—") },
              {
                label: "Последний проход",
                value: data.last_pass_at ? String(data.last_pass_at) : "—",
              },
            ]);
          } else if (section.id === "core-admin-session" && data && typeof data === "object") {
            renderAdminKpiCards(summary, [
              { label: "Пользователь", value: String(data.username ?? data.sub ?? "—") },
              {
                label: "Realm roles",
                value: Array.isArray(data.realm_roles)
                  ? data.realm_roles.join(", ") || "—"
                  : "—",
              },
            ]);
          } else if (section.id === "core-product-modules" && data && typeof data === "object") {
            const addons = Array.isArray(data.addons) ? data.addons : [];
            const installed = addons.filter((a) => a && a.installed).length;
            renderAdminKpiCards(summary, [
              { label: "Base", value: String(data.base?.state ?? data.base?.label ?? "—") },
              { label: "Addons", value: String(addons.length) },
              { label: "Installed", value: String(installed) },
            ]);
          } else if (section.id === "core-admin-manifest" && data && typeof data === "object") {
            const sections = Array.isArray(data.sections) ? data.sections : [];
            renderAdminKpiCards(summary, [
              { label: "Разделов", value: String(sections.length) },
              { label: "API version", value: String(data.api_version ?? "—") },
            ]);
          }
          if (Array.isArray(data)) {
            renderArrayTable(data, summary);
          } else if (data && typeof data === "object") {
            renderFlatObjectTable(data, summary);
          }
          pre.textContent = JSON.stringify(data, null, 2);
        }

        if (section.id === "core-export-compliance") {
          function appendExportSuggestToolbar(container, preEl) {
            if (document.getElementById("exportSuggestChatId")) {
              return;
            }
            const suggestRow = document.createElement("div");
            suggestRow.className = "admin-toolbar export-suggest-toolbar";
          const lChat = document.createElement("label");
          lChat.className = "small";
          lChat.textContent = LT("ID чата");
          const inChat = document.createElement("input");
          inChat.type = "text";
          inChat.id = "exportSuggestChatId";
          inChat.placeholder = LT("UUID чата");
          lChat.appendChild(inChat);
          const lDispatch = document.createElement("label");
          lDispatch.className = "small";
          lDispatch.textContent = LT("Dispatch");
          const selDispatch = document.createElement("select");
          selDispatch.id = "exportSuggestDispatch";
          ["local", "nats", "both"].forEach((v) => {
            const o = document.createElement("option");
            o.value = v;
            o.textContent = v;
            selDispatch.appendChild(o);
          });
          lDispatch.appendChild(selDispatch);
          const btnNewChat = document.createElement("button");
          btnNewChat.type = "button";
          btnNewChat.textContent = LT("Новая группа");
          const btnDevSeed = document.createElement("button");
          btnDevSeed.type = "button";
          btnDevSeed.title = LT("3 сообщения + PATCH retention (smoke)");
          btnDevSeed.textContent = LT("seed+prepare");
          const btnSeedFile = document.createElement("button");
          btnSeedFile.type = "button";
          btnSeedFile.title = LT("export-compliance-prep с include_file");
          btnSeedFile.textContent = LT("seed+file");
          const btnPrepareRet = document.createElement("button");
          btnPrepareRet.type = "button";
          btnPrepareRet.textContent = LT("retention");
          const btnSuggest = document.createElement("button");
          btnSuggest.type = "button";
          btnSuggest.textContent = LT("export-suggest");
          const btnEnqueue = document.createElement("button");
          btnEnqueue.type = "button";
          btnEnqueue.textContent = LT("export");
          const btnComplianceFlow = document.createElement("button");
          btnComplianceFlow.type = "button";
          btnComplianceFlow.title = LT(
            "prep → suggest → export → poll → download → attachments inspect"
          );
          btnComplianceFlow.textContent = LT("compliance flow");
          const btnComplianceFlowFile = document.createElement("button");
          btnComplianceFlowFile.type = "button";
          btnComplianceFlowFile.title = LT(
            "prep (include_file) → suggest → export → poll → download → inspect"
          );
          btnComplianceFlowFile.textContent = LT("flow+file");
          const btnPollExport = document.createElement("button");
          btnPollExport.type = "button";
          btnPollExport.title = LT("poll status по chat_id + job_id (до 120s)");
          btnPollExport.textContent = LT("poll");
          const suggestMsg = document.createElement("span");
          suggestMsg.className = "muted small";

          const exportTerminalStatuses = new Set([
            "export_v1",
            "stub_written",
            "export_failed",
            "export_cancelled"
          ]);

          async function pollExportJobStatus(chatId, jobId, onTick) {
            const maxMs = 120000;
            const intervalMs = 2000;
            const deadline = Date.now() + maxMs;
            let last = null;
            while (Date.now() < deadline) {
              last = await apiFetch(
                "/admin/chats/" +
                  encodeURIComponent(chatId) +
                  "/export/" +
                  encodeURIComponent(jobId) +
                  "/status"
              );
              const st = last.status || "";
              if (onTick) {
                onTick(st);
              }
              if (exportTerminalStatuses.has(st)) {
                return last;
              }
              await new Promise((resolve) => setTimeout(resolve, intervalMs));
            }
            const err = new Error("poll timeout (" + (last?.status || "?") + ")");
            err.lastStatus = last;
            throw err;
          }

          async function ensureChatId() {
            let cid = inChat.value.trim();
            if (cid) {
              return cid;
            }
            const chat = await apiFetch("/chats", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                type: "group",
                title: "export-compliance-smoke",
                member_ids: []
              })
            });
            cid = chat.id || chat.chat_id || "";
            if (!cid) {
              throw new Error("create chat: no id");
            }
            inChat.value = cid;
            return cid;
          }

          btnNewChat.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            try {
              const cid = await ensureChatId();
              suggestMsg.textContent = adminFmt("ID чата={id}", { id: cid });
              preEl.textContent = JSON.stringify({ chat_id: cid }, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnPrepareRet.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            const cid = inChat.value.trim();
            if (!cid) {
              suggestMsg.textContent = "Укажите chat_id.";
              return;
            }
            try {
              const pol = await apiFetch(
                "/admin/chats/" + encodeURIComponent(cid) + "/retention",
                {
                  method: "PATCH",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    hot_message_body_max_age_days: 0,
                    hot_metadata_min_age_days: null,
                    archive_metadata_enabled: false,
                    deep_archive_enabled: true,
                    legal_hold: false
                  })
                }
              );
              suggestMsg.textContent = LT("retention OK");
              preEl.textContent = JSON.stringify(pol, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnDevSeed.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            try {
              const cid = inChat.value.trim();
              const prep = await apiFetch("/admin/export-compliance-prep", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  chat_id: cid || null,
                  create_group: !cid,
                  message_count: 3
                })
              });
              const outCid = prep.chat_id || prep.chatId || "";
              if (outCid) {
                inChat.value = outCid;
              }
              suggestMsg.textContent = adminFmt("seed OK, chat={id}", { id: outCid });
              preEl.textContent = JSON.stringify(prep, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnSeedFile.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            try {
              const cidIn = inChat.value.trim();
              const prep = await apiFetch("/admin/export-compliance-prep", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  chat_id: cidIn || null,
                  create_group: !cidIn,
                  message_count: 2,
                  include_file: true,
                  file_name: "compliance-smoke.txt"
                })
              });
              const cid = prep.chat_id || prep.chatId || "";
              if (cid) {
                inChat.value = cid;
              }
              const fileId = prep.file_id || prep.fileId || "";
              suggestMsg.textContent = fileId
                ? adminFmt("seed+file OK, file_id={id}", { id: fileId })
                : adminFmt("seed+file OK, chat={id}", { id: cid });
              preEl.textContent = JSON.stringify(prep, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnEnqueue.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            const cid = inChat.value.trim();
            if (!cid) {
              suggestMsg.textContent = "Укажите chat_id.";
              return;
            }
            try {
              const res = await apiFetch("/admin/chats/" + encodeURIComponent(cid) + "/export", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: "{}"
              });
              suggestMsg.textContent = adminFmt("job_id={id}", {
                id: res.job_id || res.jobId,
              });
              const jobInp = document.getElementById("exportInspectJobId");
              if (jobInp && (res.job_id || res.jobId)) {
                jobInp.value = res.job_id || res.jobId;
              }
              preEl.textContent = JSON.stringify(res, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });
          btnSuggest.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            const cid = inChat.value.trim();
            if (!cid) {
              suggestMsg.textContent = "Укажите chat_id.";
              return;
            }
            try {
              const res = await apiFetch(
                "/admin/chats/" + encodeURIComponent(cid) + "/export-suggest",
                {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    dispatch: selDispatch.value,
                    candidate_message_count: 1,
                    reason: "hot_body_candidates"
                  })
                }
              );
              suggestMsg.textContent = res.auto_queued_job_id
                ? adminFmt("OK, auto_queued_job_id={id}", { id: res.auto_queued_job_id })
                : adminFmt("OK ({dispatch})", { dispatch: res.dispatch });
              const jobInp = document.getElementById("exportInspectJobId");
              if (jobInp && res.auto_queued_job_id) {
                jobInp.value = res.auto_queued_job_id;
              }
              preEl.textContent = JSON.stringify(res, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          async function runComplianceFlow(includeFile) {
            suggestMsg.textContent = includeFile ? LT("flow+file…") : LT("flow…");
            const cidIn = inChat.value.trim();
            const prepBody = {
              chat_id: cidIn || null,
              create_group: !cidIn,
              message_count: includeFile ? 2 : 3
            };
            if (includeFile) {
              prepBody.include_file = true;
              prepBody.file_name = "compliance-smoke.txt";
            }
            const prep = await apiFetch("/admin/export-compliance-prep", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(prepBody)
            });
              const cid = prep.chat_id || prep.chatId || "";
              if (cid) {
                inChat.value = cid;
              }
              const suggest = await apiFetch(
                "/admin/chats/" + encodeURIComponent(cid) + "/export-suggest",
                {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    dispatch: selDispatch.value,
                    candidate_message_count: 3,
                    reason: "hot_body_candidates"
                  })
                }
              );
              let jobId =
                suggest.auto_queued_job_id || suggest.autoQueuedJobId || "";
              let exportRes = null;
              if (!jobId) {
                exportRes = await apiFetch(
                  "/admin/chats/" + encodeURIComponent(cid) + "/export",
                  {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: "{}"
                  }
                );
                jobId = exportRes.job_id || exportRes.jobId || "";
              }
              const jobInp = document.getElementById("exportInspectJobId");
              if (jobInp && jobId) {
                jobInp.value = jobId;
              }
              if (!jobId) {
                suggestMsg.textContent = LT("flow OK (no job_id)");
                preEl.textContent = JSON.stringify(
                  { prep: prep, suggest: suggest, export: exportRes },
                  null,
                  2
                );
                return;
              }
              let finalStatus = null;
              try {
                finalStatus = await pollExportJobStatus(cid, jobId, (st) => {
                  suggestMsg.textContent = adminFmt("poll {status} …", { status: st });
                });
              } catch (pollErr) {
                suggestMsg.textContent = pollErr.message || String(pollErr);
                preEl.textContent = JSON.stringify(
                  {
                    prep: prep,
                    suggest: suggest,
                    export: exportRes,
                    job_id: jobId,
                    poll_error: pollErr.message,
                    last_status: pollErr.lastStatus || null
                  },
                  null,
                  2
                );
                return;
              }
              const st = finalStatus?.status || "";
              const prepFid = prep.file_id || prep.fileId || "";
              suggestMsg.textContent =
                includeFile && prepFid
                  ? adminFmt("flow OK, status={status}, file_id={id}", {
                      status: st,
                      id: prepFid,
                    })
                  : adminFmt("flow OK, status={status}", { status: st });
              preEl.textContent = JSON.stringify(
                {
                  prep: prep,
                  suggest: suggest,
                  export: exportRes,
                  job_id: jobId,
                  final_status: finalStatus
                },
                null,
                2
              );
              if (st === "export_v1" || st === "stub_written") {
                let att = null;
                let manifest = null;
                try {
                  att = await apiFetch(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jobId) +
                      "/attachments?limit=20"
                  );
                } catch (attErr) {
                  suggestMsg.textContent = adminFmt(
                    "flow OK, status={status}; attachments: {err}",
                    { status: st, err: attErr.message || String(attErr) }
                  );
                }
                try {
                  manifest = await apiFetch(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jobId) +
                      "/download?part=manifest"
                  );
                } catch (_) {}
                try {
                  await apiDownload(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jobId) +
                      "/download?part=bundle",
                    "export-" + jobId + ".zip"
                  );
                  const fc = att?.file_count ?? att?.fileCount ?? "?";
                  const mf = Array.isArray(manifest?.files) ? manifest.files.length : "?";
                  suggestMsg.textContent = adminFmt(
                    "flow OK, status={status}, bundle скачан, files={fc}, manifest={mf}",
                    { status: st, fc: fc, mf: mf }
                  );
                  if (att || manifest) {
                    preEl.textContent = JSON.stringify(
                      {
                        prep: prep,
                        suggest: suggest,
                        export: exportRes,
                        job_id: jobId,
                        final_status: finalStatus,
                        attachments: att,
                        manifest: manifest
                      },
                      null,
                      2
                    );
                  }
                } catch (dlErr) {
                  suggestMsg.textContent = adminFmt("flow OK, status={status}; download: {err}", {
                    status: st,
                    err: dlErr.message || String(dlErr),
                  });
                }
              }
          }

          btnComplianceFlow.addEventListener("click", async () => {
            try {
              await runComplianceFlow(false);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnComplianceFlowFile.addEventListener("click", async () => {
            try {
              await runComplianceFlow(true);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });

          btnPollExport.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            const cid = inChat.value.trim();
            const jid = document.getElementById("exportInspectJobId")?.value.trim() || "";
            if (!cid || !jid) {
              suggestMsg.textContent = "Нужны chat_id и job_id.";
              return;
            }
            try {
              const finalStatus = await pollExportJobStatus(cid, jid, (st) => {
                suggestMsg.textContent = adminFmt("poll {status} …", { status: st });
              });
              suggestMsg.textContent = adminFmt("status={status}", {
                status: finalStatus?.status || "?",
              });
              preEl.textContent = JSON.stringify(finalStatus, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
              if (e.lastStatus) {
                preEl.textContent = JSON.stringify(e.lastStatus, null, 2);
              }
            }
          });

          const btnCancel = document.createElement("button");
          btnCancel.type = "button";
          btnCancel.textContent = LT("cancel");
          btnCancel.addEventListener("click", async () => {
            suggestMsg.textContent = "";
            const cid = inChat.value.trim();
            const jid = document.getElementById("exportInspectJobId")?.value.trim() || "";
            if (!cid || !jid) {
              suggestMsg.textContent = "Нужны chat_id и job_id (queued или processing).";
              return;
            }
            if (
              !window.confirm(
                "Отменить export " + jid + "?\nДопустимо в статусе queued или processing."
              )
            ) {
              return;
            }
            try {
              const res = await apiFetch(
                "/admin/chats/" + encodeURIComponent(cid) + "/export/" + encodeURIComponent(jid),
                { method: "DELETE" }
              );
              suggestMsg.textContent = adminFmt("cancelled={status}", {
                status: res.cancelled || res.status,
              });
              preEl.textContent = JSON.stringify(res, null, 2);
            } catch (e) {
              suggestMsg.textContent = e.message || String(e);
            }
          });
          suggestRow.appendChild(lChat);
          suggestRow.appendChild(lDispatch);
          suggestRow.appendChild(btnNewChat);
          suggestRow.appendChild(btnDevSeed);
          suggestRow.appendChild(btnSeedFile);
          suggestRow.appendChild(btnPrepareRet);
          suggestRow.appendChild(btnSuggest);
          suggestRow.appendChild(btnEnqueue);
          suggestRow.appendChild(btnComplianceFlow);
          suggestRow.appendChild(btnComplianceFlowFile);
          suggestRow.appendChild(btnPollExport);
          suggestRow.appendChild(btnCancel);
          suggestRow.appendChild(suggestMsg);
            container.insertBefore(suggestRow, container.firstChild);
          }

          function appendExportJobInspectorToolbar(container, preEl, summaryEl) {
            if (document.getElementById("exportInspectJobId")) {
              return;
            }
            const row = document.createElement("div");
            row.className = "admin-toolbar export-inspect-toolbar";
            const lJob = document.createElement("label");
            lJob.className = "small";
            lJob.textContent = LT("job_id");
            const inJob = document.createElement("input");
            inJob.type = "text";
            inJob.id = "exportInspectJobId";
            inJob.placeholder = "UUID задачи export";
            lJob.appendChild(inJob);
            const inspectMsg = document.createElement("span");
            inspectMsg.className = "muted small";
            const chatIdVal = () => {
              const el = document.getElementById("exportSuggestChatId");
              return el ? el.value.trim() : "";
            };
            const jobIdVal = () => inJob.value.trim();
            const mkBtn = (label, handler) => {
              const b = document.createElement("button");
              b.type = "button";
              b.textContent = label;
              b.addEventListener("click", handler);
              return b;
            };
            row.appendChild(lJob);
            const lJobsFilter = document.createElement("label");
            lJobsFilter.className = "small";
            lJobsFilter.textContent = LT("jobs status");
            const selJobsStatus = document.createElement("select");
            selJobsStatus.id = "exportJobsStatusFilter";
            [
              { v: "", t: "(все)" },
              { v: "queued", t: "queued" },
              { v: "processing", t: "processing" },
              { v: "export_v1", t: "export_v1" },
              { v: "export_cancelled", t: "export_cancelled" },
              { v: "export_failed", t: "export_failed" },
              { v: "stub_written", t: "stub_written" }
            ].forEach((o) => {
              const opt = document.createElement("option");
              opt.value = o.v;
              opt.textContent = o.t;
              selJobsStatus.appendChild(opt);
            });
            lJobsFilter.appendChild(selJobsStatus);
            row.appendChild(lJobsFilter);
            row.appendChild(
              mkBtn("status", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                const jid = jobIdVal();
                if (!cid || !jid) {
                  inspectMsg.textContent = "Нужны chat_id и job_id.";
                  return;
                }
                try {
                  const st = await apiFetch(
                    "/admin/chats/" + encodeURIComponent(cid) + "/export/" + encodeURIComponent(jid) + "/status"
                  );
                  inspectMsg.textContent = adminFmt("status={status}", { status: st.status });
                  preEl.textContent = JSON.stringify(st, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("attachments", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                const jid = jobIdVal();
                if (!cid || !jid) {
                  inspectMsg.textContent = "Нужны chat_id и job_id.";
                  return;
                }
                try {
                  const att = await apiFetch(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jid) +
                      "/attachments?limit=100"
                  );
                  inspectMsg.textContent = adminFmt("files {count}/{total}", {
                    count: String(att.file_count),
                    total: String(att.total_count),
                  });
                  summaryEl.querySelectorAll(".export-att-table").forEach((n) => n.remove());
                  const box = document.createElement("div");
                  box.className = "export-att-table";
                  renderExportAttachmentsTable(att, box);
                  if (box.childNodes.length > 0) {
                    summaryEl.insertBefore(box, preEl);
                  }
                  preEl.textContent = JSON.stringify(att, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("latest", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                if (!cid) {
                  inspectMsg.textContent = "Нужен chat_id.";
                  return;
                }
                try {
                  const st = await apiFetch(
                    "/admin/chats/" + encodeURIComponent(cid) + "/export/latest/status"
                  );
                  if (st.job_id) {
                    inJob.value = st.job_id;
                  }
                  inspectMsg.textContent = adminFmt("status={status}", { status: st.status });
                  preEl.textContent = JSON.stringify(st, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("jobs", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                if (!cid) {
                  inspectMsg.textContent = "Нужен chat_id.";
                  return;
                }
                try {
                  let jobsUrl =
                    "/admin/chats/" + encodeURIComponent(cid) + "/export/jobs?limit=30";
                  const statusFilter = document.getElementById("exportJobsStatusFilter")?.value || "";
                  if (statusFilter) {
                    jobsUrl += "&status=" + encodeURIComponent(statusFilter);
                  }
                  const list = await apiFetch(jobsUrl);
                  inspectMsg.textContent = adminFmt("jobs={count}{filter}", {
                    count: String(list.job_count),
                    filter: list.status_filter ? " (" + list.status_filter + ")" : "",
                  });
                  summaryEl.querySelectorAll(".export-jobs-table").forEach((n) => n.remove());
                  const box = document.createElement("div");
                  box.className = "export-jobs-table";
                  renderExportJobsTable(list, box);
                  if (box.childNodes.length > 0) {
                    summaryEl.insertBefore(box, preEl);
                  }
                  if (list.jobs && list.jobs[0] && list.jobs[0].job_id && !inJob.value.trim()) {
                    inJob.value = list.jobs[0].job_id;
                  }
                  preEl.textContent = JSON.stringify(list, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("all jobs", async () => {
                inspectMsg.textContent = "";
                try {
                  let jobsUrl = "/admin/export/jobs?limit=50";
                  const statusFilter = document.getElementById("exportJobsStatusFilter")?.value || "";
                  if (statusFilter) {
                    jobsUrl += "&status=" + encodeURIComponent(statusFilter);
                  }
                  const cid = chatIdVal();
                  if (cid) {
                    jobsUrl += "&chat_id=" + encodeURIComponent(cid);
                  }
                  const list = await apiFetch(jobsUrl);
                  inspectMsg.textContent = adminFmt("all jobs={count}", {
                    count: String(list.job_count),
                  });
                  summaryEl.querySelectorAll(".export-jobs-table").forEach((n) => n.remove());
                  const box = document.createElement("div");
                  box.className = "export-jobs-table";
                  renderExportJobsTable(list, box);
                  if (box.childNodes.length > 0) {
                    summaryEl.insertBefore(box, preEl);
                  }
                  if (list.jobs && list.jobs[0]) {
                    if (list.jobs[0].job_id && !inJob.value.trim()) {
                      inJob.value = list.jobs[0].job_id;
                    }
                    const chatInp = document.getElementById("exportSuggestChatId");
                    if (chatInp && list.jobs[0].chat_id && !chatInp.value.trim()) {
                      chatInp.value = list.jobs[0].chat_id;
                    }
                  }
                  preEl.textContent = JSON.stringify(list, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("download", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                const jid = jobIdVal();
                if (!cid || !jid) {
                  inspectMsg.textContent = "Нужны chat_id и job_id.";
                  return;
                }
                try {
                  await apiDownload(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jid) +
                      "/download?part=bundle",
                    "export-" + jid + ".zip"
                  );
                  inspectMsg.textContent = LT("скачано (bundle)");
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(
              mkBtn("cancel", async () => {
                inspectMsg.textContent = "";
                const cid = chatIdVal();
                const jid = jobIdVal();
                if (!cid || !jid) {
                  inspectMsg.textContent = "Нужны chat_id и job_id.";
                  return;
                }
                if (
                  !window.confirm(
                    "Отменить export " + jid + "?\nДопустимо в статусе queued или processing."
                  )
                ) {
                  return;
                }
                try {
                  const res = await apiFetch(
                    "/admin/chats/" +
                      encodeURIComponent(cid) +
                      "/export/" +
                      encodeURIComponent(jid),
                    { method: "DELETE" }
                  );
                  inspectMsg.textContent = adminFmt("cancelled={status}", {
                    status: res.cancelled || res.status,
                  });
                  preEl.textContent = JSON.stringify(res, null, 2);
                } catch (e) {
                  inspectMsg.textContent = e.message || String(e);
                }
              })
            );
            row.appendChild(inspectMsg);
            container.insertBefore(row, container.firstChild);
          }

          const reloadGuide = async () => {
            try {
              const guide = await apiFetch("/admin/ui/export-compliance-guide");
              renderExportComplianceGuide(guide, summary, pre);
              appendExportSuggestToolbar(summary, pre);
              appendExportJobInspectorToolbar(summary, pre, summary);
              cap.textContent =
                "Источник: GET " +
                API +
                "/admin/ui/export-compliance-guide · admin export status/attachments · export-suggest";
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendJsonPanelReload(summary, reloadGuide);
          await reloadGuide();
        } else if (section.id === "core-audit-events") {
          cap.textContent = LT(
            "GET /api/v1/admin/audit-events — фильтры и пресеты export ниже."
          );
          const reloadAudit = async () => {
            try {
              const fp = auditEventsFetchPath();
              const data = await apiFetch(fp);
              await paintTableAndJson(data, fp);
              summary.querySelector(".json-table-wrap")?.setAttribute("data-testid", "admin-audit-table");
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendAuditToolbar(summary, reloadAudit);
          appendJsonPanelReload(summary, reloadAudit);
          await reloadAudit();
        } else if (section.id === "core-organizations") {
          cap.textContent = LT(
            "GET /api/v1/admin/organizations — список и CRUD ниже."
          );
          const reloadOrgs = async () => {
            try {
              const d = await apiFetch("/admin/organizations");
              await paintTableAndJson(d, "/admin/organizations");
              summary.querySelector(".json-table-wrap")?.setAttribute("data-testid", "admin-orgs-table");
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendOrgCreateToolbar(summary, reloadOrgs);
          appendOrgDeleteToolbar(summary, reloadOrgs);
          appendOrgLogoToolbar(summary, reloadOrgs);
          appendJsonPanelReload(summary, reloadOrgs);
          await reloadOrgs();
        } else if (section.id === "core-ui-branding") {
          cap.textContent = LT(
            "GET/PUT /api/v1/admin/branding/platform и /api/v1/admin/branding/orgs/{orgId}."
          );
          renderBrandingPanel(summary, pre, cap);
        } else if (section.id === "core-user-organization") {
          cap.textContent =
            "PATCH " +
            API +
            "/admin/users/{user_id}/organization — тело {\"org_id\":\"…\"}. Успех: 204 No Content; аудит user.organization.set.";
          const row = document.createElement("div");
          row.className = "admin-toolbar";
          const lUser = document.createElement("label");
          lUser.className = "small";
          lUser.textContent = LT("user_id");
          const inUser = document.createElement("input");
          inUser.type = "text";
          inUser.id = "setUserOrgUserId";
          inUser.placeholder = "UUID пользователя";
          lUser.appendChild(inUser);
          const lOrg = document.createElement("label");
          lOrg.className = "small";
          lOrg.textContent = LT("org_id");
          const inOrg = document.createElement("input");
          inOrg.type = "text";
          inOrg.id = "setUserOrgOrgId";
          inOrg.placeholder = "UUID организации";
          lOrg.appendChild(inOrg);
          const btn = document.createElement("button");
          btn.type = "button";
          btn.textContent = "Назначить";
          const msg = document.createElement("span");
          msg.id = "setUserOrgMsg";
          msg.className = "muted small";
          btn.addEventListener("click", async () => {
            msg.textContent = "";
            const uid = inUser.value.trim();
            const oid = inOrg.value.trim();
            if (!uid || !oid) {
              msg.textContent = "Нужны оба UUID.";
              return;
            }
            try {
              const path = "/admin/users/" + encodeURIComponent(uid) + "/organization";
              const data = await apiFetch(path, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ org_id: oid }),
              });
              pre.textContent =
                data == null ? "204 No Content — организация назначена." : JSON.stringify(data, null, 2);
              msg.textContent = "Готово.";
            } catch (e) {
              pre.textContent = "Ошибка: " + (e.message || String(e));
              msg.textContent = e.message || String(e);
            }
          });
          row.appendChild(lUser);
          row.appendChild(lOrg);
          row.appendChild(btn);
          row.appendChild(msg);
          summary.appendChild(row);
          pre.textContent = LT(
            "UUID пользователя и организации (список — раздел «Организации»)."
          );
        } else if (section.id === "core-auth-policy") {
          authPolicyTargetOrgId = null;
          cap.textContent =
            "GET " + API + "/admin/orgs/{orgId}/auth-policy — org из панели «Организация» подхватывается автоматически.";
          const subcap = document.createElement("p");
          subcap.className = "muted small";
          subcap.id = "authPolicyLastFetch";
          subcap.textContent = "";
          summary.appendChild(subcap);
          const clearAuthPolicySummary = () => {
            summary.querySelector(".json-table-wrap")?.remove();
            summary.querySelectorAll(".json-panel-note").forEach((n) => n.remove());
          };
          const showAuthPolicy = (data, path, orgId) => {
            clearAuthPolicySummary();
            const sc = document.getElementById("authPolicyLastFetch");
            if (sc) {
              sc.textContent = "Последний запрос: GET " + API + path;
            }
            pre.textContent = JSON.stringify(data, null, 2);
            renderFlatObjectTable(data, summary);
            authPolicyTargetOrgId = orgId;
            fillAuthPolicyForm(data);
            const pm = document.getElementById("authPolicyPatchMsg");
            if (pm) {
              pm.textContent = "";
            }
            const tm = document.getElementById("authPolicyTestMsg");
            if (tm) {
              tm.textContent = "";
            }
          };
          const row = document.createElement("div");
          row.className = "admin-toolbar";
          const lbl = document.createElement("label");
          lbl.className = "small";
          lbl.textContent = "Организация";
          const inp = document.createElement("input");
          inp.type = "text";
          inp.id = "authPolicyOrgId";
          inp.placeholder = "UUID";
          lbl.appendChild(inp);
          const btn = document.createElement("button");
          btn.type = "button";
          btn.textContent = "Загрузить";
          const msg = document.createElement("span");
          msg.className = "muted small";
          btn.addEventListener("click", async () => {
            msg.textContent = "";
            const uuid = inp.value.trim();
            if (!uuid) {
              msg.textContent = "Введите UUID";
              return;
            }
            try {
              const path = "/admin/orgs/" + encodeURIComponent(uuid) + "/auth-policy";
              const data = await apiFetch(path);
              showAuthPolicy(data, path, uuid);
            } catch (e) {
              msg.textContent = e.message || String(e);
            }
          });
          row.appendChild(lbl);
          row.appendChild(btn);
          row.appendChild(msg);
          summary.appendChild(row);
          appendAuthPolicyForm(summary);
          const orgPrefill =
            (window.AdminUi && AdminUi.getOrgId && AdminUi.getOrgId()) ||
            (document.getElementById("globalOrgId") && document.getElementById("globalOrgId").value.trim()) ||
            "";
          if (orgPrefill) {
            inp.value = orgPrefill;
            (async () => {
              try {
                const path = "/admin/orgs/" + encodeURIComponent(orgPrefill) + "/auth-policy";
                const data = await apiFetch(path);
                showAuthPolicy(data, path, orgPrefill);
              } catch (e) {
                msg.textContent = e.message || String(e);
              }
            })();
          }
          const reloadAuthPolicy = async () => {
            try {
              if (!authPolicyTargetOrgId) {
                pre.textContent = "Сначала нажмите «Загрузить» для организации.";
                return;
              }
              const path = "/admin/orgs/" + encodeURIComponent(authPolicyTargetOrgId) + "/auth-policy";
              const data = await apiFetch(path);
              showAuthPolicy(data, path, authPolicyTargetOrgId);
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendJsonPanelReload(summary, reloadAuthPolicy);
          pre.textContent = orgPrefill
            ? "Политика загружена для org из контекста."
            : "Введите UUID организации и нажмите «Загрузить».";
        } else if (section.id === "core-retention") {
          retentionPatchTarget = null;
          cap.textContent =
            "GET " +
            API +
            "/admin/organizations/{orgId}/retention и GET " +
            API +
            "/admin/chats/{chatId}/retention — один «Загрузить», org из контекста автоматически.";
          const subcap = document.createElement("p");
          subcap.className = "muted small";
          subcap.id = "retentionLastFetch";
          subcap.textContent = "";
          summary.appendChild(subcap);
          const clearRetentionSummary = () => {
            summary.querySelector(".json-table-wrap")?.remove();
            summary.querySelectorAll(".json-panel-note").forEach((n) => n.remove());
          };
          const showRetention = (data, path, kind, entityId) => {
            clearRetentionSummary();
            const sc = document.getElementById("retentionLastFetch");
            if (sc) {
              sc.textContent = "Последний запрос: GET " + API + path;
            }
            pre.textContent = JSON.stringify(data, null, 2);
            renderFlatObjectTable(data, summary);
            retentionPatchTarget = { kind: kind, id: entityId };
            fillRetentionPatchForm(data);
            const pm = document.getElementById("retentionPatchMsg");
            if (pm) {
              pm.textContent = "";
            }
          };
          const kindSel = document.createElement("select");
          kindSel.id = "retentionKind";
          [
            { v: "org", t: "Организация" },
            { v: "chat", t: "Чат" },
          ].forEach((o) => {
            const opt = document.createElement("option");
            opt.value = o.v;
            opt.textContent = o.t;
            kindSel.appendChild(opt);
          });
          const loadRow = document.createElement("div");
          loadRow.className = "admin-toolbar";
          const kindLbl = document.createElement("label");
          kindLbl.className = "small";
          kindLbl.textContent = "Объект";
          kindLbl.appendChild(kindSel);
          loadRow.appendChild(kindLbl);
          const idLbl = document.createElement("label");
          idLbl.className = "small";
          idLbl.textContent = LT("UUID");
          const idInp = document.createElement("input");
          idInp.type = "text";
          idInp.id = "retentionTargetId";
          idInp.placeholder = "UUID";
          idLbl.appendChild(idInp);
          loadRow.appendChild(idLbl);
    const loadBtn = document.createElement("button");
    loadBtn.type = "button";
    loadBtn.textContent = "Загрузить";
    loadBtn.setAttribute("data-testid", "admin-retention-load");
          const loadMsg = document.createElement("span");
          loadMsg.className = "muted small";
          async function loadRetention(kind, uuid, silent) {
            loadMsg.textContent = "";
            if (!uuid) {
              if (!silent) {
                loadMsg.textContent = "Введите UUID";
              }
              return false;
            }
            const path =
              kind === "org"
                ? "/admin/organizations/" + encodeURIComponent(uuid) + "/retention"
                : "/admin/chats/" + encodeURIComponent(uuid) + "/retention";
            try {
              const data = await apiFetch(path);
              showRetention(data, path, kind, uuid);
              if (!silent) {
                loadMsg.textContent = "Загружено.";
              }
              return true;
            } catch (e) {
              loadMsg.textContent = e.message || String(e);
              return false;
            }
          }
          loadBtn.addEventListener("click", () => {
            loadRetention(kindSel.value, idInp.value.trim(), false);
          });
          loadRow.appendChild(loadBtn);
          loadRow.appendChild(loadMsg);
          summary.appendChild(loadRow);
          const retentionOrgPrefill =
            (window.AdminUi && AdminUi.getOrgId && AdminUi.getOrgId()) ||
            (document.getElementById("globalOrgId") && document.getElementById("globalOrgId").value.trim()) ||
            "";
          if (retentionOrgPrefill) {
            idInp.value = retentionOrgPrefill;
            kindSel.value = "org";
            loadRetention("org", retentionOrgPrefill, true);
          }
          appendRetentionPatchForm(summary);
          const reloadRetentionGet = async () => {
            try {
              const t = retentionPatchTarget;
              if (!t || !t.id) {
                pre.textContent = "Сначала нажмите «Загрузить» для организации или чата.";
                return;
              }
              const path =
                t.kind === "org"
                  ? "/admin/organizations/" + encodeURIComponent(t.id) + "/retention"
                  : "/admin/chats/" + encodeURIComponent(t.id) + "/retention";
              const data = await apiFetch(path);
              showRetention(data, path, t.kind, t.id);
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendJsonPanelReload(summary, reloadRetentionGet);
          pre.textContent = retentionOrgPrefill
            ? "Ретенция org загружена из контекста."
            : "Выберите org или chat и нажмите «Загрузить».";
        } else {
          let basePath = normalizePanelPath(section.data_path);
          const reloadGeneric = async () => {
            try {
              let fetchPath = basePath;
              if (window.AdminUi && section.id.startsWith("plugins-")) {
                fetchPath = AdminUi.appendPathWithOrg(basePath);
              }
              const data = await apiFetch(fetchPath);
              await paintTableAndJson(data, fetchPath);
              if (window.AdminPanels && section.id === "plugins-instances") {
                AdminPanels.enhancePluginInstances(summary, pre, {
                  summary: summary,
                  pre: pre,
                  apiFetch: apiFetch,
                });
                if (Array.isArray(data.instances) && data.instances[0] && data.instances[0].id) {
                  const inp = document.getElementById("pluginInstId");
                  if (inp && !inp.value.trim()) {
                    inp.value = data.instances[0].id;
                  }
                }
              }
              if (window.AdminPanels && section.id === "plugins-policies") {
                AdminPanels.enhancePluginPolicies(summary, pre, {
                  summary: summary,
                  pre: pre,
                  apiFetch: apiFetch,
                });
                if (data && data.llm_mode) {
                  const sel = document.getElementById("pluginPolicyLlm");
                  if (sel) {
                    sel.value = data.llm_mode;
                  }
                }
                if (data && typeof data.ocr_on_prem_only === "boolean") {
                  const cb = document.getElementById("pluginPolicyOcr");
                  if (cb) {
                    cb.checked = data.ocr_on_prem_only;
                  }
                }
                if (data && Array.isArray(data.allowed_preset_ids)) {
                  const pr = document.getElementById("pluginPolicyPresets");
                  if (pr) {
                    pr.value = data.allowed_preset_ids.join(", ");
                  }
                }
              }
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          if (window.AdminUi && section.id.startsWith("plugins-")) {
            AdminUi.appendPluginOrgToolbar(summary, reloadGeneric);
          }
          appendJsonPanelReload(summary, reloadGeneric);
          await reloadGeneric();
        }
      } else {
        pre.textContent = JSON.stringify(section, null, 2);
      }
    } catch (e) {
      pre.textContent = "Ошибка: " + e.message;
    }
    const hint = el("panelHint");
    if (hint) {
      hint.hidden = true;
    }
  }

  async function loadManifest() {
    const data = await apiFetch("/admin/ui/manifest");
    cachedManifestSections = data.sections || [];
    const ver = el("apiVersionLabel");
    if (ver && data.api_version) {
      ver.textContent = LT("API ") + data.api_version;
      ver.hidden = false;
    }
    renderSections(cachedManifestSections);
    updateLogoutButton();
  }

  function onAdminLocaleChanged() {
    refreshAuthStatus().catch(function () {});
    if (!token() || !cachedManifestSections || !window.AdminUi) {
      return;
    }
    const activeId = AdminUi.getActiveSectionId();
    renderSections(cachedManifestSections, activeId || undefined);
  }

  async function logout() {
    el("authStatus").textContent = "";
    const rt = sessionStorage.getItem(LS_REFRESH);
    if (rt) {
      try {
        const res = await fetch(API + "/auth/logout", {
          method: "POST",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ refresh_token: rt }),
        });
        if (!res.ok) {
          const text = await res.text();
          let msg = res.statusText;
          try {
            const j = JSON.parse(text);
            if (j.message) {
              msg = j.message;
            }
          } catch (_) {}
          el("authStatus").textContent = adminFmt("Keycloak: {msg} — локальная сессия сброшена.", {
            msg: msg,
          });
        } else {
          el("authStatus").textContent = "Выход выполнен (refresh отозван).";
        }
      } catch (e) {
        el("authStatus").textContent = "Сеть при logout: " + (e.message || e) + " — локально вышли.";
      }
    } else {
      el("authStatus").textContent = "Нет refresh_token — только локальный сброс.";
    }
    clearAuthStorage();
    updateLogoutButton();
    resetPanelAfterLogout();
  }

  el("btnLogin").addEventListener("click", () => login().catch((e) => {
    el("authStatus").textContent = e.message || String(e);
  }));

  el("btnLogout").addEventListener("click", () => logout().catch((e) => {
    el("authStatus").textContent = e.message || String(e);
  }));

  updateLogoutButton();

  document.addEventListener("admin-locale-changed", onAdminLocaleChanged);

  if (token()) {
    loadManifest().catch((e) => {
      clearAuthStorage();
      updateLogoutButton();
      el("authStatus").textContent =
        "Токен недействителен или нет роли admin — войдите снова. " + (e.message || "");
    });
  }
})();
