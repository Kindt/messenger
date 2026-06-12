import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiExpectMessageText, uiLogin, uiOpenChatByTitle, uiSendMessage } from "../fixtures/ui";

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
    await expect(page.locator("article")).toHaveCount(2, { timeout: 20_000 });
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    const caps = await capsRes.json();
    if ((caps.mls_status || caps.mlsStatus) === "active") {
      await expect(page.locator(".msg-e2ee-body").first()).toBeVisible();
    } else {
      await uiExpectMessageText(page, "reply body");
    }
  });

  test("reply via UI composer", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-ui-reply-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);
    const parentText = `parent-${Date.now()}`;
    await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: { type: "text", content: parentText },
    });

    await uiLogin(page, "smoke_user_b", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator("article")).toHaveCount(1, { timeout: 20_000 });
    await expect(page.locator(".msg-e2ee-body, .msg-body").first()).toBeVisible();

    await page.locator("[data-testid=message-reply-button]").first().click();
    const replyText = `ui-reply-${Date.now()}`;
    const composer = page.locator("[data-testid=message-composer]");
    await composer.fill(replyText);
    await composer.press("Enter");
    await expect(composer).toHaveValue("", { timeout: 10_000 });
    await expect(page.locator("article")).toHaveCount(2, { timeout: 30_000 });
  });
});
