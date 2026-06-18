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
    pre.textContent = "Загрузите legal hold для org или chat.";

    const box = document.createElement("div");
    box.className = "panel-form legal-hold-form";
    const hint = document.createElement("p");
    hint.className = "muted small";
    hint.textContent = "GET/PATCH /admin/legal-hold/organizations/{id} и …/chats/{id} — флаги V025.";
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

    const mkLoadRow = (inputId, label, kind) => {
      const row = document.createElement("div");
      row.className = "admin-toolbar";
      row.appendChild(mkField(inputId, label, "UUID"));
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn btn-secondary";
      btn.textContent = "Загрузить";
      const msg = document.createElement("span");
      msg.className = "muted small";
      btn.addEventListener("click", async () => {
        msg.textContent = "";
        const uuid = document.getElementById(inputId).value.trim();
        if (!uuid) {
          msg.textContent = "Введите UUID";
          return;
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
          msg.textContent = "Загружено.";
        } catch (e) {
          msg.textContent = e.message || String(e);
        }
      });
      row.appendChild(btn);
      row.appendChild(msg);
      return row;
    };

    box.appendChild(mkLoadRow("lhOrgId", "Организация", "org"));
    box.appendChild(mkLoadRow("lhChatId", "Чат", "chat"));

    const orgPrefill = getOrgId(ctx);
    if (orgPrefill) {
      const o = document.getElementById("lhOrgId");
      if (o) {
        o.value = orgPrefill;
      }
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
      "Allowlist: env FLEET_TARGETS_JSON. Hot-plug workers — NATS $SVC.heartbeat.* (всегда слушается).";
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
    meta.textContent =
      "Aggregator: " +
      (data.aggregator_node || "?") +
      " · generated: " +
      (data.generated_at || "?") +
      " · targets: " +
      String(data.components.length);
    wrap.appendChild(meta);
    container.appendChild(wrap);
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

  const HANDLERS = {
    "plugins-l0-wizard": mountL0Wizard,
    "core-legal-hold": mountLegalHold,
    "core-directory-sync": mountDirectorySync,
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
