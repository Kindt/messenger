import { test, expect } from "@playwright/test";
import { apiBase, apiLogin } from "../fixtures/auth";

test.describe("live session moderation", () => {
  test("create live session returns 201 or 503", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const suffix = Date.now();

    const chatRes = await request.post(`${apiBase()}/api/v1/chats`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { type: "group", title: `e2e-live-mod-${suffix}`, member_ids: [] },
    });
    expect(chatRes.ok()).toBeTruthy();
    const chat = await chatRes.json();
    const chatId = chat.id || chat.chat_id;

    const create = await request.post(`${apiBase()}/api/v1/chats/${chatId}/live-sessions`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { title: `Moderation e2e ${suffix}` },
    });
    expect([201, 503]).toContain(create.status());
  });

  test("POST moderation returns expected status", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const suffix = Date.now();

    const chatRes = await request.post(`${apiBase()}/api/v1/chats`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { type: "group", title: `e2e-live-mod2-${suffix}`, member_ids: [] },
    });
    expect(chatRes.ok()).toBeTruthy();
    const chat = await chatRes.json();
    const chatId = chat.id || chat.chat_id;

    const create = await request.post(`${apiBase()}/api/v1/chats/${chatId}/live-sessions`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { title: `Moderation e2e ${suffix}` },
    });
    if (create.status() === 503) {
      test.skip(true, "LiveKit not configured");
      return;
    }
    expect(create.status()).toBe(201);
    const session = await create.json();
    const sessionId = session.live_session_id || session.liveSessionId;
    expect(sessionId).toBeTruthy();

    const mod = await request.post(
      `${apiBase()}/api/v1/chats/${chatId}/live-sessions/${sessionId}/moderation`,
      {
        headers: { Authorization: `Bearer ${token}` },
        data: { action: "slow_mode", reason: "e2e playwright" },
      }
    );
    expect(mod.status()).toBe(200);
    const body = await mod.json();
    expect(body.moderation_state || body.moderationState).toBe("slow_mode");
  });
});
