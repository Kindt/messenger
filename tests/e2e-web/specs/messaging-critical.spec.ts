import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";

test.describe("messaging critical path", () => {
  test("login via UI, send message in group chat", async ({ page, request }) => {
    await ensureSmokeUsers(request);

    const tokenA = await apiLogin(request, "csadmin", "csadmin");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const groupTitle = `e2e-playwright-${Date.now()}`;
    await apiCreateGroup(request, tokenA, groupTitle, [idB]);

    await page.goto("/");
    await page.locator("#u").fill("csadmin");
    await page.locator("#p").fill("csadmin");
    await page.getByRole("button", { name: "Войти" }).click();

    await expect(page.getByPlaceholder("Сообщение… (Shift+Enter — строка, перетащите файл)")).toBeVisible({
      timeout: 30_000,
    });

    const chatButton = page.getByRole("button", { name: new RegExp(groupTitle) });
    await chatButton.first().click({ timeout: 15_000 });

    const marker = `playwright-msg-${Date.now()}`;
    const textarea = page.getByPlaceholder("Сообщение… (Shift+Enter — строка, перетащите файл)");
    await textarea.fill(marker);
    await textarea.press("Enter");

    await expect(page.getByText(marker)).toBeVisible({ timeout: 20_000 });
  });
});
