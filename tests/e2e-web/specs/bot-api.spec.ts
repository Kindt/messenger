import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, ensureSmokeUsers } from "../fixtures/auth";

test.describe("bot API", () => {
  test("register subscribe sendMessage flow", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const suffix = Date.now();
    const botName = `pw_bot_${suffix}`;
    const webhook = `https://example.com/bot/${suffix}`;

    const created = await request.post(`${apiBase()}/api/v1/bots`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        bot_name: botName,
        display_name: `PW Bot ${suffix}`,
        listen_mode: "READ_ALL",
        default_webhook_url: webhook,
      },
    });
    expect(created.status()).toBe(201);
    const body = await created.json();
    expect(body.bot_id).toBeTruthy();
    expect(body.access_token).toMatch(/^kbt_/);

    const chat = await request.post(`${apiBase()}/api/v1/chats`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { type: "group", title: `bot-pw-${suffix}`, member_ids: [] },
    });
    expect(chat.ok()).toBeTruthy();
    const chatBody = await chat.json();
    const chatId = chatBody.id || chatBody.chat_id;

    const sub = await request.post(
      `${apiBase()}/api/v1/bots/${body.bot_id}/chats/${chatId}/subscribe`,
      { headers: { Authorization: `Bearer ${token}` }, data: {} }
    );
    expect(sub.status()).toBe(201);

    const sent = await request.post(`${apiBase()}/api/v1/bot/send`, {
      headers: { Authorization: `Bearer ${body.access_token}` },
      data: { chat_id: chatId, content: `bot-pw-msg-${suffix}` },
    });
    expect(sent.status()).toBe(201);
  });

  test("long-poll updates returns empty when no events", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const suffix = Date.now();
    const created = await request.post(`${apiBase()}/api/v1/bots`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        bot_name: `pw_poll_${suffix}`,
        listen_mode: "READ_ALL",
        default_webhook_url: `https://example.com/poll/${suffix}`,
      },
    });
    expect(created.status()).toBe(201);
    const body = await created.json();

    const poll = await request.get(`${apiBase()}/api/v1/bot/updates?offset=0&timeout=1`, {
      headers: { Authorization: `Bearer ${body.access_token}` },
    });
    expect(poll.ok()).toBeTruthy();
    const pollBody = await poll.json();
    expect(Array.isArray(pollBody.updates)).toBeTruthy();
    expect(typeof pollBody.next_offset).toBe("number");
  });

  test("token rotate returns new kbt token", async ({ request }) => {
    await ensureSmokeUsers(request);
    const token = await apiLogin(request, "smoke_user_a", "smokepass123");
    const suffix = Date.now();
    const created = await request.post(`${apiBase()}/api/v1/bots`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        bot_name: `pw_rot_${suffix}`,
        default_webhook_url: `https://example.com/rot/${suffix}`,
      },
    });
    const body = await created.json();
    const oldToken = body.access_token;

    const rotated = await request.post(`${apiBase()}/api/v1/bots/${body.bot_id}/token/rotate`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(rotated.ok()).toBeTruthy();
    const rotBody = await rotated.json();
    expect(rotBody.access_token).toMatch(/^kbt_/);
    expect(rotBody.access_token).not.toBe(oldToken);
  });
});
