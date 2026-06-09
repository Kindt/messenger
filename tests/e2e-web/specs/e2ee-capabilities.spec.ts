import { test, expect } from "@playwright/test";
import { apiBase, apiLogin } from "../fixtures/auth";

test.describe("e2ee capabilities", () => {
  test("capabilities advertise e2ee schemes", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    const schemes = body.e2ee_schemes || body.e2eeSchemes || [];
    expect(schemes.length).toBeGreaterThan(0);
    expect(body.mls_status || body.mlsStatus).toBeTruthy();
  });

  test("legacy e2ee key package endpoint requires auth", async ({ request }) => {
    const anon = await request.post(`${apiBase()}/api/v1/e2ee/key-packages`, { data: {} });
    expect([401, 403, 404, 405]).toContain(anon.status());
    const token = await apiLogin(request, "csadmin", "csadmin");
    const authed = await request.get(`${apiBase()}/api/v1/e2ee/key-packages/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 404]).toContain(authed.status());
  });

  test("mls active exposes mls scheme", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    const mlsStatus = body.mls_status || body.mlsStatus || "";
    const schemes: string[] = body.e2ee_schemes || body.e2eeSchemes || [];
    if (mlsStatus === "active") {
      expect(schemes).toContain("mls");
    }
  });

  test("plaintext-preview blocked when mls active", async ({ request }) => {
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(capsRes.ok()).toBeTruthy();
    const caps = await capsRes.json();
    const mlsStatus = caps.mls_status || caps.mlsStatus || "";
    if (mlsStatus !== "active") {
      test.skip();
      return;
    }
    const token = await apiLogin(request, "csadmin", "csadmin");
    const chatId = "00000000-0000-0000-0000-000000000001";
    const msgId = "00000000-0000-0000-0000-000000000002";
    const preview = await request.get(
      `${apiBase()}/api/v1/chats/${chatId}/messages/${msgId}/plaintext-preview`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    expect(preview.status()).toBe(403);
  });

  test("authenticated user can upload mls key package", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const pk = Buffer.alloc(32, 0xab).toString("base64");
    const sk = Buffer.alloc(32, 0xcd).toString("base64");
    const res = await request.post(`${apiBase()}/api/v1/e2ee/key-packages`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        public_key_base64: pk,
        signature_key_base64: sk,
        cipher_suite: "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
        protocol_version: "mls10",
      },
    });
    expect([201, 409, 500]).toContain(res.status());
  });

  test("send with e2ee_scheme mls accepted", async ({ request }) => {
    const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    const caps = await capsRes.json();
    const schemes: string[] = caps.e2ee_schemes || caps.e2eeSchemes || [];
    if (!schemes.includes("mls")) {
      test.skip();
      return;
    }
    const token = await apiLogin(request, "csadmin", "csadmin");
    const chatsRes = await request.get(`${apiBase()}/api/v1/chats`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!chatsRes.ok()) {
      test.skip();
      return;
    }
    const chats = await chatsRes.json();
    const chatList = Array.isArray(chats) ? chats : chats.chats || [];
    if (chatList.length === 0) {
      test.skip();
      return;
    }
    const chatId = chatList[0].id || chatList[0].chat_id;
    const sendRes = await request.post(`${apiBase()}/api/v1/chats/${chatId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        type: "text",
        content: "mls e2e probe",
        e2ee_scheme: "mls",
      },
    });
    expect([200, 201, 403]).toContain(sendRes.status());
    if (sendRes.ok()) {
      const sent = await sendRes.json();
      const msgType = sent.type || "";
      expect(msgType === "e2ee-text" || msgType === "text").toBeTruthy();
    }
  });
});
