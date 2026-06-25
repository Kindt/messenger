import { expect, Page, test } from "@playwright/test";
import { ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

async function forceRuLocale(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("korus_web_locale", "ru");
  });
}

async function openSettingsTab(page: Page, tabId: string): Promise<void> {
  await page.getByTestId("settings-toggle").click();
  await expect(page.locator(".settings-card")).toBeVisible({ timeout: 15_000 });
  await page.getByTestId(`settings-tab-${tabId}`).click();
  await expect(page.locator(`#settings-panel-${tabId}`)).toBeVisible({ timeout: 10_000 });
}

test.describe("settings cross-tab IA", () => {
  test.setTimeout(90_000);

  test("DND duration lives under notifications; profile shows cross-tab hint", async ({
    page,
    request,
  }) => {
    await forceRuLocale(page);
    await ensureSmokeUsers(request);
    await uiLogin(page, "smoke_user_a", "smokepass123");

    await openSettingsTab(page, "profile");
    await page.getByTestId("settings-presence").selectOption("dnd");
    await expect(page.getByTestId("settings-dnd-profile-hint")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("settings-dnd-duration")).toHaveCount(0);

    await page.getByTestId("settings-tab-notifications").click();
    await expect(page.locator("#settings-panel-notifications")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("settings-dnd-duration")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("settings-reminders")).toBeVisible();
    await expect(page.getByTestId("settings-scheduled")).toBeVisible();
  });
});
