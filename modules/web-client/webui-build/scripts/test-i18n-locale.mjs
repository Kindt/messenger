/**
 * Smoke: dynamic single-locale loading (FR-041) — fetch JSON at runtime, not all bundles upfront.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const i18n = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-i18n.js"),
  "utf8"
);
const html = readFileSync(
  join(dir, "../../src/main/resources/webui/index.html"),
  "utf8"
);

if (html.includes("locales/") && /\.js["']/.test(html)) {
  throw new Error("index.html must not load locale .js bundles");
}
if (!i18n.includes('fetchJson("/locales/manifest.json")')) {
  throw new Error("ui-i18n.js must load manifest.json at runtime");
}
if (!i18n.includes('"/locales/" + encodeURIComponent(code) + ".json"')) {
  throw new Error("ui-i18n.js must fetch single locale JSON by code");
}
if (!i18n.includes("prefetchDefaultLocale")) {
  throw new Error("ui-i18n.js must background-prefetch default locale only when needed");
}
if (i18n.includes("loadLocale(DEFAULT_LOCALE)\n      .then(function () {\n        if (next !== DEFAULT_LOCALE) return loadLocale(next);")) {
  throw new Error("setLocale must not block on default locale before target");
}

console.log("i18n dynamic locale smoke OK");
