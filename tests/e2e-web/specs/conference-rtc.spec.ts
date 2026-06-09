import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

test.describe("conference and media", () => {
  test("media capabilities include rtc hints", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.webrtc_stun_uris || body.stun_uris || body.stun).toBeTruthy();
  });

  test("in-chat conference create via API; UI shows chat", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-conf-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);

    const conf = await request.post(`${apiBase()}/api/v1/chats/${chatId}/conferences`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: { title: "Playwright meeting" },
    });
    expect([200, 201]).toContain(conf.status());

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });

  // Waiver (parity-matrix T022 / HANDOFF.md): accept/hangup/mic/cam/participants need TURN/WebRTC;
  // without local TURN the full RTC UI gate stays operator-only — see runtime-gate-report.md.
  test("in-chat conference panel toggle visible", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-conf-ui-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const callBtn = page.getByRole("button", { name: /Видео|Video|conference/i });
    await expect(callBtn).toBeVisible({ timeout: 10_000 });
    await callBtn.click();
    await expect(page.locator(".call-panel-title, .messenger-shell.call-open")).toBeVisible({
      timeout: 10_000,
    });
  });
});
