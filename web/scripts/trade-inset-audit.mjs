import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { launchAuditBrowser } from './audit-browser.mjs';

const snapshot = JSON.parse(await readFile('public/catalog-snapshot.json', 'utf8'));
const output = '../tmp/trade-inset-audit';
await mkdir(output, { recursive: true });
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
});
const results = [];

async function checkInsets(page, width) {
  const expected = width <= 700 ? 16 : 24;
  const blocks = await page.locator('.trade-cascade-block').evaluateAll(elements =>
    elements.map(el => {
      const outer = el.getBoundingClientRect();
      const style = getComputedStyle(el);
      return {
        left: parseFloat(style.paddingLeft), right: parseFloat(style.paddingRight),
        children: [...el.children].filter(child => child.getClientRects().length).map(child => {
          const r = child.getBoundingClientRect();
          return { left: r.left - outer.left, right: outer.right - r.right };
        }),
      };
    }),
  );
  for (const block of blocks) {
    assert.equal(block.left, expected, 'left padding regressed');
    assert.equal(block.right, expected, 'right padding regressed');
    for (const child of block.children) {
      assert.ok(child.left >= expected - 1 && child.right >= expected - 1, 'content touches section edge');
    }
  }
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'page overflow');
  const overflowing = await page.locator('.trade-brand-grid button, .trade-model-grid button').evaluateAll(elements =>
    elements.filter(el => el.scrollWidth > el.clientWidth + 1).map(el => el.textContent),
  );
  assert.deepEqual(overflowing, [], 'button content overflow');
}

try {
  for (const width of [360, 390, 768, 1024, 1440, 1920]) {
    for (const theme of ['classic', 'midnight']) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' });
      await context.addInitScript(theme => {
        localStorage.setItem('user', JSON.stringify({ id: 9999, username: 'isolated', role: 'USER', theme }));
        localStorage.setItem('token', 'isolated');
      }, theme);
      await context.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        return route.fulfill({ json: { code: 200, data: path === '/api/products' ? snapshot.products : [] } });
      });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto('http://127.0.0.1:5173/trade-in');
      await page.locator('.trade-brand-grid button').first().waitFor();
      await checkInsets(page, width);
      await page.locator('.trade-cascade').scrollIntoViewIfNeeded();
      await page.screenshot({ path: `${output}/brands-${width}-${theme}.png` });
      await page.locator('.trade-brand-grid button').filter({ hasText: '石头科技' }).click();
      await page.locator('.trade-category-pills button').first().click();
      await page.locator('.trade-model-grid button').first().waitFor();
      await checkInsets(page, width);
      await page.locator('.trade-model-grid button').first().click();
      await page.locator('.trade-selected-device').waitFor();
      await checkInsets(page, width);
      await page.locator('.trade-cascade-block').nth(2).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `${output}/models-${width}-${theme}.png` });
      assert.deepEqual(errors, []);
      results.push({ width, theme, pass: true });
      await context.close();
    }
  }
  await writeFile(`${output}/report.json`, JSON.stringify({ note: '隔离模拟数据；检查真实左右留白与三级选择，不代表全站 UI 验收。', results }, null, 2));
  console.log(`PASS ${results.length} viewport/theme combinations: brand/category/model insets, selection and overflow`);
} finally {
  await shutdown();
}
