/**
 * Smoke: admin UI dynamic strings go through LT/adminFmt, not raw English literals.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const adminApp = readFileSync(
  join(dir, "../../../core-api/src/main/resources/admin-ui/app.js"),
  "utf8"
);
const adminPanels = readFileSync(
  join(dir, "../../../core-api/src/main/resources/admin-ui/panels.js"),
  "utf8"
);

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

console.log("admin-i18n smoke OK");
