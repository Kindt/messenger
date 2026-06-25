#!/usr/bin/env node
/**
 * Sync admin-ui locales/manifest.json keyCount from reference locale (FR-112).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localesDir = path.resolve(
  __dirname,
  "../../../core-api/src/main/resources/admin-ui/locales"
);
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

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const refFile = path.join(localesDir, DEFAULT_LOCALE + ".json");
if (!fs.existsSync(refFile)) {
  console.error("Missing reference locale: " + refFile);
  process.exit(1);
}

const refKeys = leafKeys(readJson(refFile)).sort();
let failed = false;

LOCALE_ORDER.forEach(function (code) {
  const file = path.join(localesDir, code + ".json");
  if (!fs.existsSync(file)) {
    console.error(code + ": missing file");
    failed = true;
    return;
  }
  const keys = new Set(leafKeys(readJson(file)));
  const missing = refKeys.filter(function (k) {
    return !keys.has(k);
  });
  if (missing.length) {
    failed = true;
    console.error(code + ": missing " + missing.length + " keys");
    missing.slice(0, 5).forEach(function (k) {
      console.error("  - " + k);
    });
  }
});

if (failed) process.exit(1);

const manifestPath = path.join(localesDir, "manifest.json");
const manifest = readJson(manifestPath);
manifest.default = DEFAULT_LOCALE;
manifest.codes = LOCALE_ORDER;
manifest.keyCount = refKeys.length;

fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n", "utf8");
console.log(
  "OK admin manifest.json (" + LOCALE_ORDER.length + " locales, " + refKeys.length + " keys)"
);
