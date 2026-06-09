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
