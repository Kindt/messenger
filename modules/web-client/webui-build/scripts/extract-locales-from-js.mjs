#!/usr/bin/env node
/**
 * One-time / maintenance: convert legacy webui/locales/*.js to messages/*.json source.
 */
import fs from "fs";
import path from "path";
import vm from "vm";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const legacyDir = path.resolve(root, "../src/main/resources/webui/locales");
const outDir = path.resolve(root, "locales/messages");
const codes = ["ru", "en", "be", "kk", "zh", "ko"];

function loadLegacyJs(code) {
  const file = path.join(legacyDir, code + ".js");
  if (!fs.existsSync(file)) {
    throw new Error("Missing legacy locale: " + file);
  }
  const src = fs.readFileSync(file, "utf8");
  const sandbox = { KorusLocales: {} };
  sandbox.global = sandbox;
  sandbox.window = sandbox;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(src, sandbox, { filename: file });
  const bundle = sandbox.KorusLocales[code];
  if (!bundle) throw new Error("No bundle for " + code + " in " + file);
  return bundle;
}

fs.mkdirSync(outDir, { recursive: true });
codes.forEach(function (code) {
  const bundle = loadLegacyJs(code);
  const out = path.join(outDir, code + ".json");
  fs.writeFileSync(out, JSON.stringify(bundle, null, 2) + "\n", "utf8");
  console.log("wrote " + out);
});
