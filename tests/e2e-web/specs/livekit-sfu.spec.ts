import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

async function openCallPanel(
  page: import("@playwright/test").Page,
  request: import("@playwright/test").APIRequestContext
): Promise<string> {
  await ensureSmokeUsers(request);
  const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
  const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
  const idB = await apiMeId(request, tokenB);
  const title = `e2e-livekit-${Date.now()}`;
  await apiCreateGroup(request, tokenA, title, [idB]);
  await uiLogin(page, "smoke_user_a", "smokepass123");
  await uiOpenChatByTitle(page, title);
  await page.getByTestId("call-panel-toggle").click();
  await expect(page.getByTestId("call-panel-title")).toBeVisible({ timeout: 10_000 });
  return title;
}

test.describe("LiveKit group call SFU", () => {
  test("group_call_sfu_enabled in media capabilities", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(typeof body.group_call_sfu_enabled).toBe("boolean");
  });

  test("POST livekit join returns 200 or 503", async ({ request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const chatId = await apiCreateGroup(request, tokenA, `e2e-lk-join-${Date.now()}`, [idB]);

    const join = await request.post(`${apiBase()}/api/v1/chats/${chatId}/calls/livekit/join`, {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    expect([200, 503]).toContain(join.status());
    if (join.status() === 200) {
      const body = await join.json();
      expect(body.access_token || body.accessToken).toBeTruthy();
      expect(body.livekit_url || body.livekitUrl).toBeTruthy();
      expect(body.room_name || body.roomName).toBeTruthy();
    }
  });

  test("livekit SFU button visible when enabled", async ({ page, request }) => {
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(capsRes.ok()).toBeTruthy();
    const caps = await capsRes.json();
    const sfuEnabled = !!caps.group_call_sfu_enabled;

    await openCallPanel(page, request);
    const sfuBtn = page.getByTestId("livekit-sfu-button");
    if (sfuEnabled) {
      await expect(sfuBtn).toBeVisible({ timeout: 10_000 });
    } else {
      await expect(sfuBtn).toHaveCount(0);
    }
  });
});
