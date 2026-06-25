#!/usr/bin/env node
/**
 * Smoke: admin UI dynamic strings go through LT/adminFmt, not raw English literals.
 * Locale parity + runtime JSON loading (FR-112, webui pattern).
 */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const adminDir = join(dir, "../../../core-api/src/main/resources/admin-ui");
const localesDir = join(adminDir, "locales");

const adminApp = readFileSync(join(adminDir, "app.js"), "utf8");
const adminPanels = readFileSync(join(adminDir, "panels.js"), "utf8");
const adminI18n = readFileSync(join(adminDir, "admin-i18n.js"), "utf8");
const adminHtml = readFileSync(join(adminDir, "index.html"), "utf8");

const forbidden = [
  { file: "app.js", src: adminApp, pattern: /textContent\s*=\s*"chat_id=/ },
  { file: "app.js", src: adminApp, pattern: /textContent\s*=\s*"status="/ },
  { file: "app.js", src: adminApp, pattern: /textContent\s*=\s*"cancelled="/ },
  { file: "app.js", src: adminApp, pattern: /textContent\s*=\s*"retention OK"/ },
  { file: "panels.js", src: adminPanels, pattern: /textContent\s*=\s*"status="/ },
  { file: "panels.js", src: adminPanels, pattern: /\|\|\s*"details"/ },
  { file: "panels.js", src: adminPanels, pattern: /:\s*"No validation failures\/warnings\."/ },
];

for (const check of forbidden) {
  if (check.pattern.test(check.src)) {
    throw new Error(`${check.file} still has untranslated literal: ${check.pattern}`);
  }
}

if (!adminApp.includes("function adminFmt")) {
  throw new Error("admin app.js missing adminFmt helper");
}
if (!adminPanels.includes("function adminFmt")) {
  throw new Error("admin panels.js missing adminFmt helper");
}

if (adminHtml.includes("locales/") && /\.js["']/.test(adminHtml)) {
  throw new Error("admin index.html must not load locale .js bundles");
}
if (!adminI18n.includes('fetchJson("locales/manifest.json")')) {
  throw new Error("admin-i18n.js must load locales/manifest.json at runtime");
}
if (!/locales\/["']?\s*\+\s*encodeURIComponent\(next\)\s*\+\s*["']?\.json/.test(adminI18n)) {
  throw new Error("admin-i18n.js must fetch single locale JSON by code");
}
if (!adminI18n.includes("prefetchDefaultLocale")) {
  throw new Error("admin-i18n.js must background-prefetch default locale");
}
if (
  adminI18n.includes(
    "return loadLocale(DEFAULT_LOCALE)\n      .then(function () {\n        return next === DEFAULT_LOCALE ? null : loadLocale(next);"
  )
) {
  throw new Error("admin setLocale must not block on default locale before target");
}

const DEFAULT_LOCALE = "ru";
const LOCALE_ORDER = ["ru", "en", "be", "kk", "zh", "ko"];

function leafKeys(obj, prefix) {
  prefix = prefix || "";
  let keys = [];
  if (!obj || typeof obj !== "object") return keys;
  Object.keys(obj).forEach(function (k) {
    const full = prefix ? prefix + "." + k : k;
    if (typeof obj[k] === "string") keys.push(full);
    else keys = keys.concat(leafKeys(obj[k], full));
  });
  return keys;
}

const refKeys = leafKeys(JSON.parse(readFileSync(join(localesDir, DEFAULT_LOCALE + ".json"), "utf8"))).sort();
for (const code of LOCALE_ORDER) {
  const file = join(localesDir, code + ".json");
  if (!existsSync(file)) {
    throw new Error("admin locale missing: " + code);
  }
  const keys = new Set(leafKeys(JSON.parse(readFileSync(file, "utf8"))));
  const missing = refKeys.filter((k) => !keys.has(k));
  if (missing.length) {
    throw new Error(`${code}.json missing ${missing.length} keys (first: ${missing[0]})`);
  }
}

const manifest = JSON.parse(readFileSync(join(localesDir, "manifest.json"), "utf8"));
if (manifest.keyCount !== refKeys.length) {
  throw new Error(
    `admin manifest keyCount=${manifest.keyCount} expected ${refKeys.length} (run build:admin-manifest)`
  );
}

console.log("admin-i18n smoke OK (" + refKeys.length + " keys, " + LOCALE_ORDER.length + " locales)");
