import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";

test.describe("messaging group (3 users)", () => {
  test("API setup group; user A sends; titles visible in list", async ({ page, request }) => {
    await ensureSmokeUsers(request);

    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const tokenC = await apiLogin(request, "smoke_user_c", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const idC = await apiMeId(request, tokenC);
    const title = `e2e-group-3u-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB, idC]);

    await page.goto("/");
    await page.locator("#u").fill("smoke_user_a");
    await page.locator("#p").fill("smokepass123");
    await page.getByRole("button", { name: "Войти" }).click();

    await expect(page.getByRole("button", { name: new RegExp(title) }).first()).toBeVisible({
      timeout: 30_000,
    });
  });
});
