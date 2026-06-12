import { test, expect } from "@playwright/test";
import { apiBase, apiEnsureGroupWithMessage } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

test.describe("e2ee browser MLS roundtrip", () => {
  test("UI send uses client encrypt when mls active", async ({ page, request }) => {
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(capsRes.ok()).toBeTruthy();
    const caps = await capsRes.json();
    const mlsStatus = caps.mls_status || caps.mlsStatus || "";
    if (mlsStatus !== "active") {
      test.skip();
      return;
    }

    const { title } = await apiEnsureGroupWithMessage(request, "e2e-mls-ui");
    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);

    const probe = `mls-ui-${Date.now()}`;
    const composer = page.locator("[data-testid=message-composer]");
    const articles = page.locator("article");
    const before = await articles.count();
    await composer.fill(probe);
    await composer.press("Enter");
    await expect(composer).toHaveValue("", { timeout: 10_000 });
    await expect(articles).toHaveCount(before + 1, { timeout: 30_000 });
    await expect(
      articles.last().locator(".msg-body, .msg-e2ee-body, .msg-type-e2ee").first()
    ).toBeVisible({ timeout: 10_000 });
  });
});
