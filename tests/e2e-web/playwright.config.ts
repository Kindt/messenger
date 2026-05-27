import { defineConfig, devices } from "@playwright/test";

const webBase = process.env.KORUS_WEB_URL || "http://127.0.0.1:9088";
const apiBase = process.env.KORUS_API_URL || "http://127.0.0.1:8080";

export default defineConfig({
  testDir: "./specs",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: webBase,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  metadata: { apiBase },
});
