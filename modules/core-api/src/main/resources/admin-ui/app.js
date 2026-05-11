(function () {
  const API = "/api/v1";
  const LS_KEY = "admin_console_token";
  const LS_REFRESH = "admin_console_refresh_token";

  /** Последняя сущность для PATCH ретенции: org или chat + UUID. */
  let retentionPatchTarget = null;

  const el = (id) => document.getElementById(id);

  function clearAuthStorage() {
    sessionStorage.removeItem(LS_KEY);
    sessionStorage.removeItem(LS_REFRESH);
  }

  function updateLogoutButton() {
    const btn = el("btnLogout");
    if (btn) {
      btn.hidden = !token();
    }
  }

  function resetPanelAfterLogout() {
    const ul = el("sectionList");
    if (ul) {
      ul.innerHTML = "";
    }
    const hint = el("panelHint");
    if (hint) {
      hint.hidden = false;
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

  function renderCoreStatsSummary(stats, container) {
    container.innerHTML = "";
    container.hidden = false;
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
    const j = stats.jvm || {};
    const d = stats.dependencies || {};
    const c = stats.counts || {};
    addRow("Версия API", String(stats.api_version || "—"));
    addRow("Uptime JVM", formatDuration(j.uptime_ms));
    addRow("Heap (used / max)", formatBytes(j.heap_used_bytes) + " / " + formatBytes(j.heap_max_bytes));
    addRow("Процессоры", String(j.processors != null ? j.processors : "—"));
    addRow("PostgreSQL", d.database_ok ? "ok" : "недоступна");
    addRow("Redis", d.redis_ok ? "ok" : "недоступен");
    addRow("NATS", d.nats_ok ? "ok" : "нет соединения");
    if (c.counts_available) {
      addRow("Пользователи", String(c.users));
      addRow("Чаты", String(c.chats));
      addRow("Сообщения", String(c.messages));
    } else {
      addRow("Счётчики БД", "недоступны");
    }
    container.appendChild(table);
    const cap = document.createElement("p");
    cap.className = "muted small";
    cap.textContent = "Полный JSON ниже.";
    container.appendChild(cap);
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
    limLbl.textContent = "limit";
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
    btn.textContent = "Применить";
    btn.addEventListener("click", () => onApply());
    box.appendChild(btn);
    container.appendChild(box);
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
    lbl.textContent = "UUID";
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
      el("authStatus").textContent = body.message || "Ошибка входа";
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
    try {
      const sess = await apiFetch("/admin/session");
      el("authStatus").textContent =
        "Вошли как " + (sess.username || sess.user_id || "?") + " (sessionStorage).";
    } catch (_) {
      el("authStatus").textContent = "Токен сохранён; не удалось загрузить /admin/session.";
    }
    await loadManifest();
  }

  function renderSections(sections) {
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
    document.querySelectorAll(".sidebar li").forEach((n) => n.classList.remove("active"));
    if (liNode) {
      liNode.classList.add("active");
    }
    const pre = el("panelContent");
    const summary = el("panelSummary");
    summary.hidden = true;
    summary.innerHTML = "";
    pre.hidden = false;
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
      } else if (
        section.kind === "json_panel" &&
        (section.data_path || section.id === "core-retention" || section.id === "core-user-organization")
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
          if (Array.isArray(data)) {
            renderArrayTable(data, summary);
          } else if (data && typeof data === "object") {
            renderFlatObjectTable(data, summary);
          }
          pre.textContent = JSON.stringify(data, null, 2);
        }

        if (section.id === "core-audit-events") {
          const reloadAudit = async () => {
            try {
              const fp = auditEventsFetchPath();
              const data = await apiFetch(fp);
              await paintTableAndJson(data, fp);
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendAuditToolbar(summary, reloadAudit);
          appendJsonPanelReload(summary, reloadAudit);
          await reloadAudit();
        } else if (section.id === "core-organizations") {
          const reloadOrgs = async () => {
            try {
              const d = await apiFetch("/admin/organizations");
              await paintTableAndJson(d, "/admin/organizations");
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
          appendOrgCreateToolbar(summary, reloadOrgs);
          appendOrgDeleteToolbar(summary, reloadOrgs);
          appendJsonPanelReload(summary, reloadOrgs);
          await reloadOrgs();
        } else if (section.id === "core-user-organization") {
          cap.textContent =
            "PATCH " +
            API +
            "/admin/users/{user_id}/organization — тело {\"org_id\":\"…\"}. Успех: 204 No Content; аудит user.organization.set.";
          const row = document.createElement("div");
          row.className = "admin-toolbar";
          const lUser = document.createElement("label");
          lUser.className = "small";
          lUser.textContent = "user_id";
          const inUser = document.createElement("input");
          inUser.type = "text";
          inUser.id = "setUserOrgUserId";
          inUser.placeholder = "UUID пользователя";
          lUser.appendChild(inUser);
          const lOrg = document.createElement("label");
          lOrg.className = "small";
          lOrg.textContent = "org_id";
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
          pre.textContent = "UUID пользователя и организации (список — раздел «Организации»).";
        } else if (section.id === "core-retention") {
          retentionPatchTarget = null;
          cap.textContent =
            "GET " +
            API +
            "/admin/organizations/{orgId}/retention и GET " +
            API +
            "/admin/chats/{chatId}/retention — введите UUID и нажмите «Загрузить».";
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
          const mkRow = (inputId, labelText, buildPath, kind) => {
            const row = document.createElement("div");
            row.className = "admin-toolbar";
            const lbl = document.createElement("label");
            lbl.className = "small";
            lbl.textContent = labelText;
            const inp = document.createElement("input");
            inp.type = "text";
            inp.id = inputId;
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
                const path = buildPath(uuid);
                const data = await apiFetch(path);
                showRetention(data, path, kind, uuid);
              } catch (e) {
                msg.textContent = e.message || String(e);
              }
            });
            row.appendChild(lbl);
            row.appendChild(btn);
            row.appendChild(msg);
            summary.appendChild(row);
          };
          mkRow("retentionOrgId", "Организация", (uuid) => "/admin/organizations/" + encodeURIComponent(uuid) + "/retention", "org");
          mkRow("retentionChatId", "Чат", (uuid) => "/admin/chats/" + encodeURIComponent(uuid) + "/retention", "chat");
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
          pre.textContent = "Введите UUID организации или чата и нажмите «Загрузить».";
        } else {
          const basePath = normalizePanelPath(section.data_path);
          const reloadGeneric = async () => {
            try {
              const data = await apiFetch(basePath);
              await paintTableAndJson(data, basePath);
            } catch (e) {
              pre.textContent = "Ошибка: " + e.message;
            }
          };
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
    const ver = el("apiVersionLabel");
    if (ver && data.api_version) {
      ver.textContent = "API " + data.api_version;
      ver.hidden = false;
    }
    renderSections(data.sections || []);
    updateLogoutButton();
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
          el("authStatus").textContent = "Keycloak: " + msg + " — локальная сессия сброшена.";
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

  if (token()) {
    loadManifest().catch((e) => {
      clearAuthStorage();
      updateLogoutButton();
      el("authStatus").textContent =
        "Токен недействителен или нет роли admin — войдите снова. " + (e.message || "");
    });
  }
})();
