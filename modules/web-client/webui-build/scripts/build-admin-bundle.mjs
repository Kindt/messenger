#!/usr/bin/env node
/**
 * Production JS bundle for admin-ui (FR-112).
 * Concatenates index.html script order; runtime switch is optional follow-up.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import * as esbuild from "esbuild";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const adminDir = path.resolve(
  __dirname,
  "../../../core-api/src/main/resources/admin-ui"
);
const indexHtml = path.join(adminDir, "index.html");
const outFile = path.join(adminDir, "admin.bundle.js");

function scriptOrderFromIndex(html) {
  const order = [];
  const re = /<script src="([^"]+\.js)"><\/script>/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    order.push(m[1]);
  }
  return order;
}

const html = fs.readFileSync(indexHtml, "utf8");
const scripts = scriptOrderFromIndex(html);
if (!scripts.length) {
  console.error("No script tags found in admin index.html");
  process.exit(1);
}

let combined = "";
for (const rel of scripts) {
  const file = path.join(adminDir, rel);
  if (!fs.existsSync(file)) {
    console.error("Missing admin script for bundle: " + file);
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
  "/* Korus admin-ui production bundle (FR-112). Scripts: " +
  scripts.length +
  " modules. Wire in index.html when ready. */\n";

fs.writeFileSync(outFile, banner + result.code, "utf8");

const kb = (result.code.length / 1024).toFixed(1);
console.log(
  "OK admin.bundle.js (" + kb + " KB, " + scripts.length + " files) -> " + outFile
);
