#!/usr/bin/env node
/**
 * Minify legacy component CSS into webui/styles.css (FR-042).
 * Source: webui-build/src/styles.css (same pattern as Tailwind input.css).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { transform } from "lightningcss";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const srcPath = path.join(root, "src/styles.css");
const outPath = path.resolve(root, "../src/main/resources/webui/styles.css");

if (!fs.existsSync(srcPath)) {
  console.error("Missing styles source: " + srcPath);
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
console.log("OK styles.css (" + kb + " KB minified) -> " + outPath);
