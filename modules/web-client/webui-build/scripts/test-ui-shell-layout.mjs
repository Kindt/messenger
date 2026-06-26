import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const brandingSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-branding.js"),
  "utf8"
);

const sandbox = {
  window: {},
  globalThis: {},
  document: {
    documentElement: {
      _attrs: new Map(),
      setAttribute(k, v) {
        this._attrs.set(k, v);
      },
      getAttribute(k) {
        return this._attrs.get(k) || null;
      },
    },
    head: { appendChild() {} },
    getElementById() {
      return null;
    },
    querySelector() {
      return null;
    },
  },
  getComputedStyle() {
    return { getPropertyValue() { return "#7949f4"; } };
  },
  sessionStorage: {
    _data: new Map(),
    getItem(k) {
      return this._data.has(k) ? this._data.get(k) : null;
    },
    setItem(k, v) {
      this._data.set(k, v);
    },
    removeItem(k) {
      this._data.delete(k);
    },
  },
  navigator: { serviceWorker: null },
};

sandbox.globalThis = sandbox.window;
vm.runInNewContext(brandingSrc, sandbox);

const branding = sandbox.window.KorusUiBranding;
if (!branding) throw new Error("KorusUiBranding missing");

const cfg = branding.normalizeConfig({
  shell_layout: "auth-split",
  palette: "korus",
});
if (cfg.auth_layout !== "auth-split" || cfg.post_login_layout !== "default") {
  throw new Error("auth-split mapping failed");
}

const compact = branding.normalizeConfig({ shell_layout: "compact", palette: "korus" });
if (compact.auth_layout !== "default" || compact.post_login_layout !== "compact") {
  throw new Error("compact mapping failed");
}

branding.applyShellLayout(sandbox.document, cfg, { postLogin: false });
if (sandbox.document.documentElement.getAttribute("data-shell-layout") !== "auth-split") {
  throw new Error("auth layout not applied");
}

branding.applyShellLayout(sandbox.document, cfg, { postLogin: true });
if (sandbox.document.documentElement.getAttribute("data-shell-layout") !== "default") {
  throw new Error("post-login layout should revert auth-split to default");
}

console.log("OK test-ui-shell-layout");
