import { test, expect } from "@playwright/test";

test.describe("auth login-options", () => {
  test("public login-options returns JSON", async ({ request }) => {
    const api = process.env.KORUS_API_URL || "http://127.0.0.1:18080";
    const res = await request.get(`${api}/api/v1/auth/login-options`);
    expect([200, 404]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty("methods");
      expect(Array.isArray(body.methods)).toBe(true);
    }
  });
});
