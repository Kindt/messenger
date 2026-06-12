import { defineConfig, devices } from "@playwright/test";

// QEMU host forwards: web :19088, API :18080. Local compose defaults :9088/:8080.
// Scripts MUST set KORUS_WEB_URL / PLAYWRIGHT_BASE_URL / KORUS_API_URL; do not rely on :9088 in CI/QEMU.
const webBase =
  process.env.KORUS_WEB_URL ||
  process.env.PLAYWRIGHT_BASE_URL ||
  "http://127.0.0.1:19088";
const apiBase = process.env.KORUS_API_URL || "http://127.0.0.1:18080";

export default defineConfig({
  testDir: "./specs",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: process.env.CI ? undefined : 1,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: webBase,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  metadata: { apiBase },
});
