import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-shell-utils.js"),
  "utf8"
);

const storage = new Map();
const sandbox = {
  window: {
    localStorage: {
      getItem(k) {
        return storage.has(k) ? storage.get(k) : null;
      },
      setItem(k, v) {
        storage.set(k, v);
      },
      removeItem(k) {
        storage.delete(k);
      },
    },
  },
  globalThis: {},
  getComputedStyle() {
    return { getPropertyValue() { return ""; } };
  },
};
sandbox.globalThis = sandbox.window;
sandbox.localStorage = sandbox.window.localStorage;

vm.runInNewContext(src, sandbox);
const utils = sandbox.window.KorusUiShellUtils;
if (!utils) throw new Error("KorusUiShellUtils missing");

const styleKey = "korus_web_style";
const themeKey = "korus_web_theme";

utils.saveStyleSet(styleKey, themeKey, "light", "alfa");
const loaded = utils.loadStyleSet(styleKey, themeKey, "korus");
if (loaded.appearance !== "light" || loaded.palette !== "alfa") {
  throw new Error("palette not persisted: " + JSON.stringify(loaded));
}

const doc = {
  documentElement: {
    attributes: {},
    style: { colorScheme: "", length: 0 },
    setAttribute(name, value) {
      this.attributes[name] = value;
    },
    removeAttribute(name) {
      delete this.attributes[name];
    },
  },
  querySelector() {
    return null;
  },
};

const applied = utils.applyStyleSet(doc, loaded);
if (applied.palette !== "alfa" || doc.documentElement.attributes["data-palette"] !== "alfa") {
  throw new Error("applyStyleSet failed");
}

console.log("test-ui-shell-utils: OK");
