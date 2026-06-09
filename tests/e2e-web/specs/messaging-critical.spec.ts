import { test, expect } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle, uiSendMessage } from "../fixtures/ui";

test.describe("messaging critical path", () => {
  test("login via UI, send message in group chat", async ({ page, request }) => {
    await ensureSmokeUsers(request);

    const tokenA = await apiLogin(request, "csadmin", "csadmin");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const groupTitle = `e2e-playwright-${Date.now()}`;
    await apiCreateGroup(request, tokenA, groupTitle, [idB]);

    await uiLogin(page, "csadmin", "csadmin");
    await uiOpenChatByTitle(page, groupTitle);
    const marker = `playwright-msg-${Date.now()}`;
    await uiSendMessage(page, marker);
  });
});
