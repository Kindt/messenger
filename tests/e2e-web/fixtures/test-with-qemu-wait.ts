import { test as base, expect } from "@playwright/test";
import { attachConsoleGuard } from "./console-guard";
import { waitForQemuStackReady } from "./qemu-stack-wait";

export const test = base.extend({
  page: async ({ page }, use, testInfo) => {
    const guard = attachConsoleGuard(page);
    await use(page);
    guard.assertClean(testInfo.title);
    guard.detach();
  },
});

test.beforeEach(async () => {
  await waitForQemuStackReady();
});

export { expect } from "@playwright/test";
export { attachConsoleGuard, assertAllConsoleGuardsClean } from "./console-guard";
