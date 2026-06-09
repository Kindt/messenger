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

  test("device register API upserts push token", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const deviceName = `playwright-${Date.now()}`;
    const register = await request.post(`${apiBase()}/api/v1/me/devices`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        device_name: deviceName,
        push_provider: "web",
        push_token: `e2e-token-${Date.now()}`,
      },
    });
    expect(register.ok()).toBeTruthy();
    const body = await register.json();
    expect(body.device_name || body.deviceName).toBe(deviceName);

    const list = await request.get(`${apiBase()}/api/v1/me/devices`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(list.ok()).toBeTruthy();
    const devices = await list.json();
    const items = devices.devices || devices.items || devices;
    expect(Array.isArray(items)).toBeTruthy();
    expect(items.some((d: { device_name?: string; deviceName?: string }) =>
      (d.device_name || d.deviceName) === deviceName
    )).toBeTruthy();
  });
});
