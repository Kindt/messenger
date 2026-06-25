#!/usr/bin/env node
/**
 * Minify admin-ui styles.css (FR-112).
 * Source: core-api/admin-ui-build/src/styles.css
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { transform } from "lightningcss";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const srcPath = path.resolve(
  __dirname,
  "../../../core-api/admin-ui-build/src/styles.css"
);
const outPath = path.resolve(
  __dirname,
  "../../../core-api/src/main/resources/admin-ui/styles.css"
);

if (!fs.existsSync(srcPath)) {
  console.error("Missing admin styles source: " + srcPath);
  process.exit(1);
}

const source = fs.readFileSync(srcPath, "utf8");
const { code } = transform({
  filename: "styles.css",
  code: Buffer.from(source),
  minify: true,
  sourceMap: false,
});

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, code, "utf8");

const kb = (code.length / 1024).toFixed(1);
console.log("OK admin styles.css (" + kb + " KB minified) -> " + outPath);
