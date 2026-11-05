import assert from 'node:assert/strict';
import { launchAuditBrowser } from './audit-browser.mjs';
const { browser, shutdown } = await launchAuditBrowser({ headless: true, executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' });
try {
  const page = await browser.newPage();
  const requests = [], errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.route('https://api.example.invalid/api/**', async route => {
    requests.push(route.request().url());
    await route.fulfill({ json: { code: 200, data: route.request().url().endsWith('ai-status') ? { configured: true } : [] } });
  });
  await page.goto('http://127.0.0.1:5175/GG/');
  await page.locator('#home-need').fill('预算 4000，家里有猫');
  await page.locator('.need-submit button').click();
  await page.waitForURL('**/#/advisor');
  assert.equal(await page.locator('.advisor-chat-input textarea').inputValue(), '预算 4000，家里有猫');
  await page.locator('.advisor-chat-input button').click();
  await page.waitForURL('**/#/login?redirect=/advisor');
  assert.equal(await page.evaluate(() => sessionStorage.getItem('robotmall-advisor-entry')), '预算 4000，家里有猫');
  await page.reload();
  await page.getByRole('heading', {name:'账号登录'}).waitFor();
  const image = await page.request.get('http://127.0.0.1:5175/GG/products/placeholder.svg');
  assert.equal(image.status(), 200);
  assert.ok(requests.some(url => url.endsWith('/products')));
  assert.equal(requests.some(url => url.endsWith('/advisor/chat')), false);
  assert.deepEqual(errors, []);
  console.log('PASS: Pages subpath, hash reload, API URL, static image, anonymous AI login and draft preservation; mocked API only.');
} finally { await shutdown(); }
