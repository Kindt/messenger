import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle, uiSendMessage } from "../fixtures/ui";

test.describe("messaging actions", () => {
  test("send and verify message in group", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-actions-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const marker = `action-msg-${Date.now()}`;
    await uiSendMessage(page, marker);
  });

  test("reply via API after UI send", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-reply-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);

    const send = await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: { type: "text", content: "parent message" },
    });
    expect(send.ok()).toBeTruthy();
    const parent = await send.json();
    const parentId = parent.id || parent.message_id;

    const reply = await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
      headers: { Authorization: `Bearer ${tokenB}` },
      data: { type: "text", content: "reply body", reply_to_msg_id: parentId },
    });
    expect(reply.ok()).toBeTruthy();

    await uiLogin(page, "smoke_user_b", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.getByText("reply body")).toBeVisible({ timeout: 20_000 });
  });
});
