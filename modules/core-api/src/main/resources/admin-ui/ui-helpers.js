/**
 * Оболочка админ-консоли: группы навигации, заголовки панелей, org-контекст, статус-бейджи.
 */
(function (global) {
  const ORG_LS = "admin_console_org_id";
  let activeSectionId = null;
  let onOrgApply = null;

  function L(key, params) {
    return global.AdminI18n ? global.AdminI18n.t(key, params) : key;
  }

  function LT(text) {
    return global.AdminI18n ? global.AdminI18n.text(text) : text;
  }

  const GROUPS = [
    {
      id: "overview",
      label: "Обзор",
      match: (s) =>
        s.id === "core-server-stats" ||
        s.id === "core-fleet-stats" ||
        s.id === "core-external-stack" ||
        s.id === "core-admin-session" ||
        s.id === "core-admin-manifest" ||
        s.id === "core-purge-status",
    },
    {
      id: "orgs",
      label: "Организации и доступ",
      match: (s) =>
        s.id === "core-organizations" ||
        s.id === "core-user-organization" ||
        s.id === "core-auth-policy" ||
        s.id === "core-directory-sync" ||
        s.id === "core-federation-trust",
    },
    {
      id: "compliance",
      label: "Compliance и аудит",
      match: (s) =>
        s.id === "core-export-compliance" ||
        s.id === "core-audit-events" ||
        s.id === "core-legal-hold",
    },
    {
      id: "data",
      label: "Данные и ретенция",
      match: (s) => s.id === "core-retention",
    },
    {
      id: "security",
      label: "Безопасность и messaging",
      match: (s) => s.id === "core-e2ee-mls" || s.id === "core-read-receipts",
    },
    {
      id: "plugins",
      label: "Интеграции и боты",
      match: (s) => s.id.startsWith("plugins-"),
    },
  ];

  const SECTION_META = {
    "core-server-stats": {
      desc: "Uptime JVM, состояние PostgreSQL, Redis, NATS и счётчики Hot DB (эта нода core-api).",
    },
    "core-fleet-stats": {
      desc: "Fleet snapshot: allowlist HTTP targets + hot-plug NATS + локальный core-api.",
    },
    "core-external-stack": {
      desc: "External stack (023): desired/observed connectors, health, validation и support boundary.",
    },
    "core-export-compliance": {
      desc: "GDPR-экспорт: задачи, политика полноты, smoke-сценарии для compliance.",
    },
    "core-admin-session": {
      desc: "Текущий admin-пользователь и роли из JWT.",
    },
    "core-organizations": {
      desc: "Список организаций, создание и удаление.",
    },
    "core-auth-policy": {
      desc: "Политика входа org: LDAP/OIDC/SAML, локальный пароль, синхронизация Keycloak.",
    },
    "core-directory-sync": {
      desc: "LDAP directory sync: статус последнего прогона и ручной запуск для org.",
      orgScoped: true,
    },
    "core-ip-allowlist": {
      desc: "IP allowlist org: GET/PATCH /admin/orgs/{orgId}/ip-allowlist (lab enforce ORG_IP_ALLOWLIST_ENFORCE).",
      orgScoped: true,
    },
    "core-migration-import": {
      desc: "Migration import jobs (Telegram export scaffold): create, list, process.",
    },
    "core-federation-trust": {
      desc: "Cross-org federation trust registry: list and create partner org trust (spec 022 T02308).",
      orgScoped: true,
    },
    "core-user-organization": {
      desc: "Привязка пользователя к организации (PATCH).",
    },
    "core-audit-events": {
      desc: "Журнал аудита с фильтрами по action, resource_type и resource_id.",
    },
    "core-admin-manifest": {
      desc: "Список разделов, собранный из SPI подключённых модулей.",
    },
    "core-read-receipts": {
      desc: "Статистика read receipts по платформе.",
    },
    "core-e2ee-mls": {
      desc: "Состояние E2EE / MLS: группы, миграции, cipher suites.",
    },
    "core-retention": {
      desc: "Dual-TTL политики для организации или чата (GET / PATCH).",
    },
    "core-legal-hold": {
      desc: "Extended legal hold: legal_hold, legal_hold_files, legal_hold_deep_archive (org / chat).",
    },
    "core-purge-status": {
      desc: "Статус фонового purge / deep-archive worker.",
    },
    "plugins-l0-wizard": {
      desc: "Мастер L0 FAQ-бота: меню, slash-команды, POST /admin/plugins/instances/l0.",
      orgScoped: true,
    },
    "plugins-policies": {
      desc: "Org policy для плагинов: allowlist preset, LLM mode, OCR on-prem.",
      orgScoped: true,
    },
    "plugins-presets": {
      desc: "Каталог platform preset (L0–L3): capabilities и runtime kind.",
    },
    "plugins-instances": {
      desc: "Экземпляры ботов-плагинов org (@bot_name, config, endpoint).",
      orgScoped: true,
    },
  };

  function el(id) {
    return document.getElementById(id);
  }

  function groupFor(section) {
    for (const g of GROUPS) {
      if (g.match(section)) {
        return Object.assign({}, g, { label: LT(g.label) });
      }
    }
    return { id: "other", label: LT("Прочее"), match: () => false };
  }

  function metaFor(section) {
    const meta = SECTION_META[section.id] || { desc: "Данные из Admin API." };
    let desc = LT(meta.desc);
    if (
      desc === meta.desc &&
      global.AdminI18n &&
      global.AdminI18n.getLocale &&
      global.AdminI18n.getLocale() !== "ru"
    ) {
      desc = sectionTitle(section);
    }
    return Object.assign({}, meta, { desc });
  }

  function sectionTitle(section) {
    if (!section) return "";
    return LT(section.title || section.id);
  }

  function getOrgId() {
    const inp = el("globalOrgId");
    const v = inp && inp.value.trim();
    if (v) {
      return v;
    }
    return sessionStorage.getItem(ORG_LS) || "";
  }

  function setOrgId(uuid) {
    if (uuid) {
      sessionStorage.setItem(ORG_LS, uuid);
    } else {
      sessionStorage.removeItem(ORG_LS);
    }
    const inp = el("globalOrgId");
    if (inp) {
      inp.value = uuid || "";
    }
  }

  function statusBadge(ok, label) {
    const span = document.createElement("span");
    span.className = "status-badge " + (ok ? "status-ok" : "status-bad");
    span.textContent = label;
    return span;
  }

  function setAuthenticated(isAuth) {
    const shell = el("appShell");
    const fields = el("authFields");
    const session = el("authSession");
    const navHint = el("navHint");
    if (shell) {
      shell.classList.toggle("is-authenticated", !!isAuth);
    }
    if (fields) {
      fields.hidden = !!isAuth;
    }
    if (session) {
      session.hidden = !isAuth;
    }
    if (navHint) {
      navHint.textContent = isAuth
        ? LT("Выберите раздел. Группы соответствуют областям платформы.")
        : LT("Войдите, чтобы увидеть разделы из подключённых модулей.");
    }
  }

  function setPanelHeader(section) {
    const header = el("panelHeader");
    const title = el("panelTitle");
    const desc = el("panelDesc");
    if (!header || !title) {
      return;
    }
    if (!section) {
      header.hidden = true;
      return;
    }
    header.hidden = false;
    title.textContent = sectionTitle(section);
    if (desc) {
      desc.textContent = metaFor(section).desc;
    }
    updateOrgBar(section);
  }

  function updateOrgBar(section) {
    const bar = el("orgContextBar");
    if (!bar) {
      return;
    }
    const scoped = section && metaFor(section).orgScoped;
    bar.hidden = !scoped;
    if (scoped) {
      const saved = sessionStorage.getItem(ORG_LS);
      const inp = el("globalOrgId");
      if (inp && saved && !inp.value.trim()) {
        inp.value = saved;
      }
    }
  }

  function showJsonBlock(show) {
    const block = el("jsonBlock");
    const pre = el("panelContent");
    if (block) {
      block.hidden = !show;
    }
    if (pre && show) {
      pre.hidden = false;
    }
  }

  function getActiveSectionId() {
    return activeSectionId;
  }

  function renderGroupedNav(sections, onSelect, preserveActiveSectionId) {
    const container = el("sectionList");
    if (!container) {
      return;
    }
    container.innerHTML = "";
    const grouped = new Map();
    sections.forEach((s) => {
      const g = groupFor(s);
      if (!grouped.has(g.id)) {
        grouped.set(g.id, { group: g, items: [] });
      }
      grouped.get(g.id).items.push(s);
    });

    const order = GROUPS.map((g) => g.id);
    const sortedKeys = Array.from(grouped.keys()).sort((a, b) => {
      const ia = order.indexOf(a);
      const ib = order.indexOf(b);
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
    });

    let firstLi = null;
    let preservedLi = null;
    let preservedSection = null;
    sortedKeys.forEach((key) => {
      const { group, items } = grouped.get(key);
      const block = document.createElement("div");
      block.className = "nav-group";
      const cap = document.createElement("p");
      cap.className = "nav-group-label";
      cap.textContent = group.label;
      block.appendChild(cap);
      const ul = document.createElement("ul");
      ul.className = "nav-list";
      items.forEach((s) => {
        const li = document.createElement("li");
        li.dataset.sectionId = s.id;
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "nav-item";
        btn.textContent = sectionTitle(s);
        btn.setAttribute("data-testid", "admin-nav-" + s.id);
        btn.addEventListener("click", () => onSelect(s, li));
        li.appendChild(btn);
        ul.appendChild(li);
        if (!firstLi) {
          firstLi = { s, li };
        }
        if (preserveActiveSectionId && s.id === preserveActiveSectionId) {
          preservedLi = li;
          preservedSection = s;
        }
      });
      block.appendChild(ul);
      container.appendChild(block);
    });

    if (preservedSection && preservedLi) {
      setActiveNav(preservedLi);
      setPanelHeader(preservedSection);
      activeSectionId = preservedSection.id;
    } else if (firstLi) {
      onSelect(firstLi.s, firstLi.li);
    }
  }

  function setActiveNav(liNode) {
    document.querySelectorAll(".nav-list li").forEach((n) => n.classList.remove("active"));
    if (liNode) {
      liNode.classList.add("active");
    }
  }

  function appendPathWithOrg(basePath) {
    const orgId = getOrgId();
    if (!orgId || basePath.indexOf("org_id=") >= 0) {
      return basePath;
    }
    const sep = basePath.indexOf("?") >= 0 ? "&" : "?";
    return basePath + sep + "org_id=" + encodeURIComponent(orgId);
  }

  function appendPluginOrgToolbar(container, onReload) {
    if (document.getElementById("pluginOrgToolbar")) {
      return;
    }
    const row = document.createElement("div");
    row.id = "pluginOrgToolbar";
    row.className = "admin-toolbar card-toolbar";
    const note = document.createElement("p");
    note.className = "toolbar-note muted small";
    note.textContent = LT("Для policies и instances укажите UUID организации (или выберите org в панели сверху).");
    row.appendChild(note);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "btn btn-secondary";
    btn.textContent = LT("Обновить с org_id");
    btn.addEventListener("click", () => {
      Promise.resolve(onReload()).catch(() => {});
    });
    row.appendChild(btn);
    container.insertBefore(row, container.firstChild);
  }

  function initShell() {
    const savedOrg = sessionStorage.getItem(ORG_LS);
    if (savedOrg && el("globalOrgId")) {
      el("globalOrgId").value = savedOrg;
    }

    const btnOrg = el("btnApplyOrg");
    if (btnOrg) {
      btnOrg.addEventListener("click", () => {
        const v = el("globalOrgId") && el("globalOrgId").value.trim();
        setOrgId(v);
        const msg = el("orgContextMsg");
        if (msg) {
          msg.textContent = v ? LT("Org сохранён в сессии.") : LT("Org очищен.");
        }
        if (typeof onOrgApply === "function" && activeSectionId) {
          onOrgApply();
        }
      });
    }

    const btnJson = el("btnToggleJson");
    const pre = el("panelContent");
    if (btnJson && pre) {
      btnJson.addEventListener("click", () => {
        const collapsed = pre.classList.toggle("is-collapsed");
        btnJson.textContent = collapsed ? LT("Развернуть JSON") : LT("Свернуть");
      });
    }
  }

  global.AdminUi = {
    initShell,
    setAuthenticated,
    setPanelHeader,
    renderGroupedNav,
    setActiveNav,
    showJsonBlock,
    statusBadge,
    getOrgId,
    setOrgId,
    appendPathWithOrg,
    appendPluginOrgToolbar,
    metaFor,
    localText: LT,
    t: L,
    getActiveSectionId,
    setActiveSectionId: (id) => {
      activeSectionId = id;
    },
    setOnOrgApply: (fn) => {
      onOrgApply = fn;
    },
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initShell);
  } else {
    initShell();
  }
})(window);
