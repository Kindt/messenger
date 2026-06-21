/**
 * Специализированные панели админ-консоли: L0 wizard, legal hold, directory sync.
 */
(function (global) {
  function getOrgId(ctx) {
    if (ctx && typeof ctx.getOrgId === "function") {
      const v = ctx.getOrgId();
      if (v) {
        return v;
      }
    }
    const inp = document.getElementById("globalOrgId");
    return inp && inp.value.trim() ? inp.value.trim() : "";
  }

  function mkField(id, label, placeholder, type) {
    const wrap = document.createElement("label");
    wrap.className = "field";
    const cap = document.createElement("span");
    cap.className = "field-label";
    cap.textContent = label;
    const inp = document.createElement("input");
    inp.type = type || "text";
    inp.id = id;
    if (placeholder) {
      inp.placeholder = placeholder;
    }
    wrap.appendChild(cap);
    wrap.appendChild(inp);
    return wrap;
  }

  function mkCheckbox(id, label, checked) {
    const wrap = document.createElement("label");
    wrap.className = "field field-check";
    const inp = document.createElement("input");
    inp.type = "checkbox";
    inp.id = id;
    if (checked) {
      inp.checked = true;
    }
    wrap.appendChild(inp);
    wrap.appendChild(document.createTextNode(" " + label));
    return wrap;
  }

  function showResult(pre, data, err) {
    if (pre) {
      if (err) {
        pre.textContent = "Ошибка: " + err;
      } else {
        pre.textContent = JSON.stringify(data, null, 2);
      }
    }
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
  }

  function defaultL0Config() {
    return {
      config_schema_version: 2,
      welcome_text: "Добро пожаловать! Выберите пункт меню или команду /help",
      vars: { support_phone: "1234" },
      slash_commands: [
        { command: "/help", response_text: "Доступные команды: /help, /phone" },
        { command: "/phone", response_text: "Поддержка: {{config.support_phone}}" },
      ],
      menu: {
        root: ["faq"],
        buttons: [
          {
            id: "faq",
            label: "Справка",
            response_text: "Напишите /phone для связи с поддержкой.",
          },
        ],
      },
    };
  }

  function buildL0ConfigFromForm(root) {
    const welcome = root.querySelector("#l0Welcome").value.trim();
    const varsRaw = root.querySelector("#l0Vars").value.trim();
    const vars = {};
    if (varsRaw) {
      varsRaw.split("\n").forEach((line) => {
        const idx = line.indexOf("=");
        if (idx > 0) {
          vars[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
        }
      });
    }
    const slash = [];
    root.querySelectorAll(".l0-slash-row").forEach((row) => {
      const cmd = row.querySelector(".l0-slash-cmd").value.trim();
      const txt = row.querySelector(".l0-slash-text").value.trim();
      if (cmd && txt) {
        slash.push({ command: cmd.startsWith("/") ? cmd : "/" + cmd, response_text: txt });
      }
    });
    const buttons = [];
    const rootIds = [];
    root.querySelectorAll(".l0-btn-row").forEach((row) => {
      const id = row.querySelector(".l0-btn-id").value.trim();
      const label = row.querySelector(".l0-btn-label").value.trim();
      const txt = row.querySelector(".l0-btn-text").value.trim();
      if (id && label) {
        rootIds.push(id);
        const btn = { id: id, label: label, response_text: txt || label };
        const url = row.querySelector(".l0-btn-url").value.trim();
        if (url) {
          btn.url = url;
        }
        buttons.push(btn);
      }
    });
    let config;
    const adv = root.querySelector("#l0ConfigJson");
    if (adv && adv.dataset.advanced === "true") {
      try {
        config = JSON.parse(adv.value);
      } catch (e) {
        throw new Error("Некорректный JSON config: " + e.message);
      }
    } else {
      config = {
        config_schema_version: 2,
        welcome_text: welcome || "Привет!",
        vars: vars,
        slash_commands: slash.length ? slash : [{ command: "/help", response_text: "Справка" }],
        menu: { root: rootIds.length ? rootIds : ["help"], buttons: buttons.length ? buttons : [{ id: "help", label: "Помощь", response_text: "OK" }] },
      };
    }
    return config;
  }

  function mountL0Wizard(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Заполните форму и нажмите «Создать L0-бота».";

    const box = document.createElement("div");
    box.className = "panel-form l0-wizard";

    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "POST /api/v1/admin/plugins/instances/l0 — FAQ-меню без внешнего runtime. Org можно взять из панели «Организация» сверху.";
    box.appendChild(hint);

    const row1 = document.createElement("div");
    row1.className = "form-grid";
    row1.appendChild(mkField("l0OrgId", "org_id", "UUID организации"));
    row1.appendChild(mkField("l0BotName", "bot_name", "hr_faq (3–32, a-z0-9_)"));
    row1.appendChild(mkField("l0DisplayName", "display_name", "HR FAQ"));
    box.appendChild(row1);

    const orgPrefill = getOrgId(ctx);
    if (orgPrefill) {
      box.querySelector("#l0OrgId").value = orgPrefill;
    }

    box.appendChild(mkField("l0Welcome", "welcome_text", "Текст приветствия"));
    box.querySelector("#l0Welcome").value = defaultL0Config().welcome_text;

    const varsLbl = document.createElement("label");
    varsLbl.className = "field";
    varsLbl.innerHTML = '<span class="field-label">vars (key=value, по строке)</span>';
    const varsTa = document.createElement("textarea");
    varsTa.id = "l0Vars";
    varsTa.rows = 2;
    varsTa.placeholder = "support_phone=1234";
    varsTa.value = "support_phone=1234";
    varsLbl.appendChild(varsTa);
    box.appendChild(varsLbl);

    const slashCap = document.createElement("p");
    slashCap.className = "form-section-label";
    slashCap.textContent = "Slash-команды";
    box.appendChild(slashCap);
    const slashWrap = document.createElement("div");
    slashWrap.id = "l0SlashWrap";
    box.appendChild(slashWrap);

    function addSlashRow(cmd, text) {
      const row = document.createElement("div");
      row.className = "admin-toolbar l0-slash-row";
      const c = document.createElement("input");
      c.className = "l0-slash-cmd";
      c.placeholder = "/phone";
      c.value = cmd || "";
      const t = document.createElement("input");
      t.className = "l0-slash-text";
      t.placeholder = "Ответ";
      t.value = text || "";
      row.appendChild(c);
      row.appendChild(t);
      slashWrap.appendChild(row);
    }
    defaultL0Config().slash_commands.forEach((s) => addSlashRow(s.command, s.response_text));
    const addSlash = document.createElement("button");
    addSlash.type = "button";
    addSlash.className = "btn btn-secondary btn-sm";
    addSlash.textContent = "+ команда";
    addSlash.addEventListener("click", () => addSlashRow("", ""));
    box.appendChild(addSlash);

    const menuCap = document.createElement("p");
    menuCap.className = "form-section-label";
    menuCap.textContent = "Кнопки меню";
    box.appendChild(menuCap);
    const menuWrap = document.createElement("div");
    menuWrap.id = "l0MenuWrap";
    box.appendChild(menuWrap);

    function addMenuRow(id, label, text, url) {
      const row = document.createElement("div");
      row.className = "admin-toolbar l0-btn-row";
      ["l0-btn-id", "l0-btn-label", "l0-btn-text", "l0-btn-url"].forEach((cls, i) => {
        const inp = document.createElement("input");
        inp.className = cls;
        inp.placeholder = ["id", "Подпись", "Текст ответа", "URL (опц.)"][i];
        const vals = [id, label, text, url];
        if (vals[i]) {
          inp.value = vals[i];
        }
        row.appendChild(inp);
      });
      menuWrap.appendChild(row);
    }
    defaultL0Config().menu.buttons.forEach((b) => addMenuRow(b.id, b.label, b.response_text, b.url || ""));
    const addBtn = document.createElement("button");
    addBtn.type = "button";
    addBtn.className = "btn btn-secondary btn-sm";
    addBtn.textContent = "+ кнопка";
    addBtn.addEventListener("click", () => addMenuRow("", "", "", ""));
    box.appendChild(addBtn);

    const advToggle = document.createElement("button");
    advToggle.type = "button";
    advToggle.className = "btn btn-ghost btn-sm";
    advToggle.textContent = "Расширенный: JSON config";
    const advBlock = document.createElement("div");
    advBlock.className = "advanced-block";
    advBlock.hidden = true;
    const advTa = document.createElement("textarea");
    advTa.id = "l0ConfigJson";
    advTa.rows = 14;
    advTa.spellcheck = false;
    advTa.className = "json-editor";
    advBlock.appendChild(advTa);
    advToggle.addEventListener("click", () => {
      advBlock.hidden = !advBlock.hidden;
      if (!advBlock.hidden) {
        advTa.dataset.advanced = "true";
        try {
          advTa.value = JSON.stringify(buildL0ConfigFromForm(box), null, 2);
        } catch (_) {
          advTa.value = JSON.stringify(defaultL0Config(), null, 2);
        }
      } else {
        advTa.dataset.advanced = "false";
      }
    });
    box.appendChild(advToggle);
    box.appendChild(advBlock);

    const actions = document.createElement("div");
    actions.className = "admin-toolbar";
    const submit = document.createElement("button");
    submit.type = "button";
    submit.className = "btn btn-primary";
    submit.textContent = "Создать L0-бота";
    const msg = document.createElement("span");
    msg.className = "muted small";
    submit.addEventListener("click", async () => {
      msg.textContent = "";
      const orgRaw = box.querySelector("#l0OrgId").value.trim() || getOrgId(ctx);
      const botName = box.querySelector("#l0BotName").value.trim();
      const displayName = box.querySelector("#l0DisplayName").value.trim() || botName;
      if (!orgRaw || !botName) {
        msg.textContent = "Нужны org_id и bot_name.";
        return;
      }
      let configJson;
      try {
        configJson = buildL0ConfigFromForm(box);
      } catch (e) {
        msg.textContent = e.message || String(e);
        return;
      }
      try {
        const res = await ctx.apiFetch("/admin/plugins/instances/l0", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            org_id: orgRaw,
            bot_name: botName,
            display_name: displayName,
            config_json: configJson,
          }),
        });
        msg.textContent = "Создано: " + (res.bot_name || res.id || "OK");
        showResult(pre, res, null);
        const instInp = document.getElementById("l0LastInstanceId");
        if (instInp && res.id) {
          instInp.value = res.id;
        }
      } catch (e) {
        msg.textContent = e.message || String(e);
        showResult(pre, null, e.message || String(e));
      }
    });
    actions.appendChild(submit);
    actions.appendChild(msg);
    box.appendChild(actions);

    const postCap = document.createElement("p");
    postCap.className = "form-section-label";
    postCap.textContent = "После создания — тест invoke";
    box.appendChild(postCap);
    const postRow = document.createElement("div");
    postRow.className = "admin-toolbar";
    postRow.appendChild(mkField("l0LastInstanceId", "instance_id", "UUID из ответа"));
    const invokeBtn = document.createElement("button");
    invokeBtn.type = "button";
    invokeBtn.className = "btn btn-secondary";
    invokeBtn.textContent = "Invoke /help";
    const invokeMsg = document.createElement("span");
    invokeMsg.className = "muted small";
    invokeBtn.addEventListener("click", async () => {
      invokeMsg.textContent = "";
      const iid = document.getElementById("l0LastInstanceId")?.value.trim();
      if (!iid) {
        invokeMsg.textContent = "Нужен instance_id.";
        return;
      }
      try {
        const res = await ctx.apiFetch("/admin/plugins/instances/" + encodeURIComponent(iid) + "/invoke", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ type: "mention", text: "/help" }),
        });
        invokeMsg.textContent = "OK";
        showResult(pre, res, null);
      } catch (e) {
        invokeMsg.textContent = e.message || String(e);
      }
    });
    postRow.appendChild(invokeBtn);
    postRow.appendChild(invokeMsg);
    box.appendChild(postRow);

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(false);
    }
  }

  function mountLegalHold(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Выберите org или chat и загрузите флаги legal hold.";

    const box = document.createElement("div");
    box.className = "panel-form legal-hold-form";
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "GET/PATCH /admin/legal-hold/organizations/{id} и …/chats/{id}. Org из панели «Организация» подхватывается автоматически.";
    box.appendChild(hint);

    let target = { kind: null, id: null };

    function fillForm(data) {
      const set = (id, val) => {
        const n = document.getElementById(id);
        if (n) {
          n.checked = !!val;
        }
      };
      set("lhLegalHold", data.legal_hold);
      set("lhLegalHoldFiles", data.legal_hold_files);
      set("lhLegalHoldDeep", data.legal_hold_deep_archive);
    }

    function flagRow() {
      const row = document.createElement("div");
      row.className = "admin-toolbar legal-hold-flags";
      row.appendChild(mkCheckbox("lhLegalHold", "legal_hold"));
      row.appendChild(mkCheckbox("lhLegalHoldFiles", "legal_hold_files"));
      row.appendChild(mkCheckbox("lhLegalHoldDeep", "legal_hold_deep_archive"));
      return row;
    }
    box.appendChild(flagRow());

    const kindSel = document.createElement("select");
    kindSel.id = "lhKind";
    kindSel.className = "lh-kind-select";
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
    kindLbl.className = "field";
    kindLbl.appendChild(document.createTextNode("Объект"));
    kindLbl.appendChild(kindSel);
    loadRow.appendChild(kindLbl);
    loadRow.appendChild(mkField("lhTargetId", "target_id", "UUID org или chat"));
    const loadBtn = document.createElement("button");
    loadBtn.type = "button";
    loadBtn.className = "btn btn-secondary";
    loadBtn.textContent = "Загрузить";
    const loadMsg = document.createElement("span");
    loadMsg.className = "muted small";

    async function loadTarget(kind, uuid, silent) {
      loadMsg.textContent = "";
      if (!uuid) {
        if (!silent) {
          loadMsg.textContent = "Введите UUID";
        }
        return false;
      }
      const path =
        kind === "org"
          ? "/admin/legal-hold/organizations/" + encodeURIComponent(uuid)
          : "/admin/legal-hold/chats/" + encodeURIComponent(uuid);
      try {
        const data = await ctx.apiFetch(path);
        target = { kind: kind, id: uuid };
        fillForm(data);
        showResult(pre, data, null);
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
      const kind = kindSel.value;
      const uuid = document.getElementById("lhTargetId").value.trim();
      loadTarget(kind, uuid, false);
    });
    loadRow.appendChild(loadBtn);
    loadRow.appendChild(loadMsg);
    box.appendChild(loadRow);

    const orgPrefill = getOrgId(ctx);
    if (orgPrefill) {
      const inp = document.getElementById("lhTargetId");
      if (inp) {
        inp.value = orgPrefill;
      }
      kindSel.value = "org";
      loadTarget("org", orgPrefill, true);
    }

    const patchRow = document.createElement("div");
    patchRow.className = "admin-toolbar";
    const patchBtn = document.createElement("button");
    patchBtn.type = "button";
    patchBtn.className = "btn btn-primary";
    patchBtn.textContent = "Сохранить PATCH";
    const patchMsg = document.createElement("span");
    patchMsg.className = "muted small";
    patchBtn.addEventListener("click", async () => {
      patchMsg.textContent = "";
      if (!target.kind || !target.id) {
        patchMsg.textContent = "Сначала загрузите org или chat.";
        return;
      }
      const body = {
        legal_hold: document.getElementById("lhLegalHold").checked,
        legal_hold_files: document.getElementById("lhLegalHoldFiles").checked,
        legal_hold_deep_archive: document.getElementById("lhLegalHoldDeep").checked,
      };
      const path =
        target.kind === "org"
          ? "/admin/legal-hold/organizations/" + encodeURIComponent(target.id)
          : "/admin/legal-hold/chats/" + encodeURIComponent(target.id);
      try {
        const data = await ctx.apiFetch(path, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        fillForm(data);
        showResult(pre, data, null);
        patchMsg.textContent = "Сохранено.";
      } catch (e) {
        patchMsg.textContent = e.message || String(e);
      }
    });
    patchRow.appendChild(patchBtn);
    patchRow.appendChild(patchMsg);
    box.appendChild(patchRow);

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
  }

  function mountDirectorySync(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Укажите org и загрузите статус синхронизации LDAP.";

    const box = document.createElement("div");
    box.className = "panel-form directory-sync-form";
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "GET/POST /api/v1/admin/orgs/{orgId}/directory-sync/status|run — ручной прогон LDAP directory sync.";
    box.appendChild(hint);

    const row = document.createElement("div");
    row.className = "admin-toolbar";
    row.appendChild(mkField("dsOrgId", "org_id", "UUID организации"));
    const loadBtn = document.createElement("button");
    loadBtn.type = "button";
    loadBtn.className = "btn btn-secondary";
    loadBtn.textContent = "Статус";
    const runBtn = document.createElement("button");
    runBtn.type = "button";
    runBtn.className = "btn btn-primary";
    runBtn.textContent = "Запустить sync";
    const msg = document.createElement("span");
    msg.className = "muted small";
    row.appendChild(loadBtn);
    row.appendChild(runBtn);
    row.appendChild(msg);
    box.appendChild(row);

    const orgPrefill = getOrgId(ctx);
    if (orgPrefill) {
      box.querySelector("#dsOrgId").value = orgPrefill;
    }

    const statusBox = document.createElement("div");
    statusBox.id = "dsStatusBox";
    statusBox.className = "status-cards";
    statusBox.setAttribute("data-testid", "admin-directory-sync-status");
    box.appendChild(statusBox);

    function orgIdVal() {
      return document.getElementById("dsOrgId").value.trim() || getOrgId(ctx);
    }

    function renderStatus(data) {
      statusBox.innerHTML = "";
      if (!data) {
        return;
      }
      const table = document.createElement("table");
      const add = (k, v) => {
        const tr = document.createElement("tr");
        tr.innerHTML = "<th>" + k + "</th><td>" + (v != null ? String(v) : "—") + "</td>";
        table.appendChild(tr);
      };
      add("org_id", data.org_id);
      add("status", data.status);
      add("run_id", data.run_id);
      add("users_upserted", data.users_upserted);
      add("error", data.error);
      add("started_at", data.started_at);
      add("finished_at", data.finished_at);
      statusBox.appendChild(table);
      if (ctx.renderFlatObjectTable) {
        /* already in table */
      }
    }

    async function loadStatus() {
      msg.textContent = "";
      const oid = orgIdVal();
      if (!oid) {
        msg.textContent = "Нужен org_id.";
        return;
      }
      try {
        const data = await ctx.apiFetch(
          "/admin/orgs/" + encodeURIComponent(oid) + "/directory-sync/status"
        );
        renderStatus(data);
        showResult(pre, data, null);
        msg.textContent = "status=" + (data.status || "?");
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    }

    loadBtn.addEventListener("click", () => loadStatus().catch(() => {}));
    runBtn.addEventListener("click", async () => {
      msg.textContent = "sync…";
      const oid = orgIdVal();
      if (!oid) {
        msg.textContent = "Нужен org_id.";
        return;
      }
      try {
        const data = await ctx.apiFetch(
          "/admin/orgs/" + encodeURIComponent(oid) + "/directory-sync/run",
          { method: "POST" }
        );
        renderStatus(data);
        showResult(pre, data, null);
        msg.textContent = "Готово: " + (data.status || "?") + ", users=" + (data.users_upserted ?? "?");
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
    loadStatus().catch(() => {});
  }

  function mountFleetGrid(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;

    async function reload() {
      pre.textContent = "Загрузка…";
      try {
        const data = await ctx.apiFetch("/admin/ui/fleet/snapshot");
        renderFleetTable(data, summary);
        pre.textContent = JSON.stringify(data, null, 2);
        if (global.AdminUi) {
          AdminUi.showJsonBlock(true);
        }
      } catch (e) {
        pre.textContent = "Ошибка: " + e.message;
      }
    }

    const toolbar = document.createElement("div");
    toolbar.className = "admin-toolbar";
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "Allowlist: env FLEET_TARGETS_JSON. HTTP probes + NATS hot-plug workers (Phase B summary).";
    summary.appendChild(hint);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "btn btn-secondary";
    btn.textContent = "Обновить fleet";
    btn.addEventListener("click", () => reload().catch(() => {}));
    toolbar.appendChild(btn);
    summary.appendChild(toolbar);

    reload().catch(() => {});
  }

  function renderFleetTable(data, container) {
    container.querySelectorAll(".fleet-table-wrap").forEach((n) => n.remove());
    if (!data || !Array.isArray(data.components)) {
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap fleet-table-wrap";
    wrap.setAttribute("data-testid", "admin-fleet-table");
    const table = document.createElement("table");
    table.className = "json-panel-table";
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    ["id", "role", "source", "ready", "latency_ms", "error / probe"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    data.components.forEach((c) => {
      const tr = document.createElement("tr");
      if (c.ready === false) {
        tr.classList.add("fleet-row-bad");
      }
      const badge = global.AdminUi && AdminUi.statusBadge;
      const readyCell =
        c.ready === true
          ? badge
            ? badge(true, "ready")
            : "ready"
          : c.ready === false
            ? badge
              ? badge(false, "fail")
              : "fail"
            : "—";
      const probe = c.probe_url || c.base_url || c.error || c.hotplug_state || "";
      [c.id, c.role, c.source, readyCell, c.latency_ms != null ? String(c.latency_ms) : "—", probe].forEach(
        (val) => {
          const td = document.createElement("td");
          if (val instanceof Node) {
            td.appendChild(val);
          } else {
            td.textContent = val != null ? String(val) : "";
          }
          tr.appendChild(td);
        }
      );
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    const meta = document.createElement("p");
    meta.className = "muted small json-panel-note";
    const comps = data.components || [];
    const readyCount = comps.filter((c) => c.ready === true).length;
    const httpCount = comps.filter((c) => c.source === "http-probe").length;
    const hotplugCount = comps.filter((c) => c.role === "hotplug-worker").length;
    meta.textContent =
      "Aggregator: " +
      (data.aggregator_node || "?") +
      " · generated: " +
      (data.generated_at || "?") +
      " · ready " +
      readyCount +
      "/" +
      comps.length +
      " · http-probe " +
      httpCount +
      " · hotplug " +
      hotplugCount;
    wrap.appendChild(meta);
    container.appendChild(wrap);
  }

  function mountExternalStackStatus(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;

    async function reload() {
      pre.textContent = "Загрузка…";
      try {
        const data = await ctx.apiFetch("/platform/external-stack/status");
        const profiles = await ctx.apiFetch("/platform/external-stack/profiles");
        const compatibilityPacks = await ctx.apiFetch("/platform/external-stack/compatibility-packs");
        renderExternalStackTable(data, summary, profiles);
        renderExternalStackCompatibilityPacks(compatibilityPacks, summary);
        pre.textContent = JSON.stringify({ status: data, profiles: profiles, compatibility_packs: compatibilityPacks }, null, 2);
        if (global.AdminUi) {
          AdminUi.showJsonBlock(true);
        }
      } catch (e) {
        pre.textContent = "Ошибка: " + e.message;
      }
    }

    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "Read-only статус spec 023: desired/observed manifest, health, validation и support boundary без секретов.";
    summary.appendChild(hint);

    const toolbar = document.createElement("div");
    toolbar.className = "admin-toolbar";
    toolbar.id = "externalStackFilters";
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "btn btn-secondary";
    btn.textContent = "Обновить external stack";
    btn.addEventListener("click", () => reload().catch(() => {}));
    toolbar.appendChild(btn);
    [
      ["validation", "validation", ["all", "passed", "failed"]],
      ["health", "health", ["all", "healthy", "degraded", "configured"]],
      ["mismatch", "mismatch", ["all", "yes", "no"]],
    ].forEach(([id, label, values]) => {
      const select = document.createElement("select");
      select.id = "externalStackFilter-" + id;
      select.dataset.filter = id;
      values.forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label + ": " + value;
        select.appendChild(option);
      });
      select.addEventListener("change", () => {
        const data = summary._externalStackData;
        if (data) {
          renderExternalStackTable(data, summary);
        }
      });
      toolbar.appendChild(select);
    });
    summary.appendChild(toolbar);
    mountExternalStackPreflight(summary, pre, ctx);

    reload().catch(() => {});
  }

  function mountExternalStackPreflight(summary, pre, ctx) {
    if (summary.querySelector("#externalStackCheckpointJson")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "panel-form external-stack-preflight";
    const title = document.createElement("p");
    title.className = "form-section-label";
    title.textContent = "Checkpoint preflight (repo-local)";
    box.appendChild(title);
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent = "POST /platform/external-stack/preflight/checkpoint — проверка marker groups без live cutover.";
    box.appendChild(hint);
    const ta = document.createElement("textarea");
    ta.id = "externalStackCheckpointJson";
    ta.rows = 8;
    ta.spellcheck = false;
    ta.value = JSON.stringify(
      {
        component: "search",
        source_profile: "sql-search",
        target_profile: "solr-bundled",
        checkpoint_type: "reindex",
        markers: {
          reindex_cursor: "messages:42",
          index_schema_version: "v1",
          shadow_target: "solr-shadow",
        },
        rollback_profile: "sql-search",
        watch_window: "PT4H",
      },
      null,
      2
    );
    box.appendChild(ta);
    const row = document.createElement("div");
    row.className = "admin-toolbar";
    const validate = document.createElement("button");
    validate.type = "button";
    validate.className = "btn btn-secondary";
    validate.textContent = "Validate checkpoint";
    const result = document.createElement("span");
    result.className = "muted small";
    validate.addEventListener("click", async () => {
      result.textContent = "Проверка…";
      try {
        const body = JSON.parse(ta.value);
        const report = await ctx.apiFetch("/platform/external-stack/preflight/checkpoint", {
          method: "POST",
          body: JSON.stringify(body),
        });
        result.textContent =
          (report.passed ? "OK" : "BLOCKED") +
          " · severity=" +
          (report.severity || "?") +
          " · missing=" +
          ((report.missing_markers || []).join(", ") || "—");
        pre.textContent = JSON.stringify(report, null, 2);
        if (global.AdminUi) {
          AdminUi.showJsonBlock(true);
        }
      } catch (e) {
        result.textContent = "Ошибка: " + e.message;
      }
    });
    row.appendChild(validate);
    row.appendChild(result);
    box.appendChild(row);
    summary.appendChild(box);
    mountExternalStackManifestPreflight(summary, pre, ctx);
  }

  function mountExternalStackManifestPreflight(summary, pre, ctx) {
    if (summary.querySelector("#externalStackManifestsJson")) {
      return;
    }
    const box = document.createElement("div");
    box.className = "panel-form external-stack-preflight";
    const title = document.createElement("p");
    title.className = "form-section-label";
    title.textContent = "Manifest preflight (repo-local)";
    box.appendChild(title);
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent = "POST /platform/external-stack/preflight/manifests — проверка desired manifests без deploy.";
    box.appendChild(hint);
    const ta = document.createElement("textarea");
    ta.id = "externalStackManifestsJson";
    ta.rows = 10;
    ta.spellcheck = false;
    ta.value = JSON.stringify(
      {
        manifests: [
          {
            component: "object-storage",
            backend_family: "s3-compatible",
            connector: "minio-s3",
            version: "configured",
            role: "active",
            endpoint: "https://minio.example.test",
            resource_name_or_alias: "avandocmsg",
            schema_or_protocol_version: "s3",
            compatibility_profile: "s3-minio-bundled",
            topology: "configured",
            config_revision: "manual-preflight",
            capabilities: ["put_get_head_delete_list", "multipart", "checksum"],
            data_classification: "file-content",
            support_boundary: {
              deployment_owner: "korus",
              backup_owner: "korus",
              ha_owner: "korus",
              upgrade_owner: "korus",
              incident_owner: "korus",
              vendor_support_required: false,
              korus_support_scope: "full-stack",
            },
            metadata: { serve_traffic: "true" },
          },
        ],
      },
      null,
      2
    );
    box.appendChild(ta);
    const row = document.createElement("div");
    row.className = "admin-toolbar";
    const validate = document.createElement("button");
    validate.type = "button";
    validate.className = "btn btn-secondary";
    validate.textContent = "Validate manifests";
    const result = document.createElement("span");
    result.className = "muted small";
    validate.addEventListener("click", async () => {
      result.textContent = "Проверка…";
      try {
        const body = JSON.parse(ta.value);
        const validation = await ctx.apiFetch("/platform/external-stack/preflight/manifests", {
          method: "POST",
          body: JSON.stringify(body),
        });
        result.textContent =
          (validation.passed ? "OK" : "FAILED") +
          " · failures=" +
          ((validation.failures || []).length) +
          " · redacted=" +
          (validation.redacted === true);
        pre.textContent = JSON.stringify(validation, null, 2);
        if (global.AdminUi) {
          AdminUi.showJsonBlock(true);
        }
      } catch (e) {
        result.textContent = "Ошибка: " + e.message;
      }
    });
    row.appendChild(validate);
    row.appendChild(result);
    box.appendChild(row);
    summary.appendChild(box);
  }

  function renderExternalStackTable(data, container, profiles) {
    container.querySelectorAll(".external-stack-table-wrap").forEach((n) => n.remove());
    if (!data || !data.components) {
      return;
    }
    container._externalStackData = data;
    if (profiles) {
      container._externalStackProfiles = profiles;
    }
    const filters = externalStackFilters(container);
    const entries = Object.entries(data.components)
      .filter(([, c]) => externalStackMatches(c, filters))
      .sort((a, b) => externalStackGroup(a[0]).localeCompare(externalStackGroup(b[0])) || a[0].localeCompare(b[0]));
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap external-stack-table-wrap";
    wrap.setAttribute("data-testid", "admin-external-stack-table");
    const table = document.createElement("table");
    table.className = "json-panel-table";
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    [
      "component",
      "group",
      "desired",
      "observed",
      "health",
      "validation",
      "support",
      "degraded / mismatch",
    ].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    const badge = global.AdminUi && AdminUi.statusBadge;
    entries.forEach(([component, c]) => {
      const tr = document.createElement("tr");
      if (c.validation_status !== "passed" || c.health_status === "degraded" || c.mismatch === true) {
        tr.classList.add("fleet-row-bad");
      }
      const validation =
        c.validation_status === "passed" && badge
          ? badge(true, "passed")
          : c.validation_status && badge
            ? badge(false, c.validation_status)
            : c.validation_status || "—";
      const health =
        c.health_status === "healthy" && badge
          ? badge(true, "healthy")
          : c.health_status && badge
            ? badge(false, c.health_status)
            : c.health_status || "—";
      const mismatch = c.mismatch ? "mismatch" : "";
      const supportBadge = externalStackSupportBadge(c);
      const profile = externalStackProfileFor(container._externalStackProfiles, c);
      const drilldown = externalStackDrilldown(c, profile);
      [
        component,
        externalStackGroup(component),
        c.desired_connector || "—",
        c.observed_connector || "—",
        health,
        validation,
        supportBadge,
        drilldown || [c.degraded_reason, mismatch].filter(Boolean).join(" · ") || "—",
      ].forEach((val) => {
        const td = document.createElement("td");
        if (val instanceof Node) {
          td.appendChild(val);
        } else {
          td.textContent = val != null ? String(val) : "";
        }
        tr.appendChild(td);
      });
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    const meta = document.createElement("p");
    meta.className = "muted small json-panel-note";
    const passed = entries.filter(([, c]) => c.validation_status === "passed").length;
    meta.textContent =
      "External stack components: " + passed + "/" + entries.length + " validation passed. Badges: supported/candidate/deferred.";
    wrap.appendChild(meta);
    container.appendChild(wrap);
  }

  function renderExternalStackCompatibilityPacks(data, container) {
    container.querySelectorAll(".external-stack-pack-wrap").forEach((n) => n.remove());
    if (!data || !data.packs) {
      return;
    }
    const entries = Object.values(data.packs).sort((a, b) =>
      String(a.component || "").localeCompare(String(b.component || "")) ||
      String(a.profile_id || "").localeCompare(String(b.profile_id || ""))
    );
    const wrap = document.createElement("div");
    wrap.className = "json-table-wrap external-stack-pack-wrap";
    wrap.setAttribute("data-testid", "admin-external-stack-packs");
    const heading = document.createElement("p");
    heading.className = "form-section-label";
    heading.textContent = "Compatibility pack catalog";
    wrap.appendChild(heading);
    const table = document.createElement("table");
    table.className = "json-panel-table";
    const head = document.createElement("thead");
    const hr = document.createElement("tr");
    ["profile", "component", "lifecycle", "checks", "evidence", "unsupported"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      hr.appendChild(th);
    });
    head.appendChild(hr);
    table.appendChild(head);
    const body = document.createElement("tbody");
    entries.forEach((pack) => {
      const tr = document.createElement("tr");
      if (String(pack.lifecycle_status || "").includes("candidate")) {
        tr.classList.add("fleet-row-bad");
      }
      [
        pack.profile_id,
        pack.component,
        pack.lifecycle_status,
        (pack.required_checks || []).slice(0, 3).join(", "),
        (pack.promotion_evidence || []).join(", "),
        (pack.unsupported_modes || []).join(", ") || "—",
      ].forEach((val) => {
        const td = document.createElement("td");
        td.textContent = val != null ? String(val) : "—";
        tr.appendChild(td);
      });
      body.appendChild(tr);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    const note = document.createElement("p");
    note.className = "muted small json-panel-note";
    note.textContent = "Full catalog includes supported, external/BYO and candidate packs; candidate rows are not production support claims.";
    wrap.appendChild(note);
    container.appendChild(wrap);
  }

  function externalStackFilters(container) {
    const toolbar = container.querySelector("#externalStackFilters");
    if (!toolbar) {
      return { validation: "all", health: "all", mismatch: "all" };
    }
    const read = (name) => {
      const el = toolbar.querySelector('[data-filter="' + name + '"]');
      return el ? el.value : "all";
    };
    return { validation: read("validation"), health: read("health"), mismatch: read("mismatch") };
  }

  function externalStackMatches(component, filters) {
    if (filters.validation !== "all" && component.validation_status !== filters.validation) {
      return false;
    }
    if (filters.health !== "all" && component.health_status !== filters.health) {
      return false;
    }
    if (filters.mismatch === "yes" && component.mismatch !== true) {
      return false;
    }
    if (filters.mismatch === "no" && component.mismatch === true) {
      return false;
    }
    return true;
  }

  function externalStackGroup(component) {
    if (["media", "turn", "notifications", "dlp", "integrations"].includes(component)) {
      return "add-ons";
    }
    if (["search"].includes(component)) {
      return "candidates";
    }
    return "core";
  }

  function externalStackSupportBadge(component) {
    const span = document.createElement("span");
    span.className = "support-badge";
    const boundary = component.support_boundary || "—";
    const connector = (component.desired_connector || "").toLowerCase();
    if (connector.includes("candidate") || boundary.includes("vendor")) {
      span.textContent = "candidate";
    } else if (boundary.includes("deferred")) {
      span.textContent = "deferred";
    } else {
      span.textContent = boundary.includes("customer") ? "external/BYO" : "supported";
    }
    span.title = boundary;
    return span;
  }

  function externalStackProfileFor(profiles, component) {
    if (!profiles || !profiles.profiles) {
      return null;
    }
    return profiles.profiles[component.desired_connector] || profiles.profiles[component.observed_connector] || null;
  }

  function externalStackDrilldown(component, profile) {
    const details = document.createElement("details");
    details.className = "external-stack-drilldown";
    const summary = document.createElement("summary");
    summary.textContent = [component.degraded_reason, component.mismatch ? "mismatch" : ""].filter(Boolean).join(" · ") || "details";
    details.appendChild(summary);
    const lines = []
      .concat(component.validation_failures || [])
      .concat(component.validation_warnings || []);
    if (profile) {
      lines.push("required_checks: " + (profile.required_checks || []).join(", "));
      lines.push("promotion_evidence: " + (profile.promotion_evidence || []).join(", "));
      lines.push("unsupported_modes: " + (profile.unsupported_modes || []).join(", "));
    }
    const pre = document.createElement("pre");
    pre.textContent = lines.length ? lines.join("\n") : "No validation failures/warnings.";
    details.appendChild(pre);
    return details;
  }

  function enhancePluginInstances(summary, pre, ctx) {
    if (document.getElementById("pluginInstanceTools")) {
      return;
    }
    const box = document.createElement("div");
    box.id = "pluginInstanceTools";
    box.className = "panel-form card-toolbar";
    const cap = document.createElement("p");
    cap.className = "muted small";
    cap.textContent = "Тест instance: invoke и outbound webhook.";
    box.appendChild(cap);

    const row = document.createElement("div");
    row.className = "admin-toolbar";
    row.appendChild(mkField("pluginInstId", "instance_id", "UUID instance"));
    const invokeBtn = document.createElement("button");
    invokeBtn.type = "button";
    invokeBtn.className = "btn btn-secondary";
    invokeBtn.textContent = "Invoke";
    const invokeMsg = document.createElement("span");
    invokeMsg.className = "muted small";
    invokeBtn.addEventListener("click", async () => {
      invokeMsg.textContent = "";
      const iid = document.getElementById("pluginInstId")?.value.trim();
      if (!iid) {
        invokeMsg.textContent = "Нужен instance_id.";
        return;
      }
      try {
        const res = await ctx.apiFetch("/admin/plugins/instances/" + encodeURIComponent(iid) + "/invoke", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            type: "mention",
            text: document.getElementById("pluginInvokeText")?.value.trim() || "/help",
          }),
        });
        invokeMsg.textContent = "OK";
        showResult(pre, res, null);
      } catch (e) {
        invokeMsg.textContent = e.message || String(e);
      }
    });
    row.appendChild(invokeBtn);
    const textInp = document.createElement("input");
    textInp.id = "pluginInvokeText";
    textInp.placeholder = "text (/help)";
    textInp.value = "/help";
    row.appendChild(textInp);
    row.appendChild(invokeMsg);
    box.appendChild(row);

    const outRow = document.createElement("div");
    outRow.className = "admin-toolbar";
    outRow.appendChild(mkField("pluginOutChat", "target_chat_id", "UUID чата"));
    outRow.appendChild(mkField("pluginOutActor", "actor_user_id", "UUID пользователя"));
    outRow.appendChild(mkField("pluginOutToken", "outbound_token", "token", "password"));
    const outBtn = document.createElement("button");
    outBtn.type = "button";
    outBtn.className = "btn btn-primary btn-sm";
    outBtn.textContent = "Configure outbound";
    const outMsg = document.createElement("span");
    outMsg.className = "muted small";
    outBtn.addEventListener("click", async () => {
      outMsg.textContent = "";
      const iid = document.getElementById("pluginInstId")?.value.trim();
      const chat = document.getElementById("pluginOutChat")?.value.trim();
      const actor = document.getElementById("pluginOutActor")?.value.trim();
      const tok = document.getElementById("pluginOutToken")?.value.trim();
      if (!iid || !chat || !actor || !tok) {
        outMsg.textContent = "Заполните все поля outbound.";
        return;
      }
      try {
        const res = await ctx.apiFetch("/admin/plugins/instances/" + encodeURIComponent(iid) + "/outbound", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            target_chat_id: chat,
            actor_user_id: actor,
            outbound_token: tok,
          }),
        });
        outMsg.textContent = "OK";
        showResult(pre, res, null);
      } catch (e) {
        outMsg.textContent = e.message || String(e);
      }
    });
    outRow.appendChild(outBtn);
    outRow.appendChild(outMsg);
    box.appendChild(outRow);

    const composeCap = document.createElement("p");
    composeCap.className = "muted small";
    composeCap.textContent =
      "Integrations guest compose (scaffold): планирует команду qemu-guest-compose на хосте Windows.";
    box.appendChild(composeCap);
    const composeRow = document.createElement("div");
    composeRow.className = "admin-toolbar";
    composeRow.setAttribute("data-testid", "admin-plugin-compose-toolbar");
    const actSel = document.createElement("select");
    actSel.id = "pluginComposeAction";
    ["up", "down", "build"].forEach((v) => {
      const o = document.createElement("option");
      o.value = v;
      o.textContent = v;
      actSel.appendChild(o);
    });
    const svcSel = document.createElement("select");
    svcSel.id = "pluginComposeService";
    [
      "connector-runtime",
      "onec-bridge",
      "exchange-bridge",
      "mock-apis",
      "echo-php",
    ].forEach((v) => {
      const o = document.createElement("option");
      o.value = v;
      o.textContent = v;
      svcSel.appendChild(o);
    });
    const composeBtn = document.createElement("button");
    composeBtn.type = "button";
    composeBtn.className = "btn btn-secondary btn-sm";
    composeBtn.setAttribute("data-testid", "admin-plugin-compose-btn");
    composeBtn.textContent = "Plan compose";
    const composeMsg = document.createElement("span");
    composeMsg.className = "muted small";
    composeBtn.addEventListener("click", async () => {
      composeMsg.textContent = "";
      try {
        const res = await ctx.apiFetch("/admin/plugins/integrations/compose", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            action: actSel.value,
            services: [svcSel.value],
          }),
        });
        composeMsg.textContent = res.status || "OK";
        showResult(pre, res, null);
      } catch (e) {
        composeMsg.textContent = e.message || String(e);
      }
    });
    composeRow.appendChild(actSel);
    composeRow.appendChild(svcSel);
    composeRow.appendChild(composeBtn);
    composeRow.appendChild(composeMsg);
    box.appendChild(composeRow);

    summary.insertBefore(box, summary.firstChild);
  }

  function enhancePluginPolicies(summary, pre, ctx) {
    if (document.getElementById("pluginPolicyForm")) {
      return;
    }
    const box = document.createElement("div");
    box.id = "pluginPolicyForm";
    box.className = "panel-form card-toolbar";
    const cap = document.createElement("p");
    cap.className = "muted small";
    cap.textContent =
      "Обновление политики организации (POST). org_id берётся из панели «Организация» или поля ниже.";
    box.appendChild(cap);

    const row = document.createElement("div");
    row.className = "admin-toolbar";
    row.appendChild(mkField("pluginPolicyOrg", "org_id", "UUID org (опц.)"));
    const llmSel = document.createElement("select");
    llmSel.id = "pluginPolicyLlm";
    ["on_prem_only", "cloud_allowed", "hybrid"].forEach((v) => {
      const o = document.createElement("option");
      o.value = v;
      o.textContent = v;
      llmSel.appendChild(o);
    });
    row.appendChild(llmSel);
    const ocrLbl = document.createElement("label");
    ocrLbl.className = "checkbox-inline";
    const ocrCb = document.createElement("input");
    ocrCb.type = "checkbox";
    ocrCb.id = "pluginPolicyOcr";
    ocrCb.checked = true;
    ocrLbl.appendChild(ocrCb);
    ocrLbl.appendChild(document.createTextNode(" ocr_on_prem_only"));
    row.appendChild(ocrLbl);
    row.appendChild(mkField("pluginPolicyPresets", "allowed_preset_ids", "presets (csv)"));
    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.className = "btn btn-primary btn-sm";
    saveBtn.textContent = "Сохранить политику";
    const msg = document.createElement("span");
    msg.className = "muted small";
    saveBtn.addEventListener("click", async () => {
      msg.textContent = "";
      let orgId = document.getElementById("pluginPolicyOrg")?.value.trim();
      if (!orgId && window.AdminUi && typeof AdminUi.getSelectedOrgId === "function") {
        orgId = AdminUi.getSelectedOrgId() || "";
      }
      if (!orgId) {
        msg.textContent = "Нужен org_id.";
        return;
      }
      const presetsRaw = document.getElementById("pluginPolicyPresets")?.value.trim() || "";
      const allowed = presetsRaw
        ? presetsRaw.split(",").map((s) => s.trim()).filter(Boolean)
        : [];
      try {
        const res = await ctx.apiFetch(
          "/admin/plugins/policies?org_id=" + encodeURIComponent(orgId),
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              llm_mode: llmSel.value,
              ocr_on_prem_only: ocrCb.checked,
              allowed_preset_ids: allowed,
            }),
          }
        );
        msg.textContent = "OK";
        showResult(pre, res, null);
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    });
    row.appendChild(saveBtn);
    row.appendChild(msg);
    box.appendChild(row);
    summary.insertBefore(box, summary.firstChild);
  }

  function mountIpAllowlist(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Загрузите политику IP allowlist для организации.";

    const box = document.createElement("div");
    box.className = "panel-form ip-allowlist-form";
    box.setAttribute("data-testid", "admin-ip-allowlist-form");
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "GET/PATCH /api/v1/admin/orgs/{orgId}/ip-allowlist. При ORG_IP_ALLOWLIST_ENFORCE=1 фильтр блокирует API.";
    box.appendChild(hint);

    const row = document.createElement("div");
    row.className = "admin-toolbar";
    row.appendChild(mkField("ipAllowOrgId", "org_id", "UUID org"));
    const loadBtn = document.createElement("button");
    loadBtn.type = "button";
    loadBtn.className = "btn btn-secondary";
    loadBtn.setAttribute("data-testid", "admin-ip-allowlist-load");
    loadBtn.textContent = "Загрузить";
    const msg = document.createElement("span");
    msg.className = "muted small";
    row.appendChild(loadBtn);
    row.appendChild(msg);
    box.appendChild(row);

    const flags = document.createElement("div");
    flags.className = "admin-toolbar";
    const enLbl = document.createElement("label");
    enLbl.className = "checkbox-inline";
    const enCb = document.createElement("input");
    enCb.type = "checkbox";
    enCb.id = "ipAllowEnabled";
    enLbl.appendChild(enCb);
    enLbl.appendChild(document.createTextNode(" enabled"));
    flags.appendChild(enLbl);
    const cidrsLbl = document.createElement("label");
    cidrsLbl.className = "small";
    cidrsLbl.textContent = "allowed_cidrs";
    const cidrsInp = document.createElement("textarea");
    cidrsInp.id = "ipAllowCidrs";
    cidrsInp.rows = 2;
    cidrsInp.placeholder = "127.0.0.1,192.168.1.10";
    cidrsLbl.appendChild(cidrsInp);
    flags.appendChild(cidrsLbl);
    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.className = "btn btn-primary";
    saveBtn.setAttribute("data-testid", "admin-ip-allowlist-save");
    saveBtn.textContent = "Сохранить";
    const saveMsg = document.createElement("span");
    saveMsg.className = "muted small";
    flags.appendChild(saveBtn);
    flags.appendChild(saveMsg);
    box.appendChild(flags);

    function orgIdVal() {
      const fromField = document.getElementById("ipAllowOrgId")?.value.trim();
      if (fromField) {
        return fromField;
      }
      return getOrgId(ctx);
    }

    async function loadPolicy() {
      msg.textContent = "";
      const oid = orgIdVal();
      if (!oid) {
        msg.textContent = "Нужен org_id.";
        return;
      }
      try {
        const data = await ctx.apiFetch("/admin/orgs/" + encodeURIComponent(oid) + "/ip-allowlist");
        enCb.checked = !!data.enabled;
        cidrsInp.value = data.allowed_cidrs || "";
        showResult(pre, data, null);
        msg.textContent = "Загружено.";
      } catch (e) {
        msg.textContent = e.message || String(e);
      }
    }

    loadBtn.addEventListener("click", () => loadPolicy().catch(() => {}));
    saveBtn.addEventListener("click", async () => {
      saveMsg.textContent = "";
      const oid = orgIdVal();
      if (!oid) {
        saveMsg.textContent = "Нужен org_id.";
        return;
      }
      try {
        const data = await ctx.apiFetch("/admin/orgs/" + encodeURIComponent(oid) + "/ip-allowlist", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            enabled: enCb.checked,
            allowed_cidrs: cidrsInp.value.trim(),
          }),
        });
        showResult(pre, data, null);
        saveMsg.textContent = "Сохранено.";
      } catch (e) {
        saveMsg.textContent = e.message || String(e);
      }
    });

    const orgPrefill = getOrgId(ctx);
    if (orgPrefill) {
      const inp = document.getElementById("ipAllowOrgId");
      if (inp) {
        inp.value = orgPrefill;
      }
      loadPolicy().catch(() => {});
    }

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
  }

  function mountMigrationImport(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Создайте job импорта или обработайте pending job.";

    const box = document.createElement("div");
    box.className = "panel-form migration-import-form";
    box.setAttribute("data-testid", "admin-migration-import-form");
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "POST /api/v1/admin/migration-import · POST .../{jobId}/process — scaffold processor (spec 022).";
    box.appendChild(hint);

    const createRow = document.createElement("div");
    createRow.className = "admin-toolbar";
    const srcLbl = document.createElement("label");
    srcLbl.className = "small";
    srcLbl.textContent = "source";
    const srcInp = document.createElement("input");
    srcInp.id = "migrationImportSource";
    srcInp.value = "telegram_export_v1";
    srcLbl.appendChild(srcInp);
    const cfgLbl = document.createElement("label");
    cfgLbl.className = "small";
    cfgLbl.textContent = "config_json";
    const cfgInp = document.createElement("input");
    cfgInp.id = "migrationImportConfig";
    cfgInp.placeholder = "{}";
    cfgInp.value = "{}";
    cfgLbl.appendChild(cfgInp);
    const fileLbl = document.createElement("label");
    fileLbl.className = "small";
    fileLbl.textContent = "telegram export JSON";
    const fileInp = document.createElement("input");
    fileInp.type = "file";
    fileInp.accept = "application/json,.json";
    fileInp.setAttribute("data-testid", "admin-migration-import-file");
    fileInp.addEventListener("change", () => {
      const f = fileInp.files && fileInp.files[0];
      if (!f) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const txt = String(reader.result || "");
          JSON.parse(txt);
          cfgInp.value = txt;
          createMsg.textContent = "JSON loaded: " + f.name;
        } catch (e) {
          createMsg.textContent = "Invalid JSON: " + (e.message || String(e));
        }
      };
      reader.onerror = () => {
        createMsg.textContent = "Could not read file";
      };
      reader.readAsText(f);
    });
    fileLbl.appendChild(fileInp);
    const createBtn = document.createElement("button");
    createBtn.type = "button";
    createBtn.className = "btn btn-primary";
    createBtn.setAttribute("data-testid", "admin-migration-import-create");
    createBtn.textContent = "Создать job";
    const createMsg = document.createElement("span");
    createMsg.className = "muted small";
    createBtn.addEventListener("click", async () => {
      createMsg.textContent = "";
      try {
        const data = await ctx.apiFetch("/admin/migration-import", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            source: srcInp.value.trim() || "telegram_export_v1",
            config_json: cfgInp.value.trim() || "{}",
          }),
        });
        createMsg.textContent = "job " + (data.id || "?");
        showResult(pre, data, null);
        await reloadJobs();
      } catch (e) {
        createMsg.textContent = e.message || String(e);
      }
    });
    createRow.appendChild(srcLbl);
    createRow.appendChild(cfgLbl);
    createRow.appendChild(fileLbl);
    createRow.appendChild(createBtn);
    createRow.appendChild(createMsg);
    box.appendChild(createRow);

    const listRow = document.createElement("div");
    listRow.className = "admin-toolbar";
    const reloadBtn = document.createElement("button");
    reloadBtn.type = "button";
    reloadBtn.className = "btn btn-secondary";
    reloadBtn.textContent = "Обновить список";
    const listMsg = document.createElement("span");
    listMsg.className = "muted small";
    listRow.appendChild(reloadBtn);
    listRow.appendChild(listMsg);
    box.appendChild(listRow);

    const jobsWrap = document.createElement("div");
    jobsWrap.className = "json-table-wrap";
    jobsWrap.setAttribute("data-testid", "admin-migration-import-jobs");
    box.appendChild(jobsWrap);

    async function reloadJobs() {
      listMsg.textContent = "";
      try {
        const jobs = await ctx.apiFetch("/admin/migration-import?limit=20");
        jobsWrap.innerHTML = "";
        if (!Array.isArray(jobs) || jobs.length === 0) {
          jobsWrap.textContent = "Нет jobs.";
          return;
        }
        const table = document.createElement("table");
        table.className = "json-panel-table";
        const head = document.createElement("thead");
        const hr = document.createElement("tr");
        ["id", "status", "source", "process"].forEach((h) => {
          const th = document.createElement("th");
          th.textContent = h;
          hr.appendChild(th);
        });
        head.appendChild(hr);
        table.appendChild(head);
        const body = document.createElement("tbody");
        jobs.forEach((j) => {
          const tr = document.createElement("tr");
          const idTd = document.createElement("td");
          idTd.textContent = j.id || "";
          tr.appendChild(idTd);
          const stTd = document.createElement("td");
          stTd.textContent = j.status || "";
          tr.appendChild(stTd);
          const srcTd = document.createElement("td");
          srcTd.textContent = j.source || "";
          tr.appendChild(srcTd);
          const actTd = document.createElement("td");
          const procBtn = document.createElement("button");
          procBtn.type = "button";
          procBtn.className = "btn btn-secondary btn-sm";
          procBtn.textContent = "Process";
          procBtn.addEventListener("click", async () => {
            try {
              const res = await ctx.apiFetch(
                "/admin/migration-import/" + encodeURIComponent(j.id) + "/process",
                { method: "POST" }
              );
              showResult(pre, res, null);
              await reloadJobs();
            } catch (e) {
              listMsg.textContent = e.message || String(e);
            }
          });
          actTd.appendChild(procBtn);
          tr.appendChild(actTd);
          body.appendChild(tr);
        });
        table.appendChild(body);
        jobsWrap.appendChild(table);
        listMsg.textContent = jobs.length + " job(s)";
      } catch (e) {
        listMsg.textContent = e.message || String(e);
      }
    }

    reloadBtn.addEventListener("click", () => reloadJobs().catch(() => {}));
    reloadJobs().catch(() => {});

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
  }

  function mountFederationTrust(summary, pre, ctx) {
    summary.innerHTML = "";
    summary.hidden = false;
    pre.textContent = "Управление доверием между организациями (federation trust).";

    const box = document.createElement("div");
    box.className = "panel-form federation-trust-form";
    box.setAttribute("data-testid", "admin-federation-trust-form");
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent =
      "GET/POST /api/v1/admin/federation/trust — partner org UUID, status active|suspended|revoked.";
    box.appendChild(hint);

    const createRow = document.createElement("div");
    createRow.className = "admin-toolbar";
    createRow.appendChild(mkField("federationPartnerOrgId", "partner_org_id", "UUID partner org"));
    const statusSel = document.createElement("select");
    statusSel.id = "federationTrustStatus";
    ["active", "suspended", "revoked"].forEach((st) => {
      const opt = document.createElement("option");
      opt.value = st;
      opt.textContent = st;
      statusSel.appendChild(opt);
    });
    const statusLbl = document.createElement("label");
    statusLbl.className = "field";
    statusLbl.appendChild(document.createTextNode("status "));
    statusLbl.appendChild(statusSel);
    createRow.appendChild(statusLbl);
    const createBtn = document.createElement("button");
    createBtn.type = "button";
    createBtn.className = "btn btn-primary";
    createBtn.setAttribute("data-testid", "admin-federation-trust-create");
    createBtn.textContent = "Создать trust";
    const createMsg = document.createElement("span");
    createMsg.className = "muted small";
    createBtn.addEventListener("click", async () => {
      createMsg.textContent = "";
      const partner = document.getElementById("federationPartnerOrgId")?.value.trim();
      if (!partner) {
        createMsg.textContent = "partner_org_id required";
        return;
      }
      try {
        const data = await ctx.apiFetch("/admin/federation/trust", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            partner_org_id: partner,
            status: statusSel.value || "active",
          }),
        });
        createMsg.textContent = "trust " + (data.id || "?");
        showResult(pre, data, null);
        await reloadTrusts();
      } catch (e) {
        createMsg.textContent = e.message || String(e);
      }
    });
    createRow.appendChild(createBtn);
    createRow.appendChild(createMsg);
    box.appendChild(createRow);

    const listRow = document.createElement("div");
    listRow.className = "admin-toolbar";
    const reloadBtn = document.createElement("button");
    reloadBtn.type = "button";
    reloadBtn.className = "btn btn-secondary";
    reloadBtn.textContent = "Обновить список";
    const listMsg = document.createElement("span");
    listMsg.className = "muted small";
    listRow.appendChild(reloadBtn);
    listRow.appendChild(listMsg);
    box.appendChild(listRow);

    const trustsWrap = document.createElement("div");
    trustsWrap.className = "json-table-wrap";
    trustsWrap.setAttribute("data-testid", "admin-federation-trust-list");
    box.appendChild(trustsWrap);

    async function reloadTrusts() {
      listMsg.textContent = "";
      try {
        const rows = await ctx.apiFetch("/admin/federation/trust");
        trustsWrap.innerHTML = "";
        if (!Array.isArray(rows) || rows.length === 0) {
          trustsWrap.textContent = "Нет trust записей.";
          return;
        }
        const table = document.createElement("table");
        table.className = "json-panel-table";
        const head = document.createElement("thead");
        const hr = document.createElement("tr");
        ["id", "partner_org_id", "status", "expires_at"].forEach((h) => {
          const th = document.createElement("th");
          th.textContent = h;
          hr.appendChild(th);
        });
        head.appendChild(hr);
        table.appendChild(head);
        const body = document.createElement("tbody");
        rows.forEach((r) => {
          const tr = document.createElement("tr");
          ["id", "partner_org_id", "status", "expires_at"].forEach((k) => {
            const td = document.createElement("td");
            td.textContent = r[k] != null ? String(r[k]) : "";
            tr.appendChild(td);
          });
          body.appendChild(tr);
        });
        table.appendChild(body);
        trustsWrap.appendChild(table);
        listMsg.textContent = rows.length + " trust(s)";
      } catch (e) {
        listMsg.textContent = e.message || String(e);
      }
    }

    reloadBtn.addEventListener("click", () => reloadTrusts().catch(() => {}));
    reloadTrusts().catch(() => {});

    summary.appendChild(box);
    if (global.AdminUi) {
      AdminUi.showJsonBlock(true);
    }
  }

  const HANDLERS = {
    "plugins-l0-wizard": mountL0Wizard,
    "core-legal-hold": mountLegalHold,
    "core-directory-sync": mountDirectorySync,
    "core-ip-allowlist": mountIpAllowlist,
    "core-migration-import": mountMigrationImport,
    "core-federation-trust": mountFederationTrust,
    "core-external-stack": mountExternalStackStatus,
  };

  function tryMount(section, ctx) {
    const fn = HANDLERS[section.id];
    if (!fn) {
      return false;
    }
    fn(ctx.summary, ctx.pre, ctx);
    const hint = document.getElementById("panelHint");
    if (hint) {
      hint.hidden = true;
    }
    return true;
  }

  global.AdminPanels = {
    tryMount,
    mountFleetGrid,
    enhancePluginInstances,
    enhancePluginPolicies,
  };
})(window);
