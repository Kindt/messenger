/**
 * Lab-scope first-load budget gate (spec 025 FR-175 / SC-024).
 * Measures critical-path static assets from index.html — no live stack required.
 */
import { readFileSync, statSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const webuiRoot = join(dir, "../../src/main/resources/webui");

/** Raw bytes budget for HTML + blocking CSS + app.bundle.js (excludes deferred MLS WASM). */
const MAX_CRITICAL_BYTES = Number(process.env.KORUS_FIRST_LOAD_MAX_KB || 450) * 1024;

const CRITICAL = [
  "index.html",
  "fonts.css",
  "tailwind.css",
  "styles.css",
  "themes.css",
  "app.bundle.js",
];

let total = 0;
const breakdown = [];

for (const name of CRITICAL) {
  const path = join(webuiRoot, name);
  if (!existsSync(path)) {
    console.error(`[first-load] missing asset: ${name} — run npm run build:assets`);
    process.exit(1);
  }
  const size = statSync(path).size;
  total += size;
  breakdown.push({ name, kb: Math.round((size / 1024) * 10) / 10 });
}

const totalKb = Math.round((total / 1024) * 10) / 10;
const maxKb = Math.round((MAX_CRITICAL_BYTES / 1024) * 10) / 10;

console.log("[first-load] critical path (lab static):");
for (const row of breakdown) {
  console.log(`  ${row.name}: ${row.kb} KB`);
}
console.log(`  total: ${totalKb} KB (budget ${maxKb} KB)`);

if (total > MAX_CRITICAL_BYTES) {
  console.error(
    `[first-load] FAIL: ${totalKb} KB exceeds budget ${maxKb} KB — trim bundle/CSS or raise KORUS_FIRST_LOAD_MAX_KB`
  );
  process.exit(1);
}

console.log("[first-load] PASS");
