import { test, expect } from "@playwright/test";
import { apiBase, apiLogin, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

test.describe("profile and settings", () => {
  test("users/me via API after UI login", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    await uiLogin(page, "smoke_user_a", "smokepass123");

    const me = await request.get(`${apiBase()}/api/v1/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(me.ok()).toBeTruthy();
    const body = await me.json();
    expect(body.username || body.display_name).toBeTruthy();
  });

  test("blocks list API reachable", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const res = await request.get(`${apiBase()}/api/v1/blocks`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
  });
});
