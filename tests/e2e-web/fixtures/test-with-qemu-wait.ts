import { test as base } from "@playwright/test";
import { waitForQemuStackReady } from "./qemu-stack-wait";

export const test = base;

test.beforeEach(async () => {
  await waitForQemuStackReady();
});

export { expect } from "@playwright/test";
