import { expect, test } from "@playwright/test";
import { ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

/** Minimal avatar UI smoke (spec 068) — settings avatar row + sidebar initials/img. */
test.describe("UI avatar smoke", () => {
  test("settings shows profile avatar control after login", async ({ page, request }) => {
    await page.addInitScript(() => {
      localStorage.setItem("korus_web_locale", "ru");
    });
    await ensureSmokeUsers(request);
    await uiLogin(page, "smoke_user_a", "smokepass123");
    await page.getByTestId("settings-toggle").click();
    await expect(page.locator(".settings-card")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("settings-tab-profile").click();
    await expect(page.locator("#settings-panel-profile")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId("settings-profile-avatar")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("settings-avatar-change")).toBeVisible();
  });

  test("chat list rows expose avatar test ids", async ({ page, request }) => {
    await page.addInitScript(() => {
      localStorage.setItem("korus_web_locale", "ru");
    });
    await ensureSmokeUsers(request);
    await uiLogin(page, "smoke_user_a", "smokepass123");
    const avatar = page.locator("[data-testid^='chat-row-avatar']").first();
    await expect(avatar).toBeVisible({ timeout: 20_000 });
  });
});
