import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const shellSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-shell-utils.js"),
  "utf8"
);
const brandingSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-branding.js"),
  "utf8"
);

const sandbox = {
  window: {},
  globalThis: {},
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
vm.runInNewContext(shellSrc, sandbox);
vm.runInNewContext(brandingSrc, sandbox);

const branding = sandbox.window.KorusUiBranding;
if (!branding) throw new Error("KorusUiBranding missing");

const platformDefault = {
  org_id: "995dcc4d-3fd2-4ffc-8bd7-04e14d75a4e2",
  palette: "korus",
  token_overrides: {},
  custom_css: "",
  brand_title: null,
  logo_url: null,
};

if (!branding.isPlatformDefaultBranding(platformDefault)) {
  throw new Error("expected platform-default branding");
}

if (branding.isPlatformDefaultBranding({ palette: "vtb", token_overrides: {} })) {
  throw new Error("non-korus palette must not be platform default");
}

if (
  branding.isPlatformDefaultBranding({
    palette: "korus",
    token_overrides: { "--accent": "#000" },
  })
) {
  throw new Error("token overrides must block platform default");
}

if (branding.resolveMergedPalette(platformDefault, "vtb") !== "vtb") {
  throw new Error("demo palette should persist on platform default");
}

if (branding.resolveMergedPalette({ palette: "alfa", token_overrides: {} }, "vtb") !== "alfa") {
  throw new Error("server palette must win when org/platform overrides");
}

if (
  branding.isPlatformDefaultBranding({
    palette: "korus",
    token_overrides: {},
    brand_title: "Acme",
  })
) {
  throw new Error("brand_title must block platform default");
}

if (
  branding.isPlatformDefaultBranding({
    palette: "korus",
    token_overrides: {},
    logo_url: "/logo.svg",
  })
) {
  throw new Error("logo_url must block platform default");
}

if (
  branding.isPlatformDefaultBranding({
    palette: "korus",
    token_overrides: {},
    custom_css: ".x{color:red}",
  })
) {
  throw new Error("custom_css must block platform default");
}

if (branding.resolveMergedPalette(platformDefault, "korus") !== "korus") {
  throw new Error("korus demo palette must not override platform default");
}

if (
  branding.resolveMergedPalette(
    { palette: "korus", token_overrides: {}, brand_title: "Org" },
    "vtb"
  ) !== "korus"
) {
  throw new Error("server branding must win over demo when brand_title set");
}

console.log("test-ui-branding: OK");
