#!/usr/bin/env node
import { chromium } from '../../tests/e2e-web/node_modules/@playwright/test/index.mjs';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const outDir = path.join(root, 'docs', 'images', 'guides');
const baseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:19088';
const apiUrl = process.env.KORUS_API_URL || 'http://127.0.0.1:18080';

async function shot(page, name, url, selector = 'body', fallbackUrl = null) {
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 20000 });
  } catch (error) {
    if (!fallbackUrl) throw error;
    console.warn(`fallback ${name}: ${url} -> ${fallbackUrl} (${error.message})`);
    await page.goto(fallbackUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
  }
  await page.waitForTimeout(800);
  const target = await page.locator(selector).first();
  await target.screenshot({ path: path.join(outDir, name) });
  console.log(`screenshot ${name} <- ${url}`);
}

async function adminLogin(page) {
  await page.goto(`${apiUrl}/admin/`, { waitUntil: 'domcontentloaded', timeout: 20000 });
  await page.locator('[data-testid=admin-login-user]').fill(process.env.KORUS_ADMIN_USER || 'csadmin');
  await page.locator('[data-testid=admin-login-pass]').fill(process.env.KORUS_ADMIN_PASS || 'csadmin');
  await page.locator('[data-testid=admin-login-btn]').click();
  await page.locator('[data-testid=admin-logout-btn]').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#sectionList li').first().waitFor({ state: 'visible', timeout: 15000 });
}

async function adminSectionShot(page, sectionId, name) {
  const nav = page.locator(`[data-testid=admin-nav-${sectionId}], li[data-section-id="${sectionId}"] button`).first();
  await nav.waitFor({ state: 'visible', timeout: 15000 });
  await nav.click();
  await page.locator('#panelTitle').waitFor({ state: 'visible', timeout: 15000 });
  await page.locator('[data-testid=admin-panel]').waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForFunction(() => {
    const title = document.querySelector('#panelTitle')?.textContent?.trim() || '';
    const panel = document.querySelector('[data-testid=admin-panel]');
    const summary = document.querySelector('#panelSummary');
    const content = document.querySelector('#panelContent');
    const visibleText = [title, summary?.textContent || '', content?.textContent || '', panel?.textContent || '']
      .join('\n')
      .trim();
    return visibleText.length > 20 || !!panel?.querySelector('table,input,select,textarea,button');
  }, null, { timeout: 15000 });
  await page.screenshot({ path: path.join(outDir, name), fullPage: true });
  console.log(`screenshot ${name} <- admin section ${sectionId}`);
}

async function assertUniqueScreenshots(names, label) {
  const seen = new Map();
  for (const name of names) {
    const bytes = await fs.readFile(path.join(outDir, name));
    const hash = crypto.createHash('sha256').update(bytes).digest('hex');
    if (seen.has(hash)) {
      throw new Error(`${label}: screenshots ${seen.get(hash)} and ${name} are identical`);
    }
    seen.set(hash, name);
  }
}

async function main() {
  await fs.mkdir(outDir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
  const admin = await browser.newPage({ viewport: { width: 1440, height: 960 } });

  await shot(page, 'user-login.png', baseUrl);
  await shot(page, 'user-chat-list.png', baseUrl);
  await shot(page, 'user-chat-thread.png', baseUrl);
  await shot(page, 'user-profile-settings.png', `${baseUrl}/`);
  await shot(page, 'user-file-link.png', `${baseUrl}/`);
  await shot(page, 'user-capabilities.png', `${apiUrl}/api/v1/platform/capabilities`, 'body', baseUrl);

  await shot(admin, 'app-admin-login.png', `${apiUrl}/admin/`);
  await adminLogin(admin);
  await adminSectionShot(admin, 'core-admin-session', 'app-admin-session.png');
  await adminSectionShot(admin, 'core-organizations', 'app-admin-organizations.png');
  await adminSectionShot(admin, 'core-product-modules', 'app-admin-product-modules.png');
  await adminSectionShot(admin, 'core-retention', 'app-admin-retention.png');
  await adminSectionShot(admin, 'plugins-l0-wizard', 'app-admin-plugins.png');

  await shot(page, 'infra-server-health.png', `${apiUrl}/api/v1/health`);
  await shot(page, 'infra-web-health.png', `${baseUrl}/health`);
  await adminSectionShot(admin, 'core-server-stats', 'infra-admin-stats.png');
  await adminSectionShot(admin, 'core-external-stack', 'infra-external-stack.png');
  await adminSectionShot(admin, 'core-fleet-stats', 'infra-product-modules.png');
  await shot(page, 'infra-smoke-result.png', `${apiUrl}/api/v1/ready`, 'body', `${apiUrl}/api/v1/health`);

  await assertUniqueScreenshots(
    [
      'app-admin-login.png',
      'app-admin-session.png',
      'app-admin-organizations.png',
      'app-admin-product-modules.png',
      'app-admin-retention.png',
      'app-admin-plugins.png',
    ],
    'admin guide'
  );

  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
