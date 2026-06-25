/**
 * esbuild reference config for webui (FR-040).
 * Production entry: scripts/build-bundle.mjs (bundle-script-order.mjs).
 */
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webuiDir = path.resolve(__dirname, "../src/main/resources/webui");

/** @type {import('esbuild').BuildOptions} */
export default {
  entryPoints: [path.join(webuiDir, "app.js")],
  bundle: true,
  format: "iife",
  globalName: "KorusWebUi",
  minify: true,
  target: "es2020",
  outfile: path.join(webuiDir, "app.bundle.js"),
  legalComments: "none",
  logLevel: "info",
};
