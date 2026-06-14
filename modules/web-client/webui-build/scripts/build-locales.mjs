#!/usr/bin/env node
/**
 * Copy locale JSON source -> webui/locales/*.json + manifest.json
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const srcDir = path.resolve(root, "locales/messages");
const outDir = path.resolve(root, "../src/main/resources/webui/locales");

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

fs.mkdirSync(outDir, { recursive: true });

const refFile = path.join(srcDir, DEFAULT_LOCALE + ".json");
if (!fs.existsSync(refFile)) {
  console.error("Missing reference: " + refFile);
  process.exit(1);
}
const refKeys = leafKeys(readJson(refFile)).sort();
let failed = false;

LOCALE_ORDER.forEach(function (code) {
  const src = path.join(srcDir, code + ".json");
  if (!fs.existsSync(src)) {
    console.error("Missing source locale: " + src);
    failed = true;
    return;
  }
  const bundle = readJson(src);
  const keys = new Set(leafKeys(bundle));
  const missing = refKeys.filter(function (k) {
    return !keys.has(k);
  });
  if (missing.length) {
    failed = true;
    console.error(code + ": missing " + missing.length + " keys (ref " + DEFAULT_LOCALE + ")");
    missing.slice(0, 5).forEach(function (k) {
      console.error("  - " + k);
    });
    return;
  }
  const dest = path.join(outDir, code + ".json");
  fs.copyFileSync(src, dest);
  console.log("OK " + code + " -> " + dest);
});

if (failed) process.exit(1);

const manifest = {
  default: DEFAULT_LOCALE,
  codes: LOCALE_ORDER,
};
fs.writeFileSync(
  path.join(outDir, "manifest.json"),
  JSON.stringify(manifest, null, 2) + "\n",
  "utf8"
);
console.log("OK manifest.json (" + LOCALE_ORDER.length + " locales, " + refKeys.length + " keys)");
