import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const webuiDir = join(here, "../../src/main/resources/webui/e2ee/openmls");
const required = ["korus-openmls-dev.js", "README.md"];

for (const name of required) {
  const path = join(webuiDir, name);
  if (!existsSync(path)) {
    console.error("[build:openmls] missing", path);
    process.exit(1);
  }
  console.log("[build:openmls] OK", name);
}
