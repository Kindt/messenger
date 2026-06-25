#!/usr/bin/env node
/**
 * Production JS bundle via esbuild (FR-040 / T073).
 * Source order: bundle-script-order.mjs (index.html loads app.bundle.js only).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import * as esbuild from "esbuild";
import { BUNDLE_SCRIPTS } from "./bundle-script-order.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const webuiDir = path.resolve(root, "../src/main/resources/webui");
const outFile = path.join(webuiDir, "app.bundle.js");

const scripts = BUNDLE_SCRIPTS;
if (!scripts.length) {
  console.error("BUNDLE_SCRIPTS is empty");
  process.exit(1);
}

let combined = "";
for (const rel of scripts) {
  const file = path.join(webuiDir, rel);
  if (!fs.existsSync(file)) {
    console.error("Missing script for bundle: " + file);
    process.exit(1);
  }
  combined += fs.readFileSync(file, "utf8") + "\n;\n";
}

const result = await esbuild.transform(combined, {
  minify: true,
  target: "es2020",
  legalComments: "none",
});

const banner =
  "/* Korus webui production bundle (FR-040/T073). Modules: " +
  scripts.length +
  ". Heavy MLS WASM loaded separately — see index.html */\n";

fs.writeFileSync(outFile, banner + result.code, "utf8");

const kb = (result.code.length / 1024).toFixed(1);
console.log(
  "OK app.bundle.js (" +
    kb +
    " KB, " +
    scripts.length +
    " files) -> " +
    outFile
);
