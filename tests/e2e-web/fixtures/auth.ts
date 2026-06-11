import { APIRequestContext } from "@playwright/test";

const apiBase = () => process.env.KORUS_API_URL || "http://127.0.0.1:8080";

export { apiBase };

export async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string
): Promise<string> {
  const res = await request.post(`${apiBase()}/api/v1/auth/login`, {
    data: { username, password },
  });
  if (!res.ok()) {
    throw new Error(`login failed for ${username}: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  const token = body.access_token || body.accessToken;
  if (!token) throw new Error(`no token for ${username}`);
  return token as string;
}

export async function apiMeId(request: APIRequestContext, token: string): Promise<string> {
  const res = await request.get(`${apiBase()}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) throw new Error(`users/me failed: ${res.status()}`);
  const body = await res.json();
  return (body.id || body.user_id) as string;
}

export async function apiCreateGroup(
  request: APIRequestContext,
  token: string,
  title: string,
  memberIds: string[]
): Promise<string> {
  const res = await request.post(`${apiBase()}/api/v1/chats`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { type: "group", title, member_ids: memberIds },
  });
  if (!res.ok()) throw new Error(`create group failed: ${res.status()} ${await res.text()}`);
  const body = await res.json();
  return (body.id || body.chat_id) as string;
}

export async function apiSendMessage(
  request: APIRequestContext,
  token: string,
  chatId: string,
  text: string
): Promise<void> {
  const res = await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { type: "text", content: text },
  });
  if (!res.ok()) {
    throw new Error(`send message failed: ${res.status()} ${await res.text()}`);
  }
}

/** Group with at least one message — avoids empty chat list in MLS/API tests. */
export async function apiEnsureGroupWithMessage(
  request: APIRequestContext,
  titlePrefix = "e2e-seed"
): Promise<{ chatId: string; token: string; title: string }> {
  await ensureSmokeUsers(request);
  const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
  const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
  const idB = await apiMeId(request, tokenB);
  const title = `${titlePrefix}-${Date.now()}`;
  const chatId = await apiCreateGroup(request, tokenA, title, [idB]);
  await apiSendMessage(request, tokenA, chatId, "seed message for e2e");
  return { chatId, token: tokenA, title };
}

export async function ensureSmokeUsers(request: APIRequestContext): Promise<void> {
  const users = [
    { u: "smoke_user_a", d: "Smoke User A" },
    { u: "smoke_user_b", d: "Smoke User B" },
    { u: "smoke_user_c", d: "Smoke User C" },
  ];
  for (const { u, d } of users) {
    const res = await request.post(`${apiBase()}/api/v1/auth/register`, {
      data: { username: u, password: "smokepass123", display_name: d },
    });
    if (!res.ok() && res.status() !== 409) {
      throw new Error(`register ${u}: ${res.status()}`);
    }
  }
}
