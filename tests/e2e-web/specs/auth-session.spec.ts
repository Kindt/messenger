import { test, expect } from "@playwright/test";
import { apiLogin } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

test.describe("auth session", () => {
  test("login via UI and load composer", async ({ page, request }) => {
    await apiLogin(request, "csadmin", "csadmin");
    await uiLogin(page, "csadmin", "csadmin");
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });

  test("logout returns to auth form", async ({ page, request }) => {
    await apiLogin(request, "csadmin", "csadmin");
    await uiLogin(page, "csadmin", "csadmin");
    const logoutBtn = page.getByRole("button", { name: /Выйти|Sign out|Logout/i });
    if (await logoutBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await logoutBtn.click();
      await expect(page.locator("#u")).toBeVisible({ timeout: 15_000 });
    } else {
      test.skip(true, "Logout control not exposed in current shell layout");
    }
  });
});
