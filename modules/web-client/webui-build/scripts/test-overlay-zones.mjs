/**
 * Smoke tests for modal overlay layout zones (client + admin KPI).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const appJs = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);
const pollsJs = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-polls.js"),
  "utf8"
);
const phase5Js = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-phase5-ext.js"),
  "utf8"
);
const callAdrJs = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-call-adr.js"),
  "utf8"
);
const adminAppJs = readFileSync(
  join(dir, "../../../core-api/src/main/resources/admin-ui/app.js"),
  "utf8"
);

const checks = [
  { file: "app.js", src: appJs, needle: "function modalCardHead" },
  { file: "app.js", src: appJs, needle: '"read-receipt-overlay"' },
  { file: "app.js", src: appJs, needle: "forward-card-content" },
  { file: "app.js", src: appJs, needle: "function memberRoleLabel" },
  { file: "app.js", src: appJs, needle: "function closeReadReceiptPopup" },
  { file: "ui-polls.js", src: pollsJs, needle: "function modalCardHead" },
  { file: "ui-polls.js", src: pollsJs, needle: "settings-content forward-card-content" },
  { file: "ui-phase5-ext.js", src: phase5Js, needle: "phase5-overlay-content" },
  { file: "ui-call-adr.js", src: callAdrJs, needle: "phase5-overlay-content" },
  { file: "admin app.js", src: adminAppJs, needle: "function renderAdminKpiCards" },
];

for (const check of checks) {
  if (!check.src.includes(check.needle)) {
    throw new Error(`${check.file} missing overlay marker: ${check.needle}`);
  }
}

if (/window\.alert\(L\("readReceipts/.test(appJs)) {
  throw new Error("read receipts must use overlay modal, not window.alert");
}

console.log("overlay-zones smoke OK");
