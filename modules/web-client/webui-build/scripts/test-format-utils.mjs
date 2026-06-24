import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-format-utils.js"),
  "utf8"
);
const appSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);

const sandbox = {
  window: {},
  globalThis: {},
};
sandbox.window.KorusI18n = {
  getLocale: () => "ru",
  t: (key) => key,
};
sandbox.globalThis = sandbox.window;
vm.runInNewContext(src, sandbox);

const utils = sandbox.window.KorusUiFormatUtils;
if (!utils) {
  throw new Error("KorusUiFormatUtils missing");
}

const seconds = 1782252311.403022;
const millis = utils.instantEpochMs(seconds);
if (millis < 1000000000000) {
  throw new Error("epoch seconds were not converted to milliseconds: " + millis);
}

const label = utils.formatInstantLabel(seconds);
if (/1970/.test(label)) {
  throw new Error("numeric epoch seconds formatted as 1970: " + label);
}

const isoLabel = utils.formatInstantLabel("2026-06-23T22:05:11Z");
if (!isoLabel || isoLabel === "—") {
  throw new Error("ISO instant label failed");
}

for (const forbidden of [
  "new Date(c.created_at).getTime()",
  "new Date(m.created_at).getTime()",
  "new Date(last.created_at).getTime()",
  "new Date(data.createdAt).toISOString()",
  "Date.parse(m.created_at)",
]) {
  if (appSrc.includes(forbidden)) {
    throw new Error("app.js must use instantEpochMs for API created_at: " + forbidden);
  }
}

console.log("ui-format-utils smoke OK");
