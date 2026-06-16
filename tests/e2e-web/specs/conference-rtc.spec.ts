import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

/** Headless Chromium has no camera/mic; mesh call panel needs a stub stream. */
async function mockGetUserMedia(page: import("@playwright/test").Page): Promise<void> {
  await page.addInitScript(() => {
    const audioOnlyStream = () => {
      const ctx = new AudioContext();
      const dest = ctx.createMediaStreamDestination();
      const osc = ctx.createOscillator();
      osc.frequency.value = 440;
      osc.connect(dest);
      osc.start();
      return dest.stream;
    };
    const videoStream = () => {
      const canvas = document.createElement("canvas");
      canvas.width = 2;
      canvas.height = 2;
      return canvas.captureStream(1);
    };
    Object.defineProperty(navigator.mediaDevices, "getUserMedia", {
      configurable: true,
      value: async (constraints: MediaStreamConstraints) => {
        const wantVideo = !!constraints.video;
        const wantAudio = !!constraints.audio;
        if (wantVideo && !wantAudio) return videoStream();
        if (wantAudio && !wantVideo) return audioOnlyStream();
        const v = videoStream();
        const a = audioOnlyStream();
        a.getAudioTracks().forEach((t) => v.addTrack(t));
        return v;
      },
    });
  });
}

async function mockMeshWebRtc(page: import("@playwright/test").Page): Promise<void> {
  await mockGetUserMedia(page);
  await page.addInitScript(() => {
    class MockPC {
      localDescription: RTCSessionDescriptionInit | null = null;
      onicecandidate: ((ev: RTCPeerConnectionIceEvent) => void) | null = null;
      ontrack: ((ev: RTCTrackEvent) => void) | null = null;
      async createOffer() {
        return { type: "offer", sdp: "v=0" } as RTCSessionDescriptionInit;
      }
      async createAnswer() {
        return { type: "answer", sdp: "v=0" } as RTCSessionDescriptionInit;
      }
      async setLocalDescription(desc: RTCSessionDescriptionInit) {
        this.localDescription = desc;
      }
      async setRemoteDescription(_desc: RTCSessionDescriptionInit) {}
      async addIceCandidate(_c: RTCIceCandidateInit) {}
      addTrack() {}
      close() {}
    }
    // @ts-expect-error test mock
    window.RTCPeerConnection = MockPC;
  });
}

async function openMeshCallPanel(
  page: import("@playwright/test").Page,
  request: import("@playwright/test").APIRequestContext
): Promise<string> {
  await ensureSmokeUsers(request);
  const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
  const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
  const idB = await apiMeId(request, tokenB);
  const title = `e2e-mesh-${Date.now()}`;
  await apiCreateGroup(request, tokenA, title, [idB]);
  await uiLogin(page, "smoke_user_a", "smokepass123");
  await uiOpenChatByTitle(page, title);
  await page.getByTestId("call-panel-toggle").click();
  const meshBtn = page.locator("[data-testid=mesh-webrtc-button]");
  await expect(meshBtn).toBeVisible({ timeout: 10_000 });
  await meshBtn.click();
  return title;
}

test.describe("conference and media", () => {
  test("media capabilities include rtc hints", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.webrtc_stun_uris || body.stun_uris || body.stun).toBeTruthy();
  });

  test("in-chat conference create via API; UI shows chat", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-conf-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);

    const conf = await request.post(`${apiBase()}/api/v1/chats/${chatId}/conferences`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: { title: "Playwright meeting" },
    });
    expect([200, 201]).toContain(conf.status());

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });

  test("mesh call controls with mocked WebRTC", async ({ page, request }) => {
    await mockMeshWebRtc(page);
    await openMeshCallPanel(page, request);
    await expect(page.getByTestId("call-panel-title-audio")).toBeVisible({ timeout: 10_000 });
  });

  test("mesh audio-first shows local avatar without camera", async ({ page, request }) => {
    await mockMeshWebRtc(page);
    await openMeshCallPanel(page, request);
    await expect(page.getByTestId("call-panel-title-audio")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId("call-local-avatar")).toBeVisible();
    await expect(page.getByTestId("call-cam-toggle")).toBeVisible();
  });

  test("in-chat conference panel toggle visible", async ({ page, request }) => {
    await mockGetUserMedia(page);
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-conf-ui-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const callBtn = page.getByTestId("call-panel-toggle");
    await expect(callBtn).toBeVisible({ timeout: 10_000 });
    await callBtn.click();
    await expect(page.getByTestId("call-panel-title")).toBeVisible({ timeout: 10_000 });
  });
});
