import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

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

    await uiLogin(page, "smoke_user_a", "smokepass123");

    await expect(page.getByRole("button", { name: new RegExp(title) }).first()).toBeVisible({
      timeout: 30_000,
    });
  });
});
