import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

test.describe("auth session", () => {
  test("login via UI and load composer", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "csadmin", "csadmin");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-auth-${Date.now()}`;
    await apiCreateGroup(request, token, title, [idB]);
    await uiLogin(page, "csadmin", "csadmin");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });

  test("logout returns to auth form", async ({ page, request }) => {
    await apiLogin(request, "csadmin", "csadmin");
    await uiLogin(page, "csadmin", "csadmin");
    const logoutBtn = page.locator("[data-testid=logout]");
    await expect(logoutBtn).toBeVisible({ timeout: 5_000 });
    await logoutBtn.click();
    await expect(page.locator("#u")).toBeVisible({ timeout: 15_000 });
  });

  test("locale switch in settings keeps auth form usable", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    await uiLogin(page, "csadmin", "csadmin");
    await page.locator("[data-testid=settings-toggle]").click();
    await expect(page.locator(".settings-overlay")).toBeVisible();
    const clickLocale = async (code: string) => {
      await page.locator(`[data-testid=locale-${code}]`).evaluate((el) => {
        (el as HTMLButtonElement).click();
      });
    };
    await clickLocale("ru");
    await expect(page.locator("html")).toHaveAttribute("lang", "ru", { timeout: 10_000 });
    await clickLocale("en");
    await expect(page.locator("html")).toHaveAttribute("lang", "en", { timeout: 10_000 });
    await page.keyboard.press("Escape");
    await expect(page.locator(".settings-overlay")).toHaveCount(0, { timeout: 5_000 });
    await page.locator("[data-testid=logout]").click();
    await expect(page.locator("#u")).toBeVisible({ timeout: 15_000 });
    await expect(page.locator("[data-testid=auth-submit]")).toBeVisible();
    await page.locator("#u").fill("csadmin");
    await page.locator("#p").fill("csadmin");
    await page.locator("[data-testid=auth-submit]").click();
    await expect(page.locator("[data-testid=logout]")).toBeVisible({ timeout: 30_000 });
  });
});
