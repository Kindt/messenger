import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import {
  uiClickMessageAction,
  uiExpectMessageText,
  uiExpectThreadHasMessage,
  uiLogin,
  uiOpenChatByTitle,
  uiSendMessage,
} from "../fixtures/ui";

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
      await expect(page.locator("[data-testid=message-reply-quote]").last()).toContainText(
        "parent message"
      );
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

    await uiClickMessageAction(page, "message-reply-button");
    await expect(page.locator("[data-testid=composer-reply-bar]")).toBeVisible();
    const replyText = `ui-reply-${Date.now()}`;
    const composer = page.locator("[data-testid=message-composer]");
    await composer.fill(replyText);
    await composer.press("Enter");
    await expect(composer).toHaveValue("", { timeout: 10_000 });
    await expect(page.locator("article")).toHaveCount(2, { timeout: 30_000 });
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    const caps = await capsRes.json();
    if ((caps.mls_status || caps.mlsStatus) !== "active") {
      await expect(page.locator("[data-testid=message-reply-quote]").last()).toContainText(
        parentText
      );
    }
  });

  test("forward to second chat", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const titleSrc = `e2e-fwd-src-${Date.now()}`;
    const titleDst = `e2e-fwd-dst-${Date.now()}`;
    await apiCreateGroup(request, tokenA, titleSrc, [idB]);
    const chatDst = await apiCreateGroup(request, tokenA, titleDst, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, titleSrc);
    const marker = `fwd-${Date.now()}`;
    await uiSendMessage(page, marker);
    const msgIdx = (await page.locator("article").count()) - 1;
    page.once("dialog", (d) => d.accept());
    await uiClickMessageAction(page, "message-forward-button", msgIdx);
    await page.locator(".forward-chat-item", { hasText: titleDst }).click();

    const list = await request.get(`${apiBase()}/api/v1/chats/${chatDst}/messages?limit=10`, {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    expect(list.ok()).toBeTruthy();
    const rows = await list.json();
    expect(rows.length).toBeGreaterThanOrEqual(1);

    await uiOpenChatByTitle(page, titleDst);
    await uiExpectThreadHasMessage(page);
  });

  test("delete own message", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-del-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const marker = `del-${Date.now()}`;
    await uiSendMessage(page, marker);
    const idx = (await page.locator("article").count()) - 1;
    page.once("dialog", (d) => d.accept());
    await uiClickMessageAction(page, "message-delete-button", idx);
    await expect(page.locator("article").nth(idx).locator(".msg-deleted-body")).toBeVisible({
      timeout: 15_000,
    });
  });

  test("edit own message", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-edit-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const original = `edit-orig-${Date.now()}`;
    await uiSendMessage(page, original);
    const edited = `edit-new-${Date.now()}`;
    const idx = (await page.locator("article").count()) - 1;
    page.once("dialog", (d) => d.accept(edited));
    await uiClickMessageAction(page, "message-edit-button", idx);
    await uiExpectMessageText(page, edited);
  });

  test("message deep link opens chat and highlights", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-deeplink-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);
    const marker = `deeplink-${Date.now()}`;
    const send = await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: { type: "text", content: marker },
    });
    expect(send.ok()).toBeTruthy();
    const body = await send.json();
    const msgId = body.id || body.message_id;

    await page.goto(`/?chat=${encodeURIComponent(chatId)}&msg=${encodeURIComponent(msgId)}`);
    await uiLogin(page, "smoke_user_a", "smokepass123");
    const target = page.locator(`[id="msg-${msgId}"]`);
    await expect(target).toBeVisible({ timeout: 30_000 });
  });

  test("copy message link shows status", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-link-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const marker = `link-${Date.now()}`;
    await uiSendMessage(page, marker);
    const msgIdx = (await page.locator("article").count()) - 1;
    await uiClickMessageAction(page, "message-link-button", msgIdx);
    await expect(page.locator(".info-banner").first()).toBeVisible({ timeout: 10_000 });
  });
});
