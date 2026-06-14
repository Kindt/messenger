import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import {
  closeGroupUserSessions,
  openGroupChatForAll,
  openGroupUserSessions,
  sessionPage,
  uiSendAndExpectDelivery,
  uiWaitForNewMessage,
} from "../fixtures/group-users";
import { uiSendMessage } from "../fixtures/ui";

test.describe("messaging group users (multi-browser)", () => {
  test.setTimeout(120_000);

  test("user A UI send delivers to user B in parallel browsers", async ({ browser, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-multi-ab-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    const sessions = await openGroupUserSessions(browser, ["smoke_user_a", "smoke_user_b"]);
    try {
      await openGroupChatForAll(sessions, title);
      const pageA = sessionPage(sessions, "smoke_user_a");
      const pageB = sessionPage(sessions, "smoke_user_b");
      const marker = `multi-ab-${Date.now()}`;
      await uiSendAndExpectDelivery(pageA, [pageB], marker, title);
    } finally {
      await closeGroupUserSessions(sessions);
    }
  });

  test("3-user group: A sends once, B and C both receive in UI", async ({ browser, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const tokenC = await apiLogin(request, "smoke_user_c", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const idC = await apiMeId(request, tokenC);
    const title = `e2e-multi-3u-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB, idC]);

    const sessions = await openGroupUserSessions(browser, [
      "smoke_user_a",
      "smoke_user_b",
      "smoke_user_c",
    ]);
    try {
      await openGroupChatForAll(sessions, title);
      const pageA = sessionPage(sessions, "smoke_user_a");
      const pageB = sessionPage(sessions, "smoke_user_b");
      const pageC = sessionPage(sessions, "smoke_user_c");
      const marker = `multi-3u-${Date.now()}`;
      await uiSendAndExpectDelivery(pageA, [pageB, pageC], marker, title);
    } finally {
      await closeGroupUserSessions(sessions);
    }
  });

  test("user B UI reply visible to user A in group", async ({ browser, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-multi-reply-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    const sessions = await openGroupUserSessions(browser, ["smoke_user_a", "smoke_user_b"]);
    try {
      await openGroupChatForAll(sessions, title);
      const pageA = sessionPage(sessions, "smoke_user_a");
      const pageB = sessionPage(sessions, "smoke_user_b");

      const parent = `parent-${Date.now()}`;
      await uiSendAndExpectDelivery(pageA, [pageB], parent, title);

      const reply = `reply-${Date.now()}`;
      const countA = await pageA.locator("article").count();
      await uiSendMessage(pageB, reply);
      await uiWaitForNewMessage(pageA, countA, reply, title);
      await expect(pageA.locator("article")).toHaveCount(2);
      await expect(pageB.locator("article")).toHaveCount(2);
    } finally {
      await closeGroupUserSessions(sessions);
    }
  });
});
