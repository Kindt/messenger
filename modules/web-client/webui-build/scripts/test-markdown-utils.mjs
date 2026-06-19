/**
 * Smoke tests for ui-markdown-utils.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-markdown-utils.js"),
  "utf8"
);
const sandbox = { window: {}, globalThis: {} };
vm.runInNewContext(src, sandbox);
const md = sandbox.window.KorusUiMarkdownUtils;

if (!md) {
  throw new Error("KorusUiMarkdownUtils missing");
}

const html = md.safeMarkdown("**bold** and `code`");
if (!html.includes("<strong>bold</strong>") || !html.includes("<code>code</code>")) {
  throw new Error("safeMarkdown failed: " + html);
}
if (md.safeMarkdown("<script>") !== "&lt;script&gt;") {
  throw new Error("escape via safeMarkdown");
}

console.log("ui-markdown-utils smoke OK");
