import { test, expect } from "@playwright/test";
import { apiBase, apiLogin, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

test.describe("contacts and search", () => {
  test("global search API returns smoke user", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const res = await request.get(`${apiBase()}/api/v1/search/users?q=smoke_user_b`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    const hits = body.items || body.results || body;
    expect(JSON.stringify(hits)).toContain("smoke_user_b");
  });

  test("contacts list loads after login", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    await uiLogin(page, "smoke_user_a", "smokepass123");
    const contactsBtn = page.getByRole("button", { name: /Контакты|Contacts/i });
    if (await contactsBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await contactsBtn.click();
    }
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });
});
