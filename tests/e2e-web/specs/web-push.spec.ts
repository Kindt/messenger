import { test, expect } from "@playwright/test";
import { apiBase, apiLogin, ensureSmokeUsers } from "../fixtures/auth";

test.describe("web push", () => {
  test("device register API upserts web push token", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const deviceName = `web-push-${Date.now()}`;
    const register = await request.post(`${apiBase()}/api/v1/me/devices`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        device_name: deviceName,
        push_provider: "web",
        push_token: `web-push-token-${Date.now()}`,
      },
    });
    expect(register.ok()).toBeTruthy();
    const body = await register.json();
    expect(body.device_name || body.deviceName).toBe(deviceName);
  });

  test("me/settings exposes optional VAPID public key", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const res = await request.get(`${apiBase()}/api/v1/me/settings`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    const push = body.push || {};
    const vapid = push.vapid_public_key ?? push.vapidPublicKey;
    expect(vapid === null || vapid === undefined || (typeof vapid === "string" && vapid.length > 0)).toBeTruthy();
  });
});
