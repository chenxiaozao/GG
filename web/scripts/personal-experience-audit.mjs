import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { launchAuditBrowser } from './audit-browser.mjs';
const output = '../tmp/personal-experience-audit';
await mkdir(output, { recursive: true });
const { browser, shutdown } = await launchAuditBrowser({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
const results = [];
try {
  for (const width of [1440, 820, 390]) for (const theme of ['classic', 'midnight']) {
    const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' });
    await context.addInitScript(({ theme }) => {
      localStorage.setItem('user', JSON.stringify({ id: 99999, username: 'ui-test', nickname: '小造同学', role: 'USER', theme }));
    }, { theme });
    await context.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      const data = path.endsWith('/summary') ? { orderCount: 0, cartCount: 0, favoriteCount: 0, historyCount: 0, pendingPaymentCount: 0, pendingReceiptCount: 0, recentProducts: [] } : path.endsWith('/ai-status') ? { configured: false } : [];
      await route.fulfill({ json: { code: 200, data } });
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://127.0.0.1:5173/');
    await page.locator('#home-need').fill('家里有猫，预算 4000 元');
    await page.screenshot({ path: `${output}/home-${width}-${theme}.png` });
    assert.ok(await page.locator('.account-menu-toggle').isVisible());
    await page.locator('.account-menu-toggle').click();
    assert.ok(await page.locator('#account-navigation').isVisible());
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#account-navigation').isVisible(), false);
    await page.locator('.need-submit button').click();
    await page.waitForURL('**/advisor');
    assert.equal(await page.locator('.advisor-chat-input textarea').inputValue(), '家里有猫，预算 4000 元');
    await page.goto('http://127.0.0.1:5173/profile');
    await page.locator('.profile-stats').waitFor();
    await page.screenshot({ path: `${output}/space-${width}-${theme}.png`, fullPage: true });
    assert.ok(await page.locator('.space-continuation').isVisible());
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `overflow ${width}/${theme}`);
    await page.getByRole('button', { name: '定制我的界面' }).click();
    assert.ok(await page.locator('#appearance').isVisible());
    assert.equal(await page.locator('.space-continuation').count(), 0);
    assert.deepEqual(errors, []);
    results.push({ width, theme, pass: true });
    await context.close();
  }
  await writeFile(`${output}/report.json`, JSON.stringify({ note: '隔离模拟接口的页面回归；未调用真实 AI、未写入用户数据。', results }, null, 2));
  console.log(JSON.stringify(results));
} finally { await shutdown(); }
