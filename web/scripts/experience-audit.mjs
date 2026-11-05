import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { launchAuditBrowser } from "./audit-browser.mjs";

const base = "http://127.0.0.1:5173";
const apiBase = "http://127.0.0.1:8080/api";
const output = path.resolve("../tmp/experience-audit");
await mkdir(output, { recursive: true });
const login = await fetch(`${apiBase}/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "user", password: "123456" }),
}).then((r) => r.json());
assert.equal(login.code, 200);
const catalog = await fetch(`${apiBase}/products`)
  .then((r) => r.json())
  .then((r) => r.data);
assert.ok(catalog.length > 12);
const parameterProblems = catalog.flatMap((product) => {
  try {
    const value = JSON.parse(product.parametersJson);
    return !value ||
      Array.isArray(value) ||
      typeof value !== "object" ||
      !Object.keys(value).length
      ? [{ id: product.id, name: product.name, reason: "参数不是非空对象" }]
      : [];
  } catch {
    return [
      { id: product.id, name: product.name, reason: "参数 JSON 无法解析" },
    ];
  }
});
assert.deepEqual(parameterProblems, []);
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const results = [];
const report = {
  generatedAt: new Date().toISOString(),
  results,
  parameterStructure: {
    total: catalog.length,
    problems: parameterProblems,
    note: "仅核对参数结构可用于对比，不代表联网核实每个数值。",
  },
  note: "页面读取真实商品和账号数据；竞态及对比结果使用隔离响应。不保存个人资料、不下单、不调用外部模型。",
};
try {
  for (const viewport of [
    { name: "desktop", width: 1440, height: 1000 },
    { name: "tablet", width: 820, height: 1180 },
    { name: "mobile", width: 390, height: 844 },
  ]) {
    for (const theme of ["classic", "midnight"]) {
      const context = await browser.newContext({
        viewport,
        reducedMotion: "reduce",
      });
      await context.addInitScript(
        ({ user, theme }) => {
          localStorage.setItem("token", user.token);
          localStorage.setItem(
            "user",
            JSON.stringify({
              ...user,
              theme,
              interfaceLayout: "standard",
              accentColor: theme === "classic" ? "#d9363e" : "#5aa7ff",
              backgroundColor: theme === "classic" ? "#f5f6f8" : "#111827",
            }),
          );
        },
        { user: login.data, theme },
      );
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (error) => errors.push(error.message));
      async function check(name, fn) {
        let error = "";
        try {
          await fn();
          assert.deepEqual(errors, []);
          assert.equal(
            await page.evaluate(
              () => document.documentElement.scrollWidth > innerWidth + 2,
            ),
            false,
            "页面级水平溢出",
          );
          await page.screenshot({
            path: path.join(output, `${viewport.name}-${theme}-${name}.png`),
            fullPage: false,
          });
        } catch (caught) {
          error = caught.stack;
        }
        results.push({
          viewport: viewport.name,
          theme,
          name,
          passed: !error,
          error,
        });
      }
      await check("catalog-pagination-and-filters", async () => {
        await page.goto(`${base}/products`, { waitUntil: "networkidle" });
        assert.equal(await page.locator(".product-card").count(), 12);
        const firstHref = await page
          .locator(".product-card-link")
          .first()
          .getAttribute("href");
        const y1 = (await page.locator(".product-card").nth(0).boundingBox()).y;
        const y2 = (await page.locator(".product-card").nth(1).boundingBox()).y;
        assert.ok(Math.abs(y1 - y2) < 2, "同一行卡片不能额外错位");
        await page.getByRole("button", { name: "下一页", exact: true }).click();
        assert.notEqual(
          await page.locator(".product-card-link").first().getAttribute("href"),
          firstHref,
        );
        assert.equal(
          await page
            .getByRole("button", { name: "第 2 页", exact: true })
            .getAttribute("aria-current"),
          "page",
        );
        if (viewport.width < 650)
          await page.getByRole("button", { name: /筛选商品/ }).click();
        await page.getByLabel("最低价", { exact: true }).fill("1000");
        await page.getByLabel("最高价", { exact: true }).fill("10000");
        await page
          .getByRole("button", { name: "应用价格", exact: true })
          .click();
        await page.waitForURL(
          (url) =>
            url.searchParams.get("minPrice") === "1000" &&
            url.searchParams.get("maxPrice") === "10000",
        );
        await page
          .getByRole("button", { name: "移除筛选：最高 ¥10000", exact: true })
          .click();
        await page.waitForURL((url) => !url.searchParams.has("maxPrice"));
        assert.equal(
          await page
            .getByRole("button", { name: "移除筛选：最低 ¥1000", exact: true })
            .count(),
          1,
        );
        await page.getByLabel("最低价", { exact: true }).fill("10000");
        await page.getByLabel("最高价", { exact: true }).fill("1000");
        await page
          .getByRole("button", { name: "应用价格", exact: true })
          .click();
        await page
          .getByRole("alert")
          .filter({ hasText: "最低价不能高于最高价" })
          .waitFor();
      });
      await check("catalog-late-response", async () => {
        await page.route("**/api/products?*", async (route) => {
          const keyword = new URL(route.request().url()).searchParams.get(
            "keyword",
          );
          if (!["slow", "fast"].includes(keyword)) return route.continue();
          if (keyword === "slow")
            await new Promise((resolve) => setTimeout(resolve, 600));
          await route.fulfill({
            json: {
              code: 200,
              data: [
                {
                  ...catalog[0],
                  name: keyword === "slow" ? "慢速旧结果" : "快速新结果",
                },
              ],
            },
          });
        });
        await page.goto(`${base}/products`, { waitUntil: "networkidle" });
        await page.getByLabel("搜索商城商品").fill("slow");
        const slow = page.waitForRequest((request) =>
          request.url().includes("keyword=slow"),
        );
        await page
          .locator(".catalog-search")
          .getByRole("button", { name: "搜索", exact: true })
          .click();
        await slow;
        await page.getByLabel("搜索商城商品").fill("fast");
        await page
          .locator(".catalog-search")
          .getByRole("button", { name: "搜索", exact: true })
          .click();
        await page
          .getByRole("heading", { name: "快速新结果", exact: true })
          .waitFor();
        await page.waitForTimeout(800);
        assert.equal(
          await page
            .getByRole("heading", { name: "慢速旧结果", exact: true })
            .count(),
          0,
        );
        await page.unroute("**/api/products?*");
      });
      await check("profile-sections-and-draft", async () => {
        await page.goto(`${base}/profile`, { waitUntil: "networkidle" });
        await page
          .locator(".profile-main > #overview")
          .waitFor({ state: "visible" });
        assert.equal(
          await page.locator(".profile-main > section:visible").count(),
          1,
        );
        const nav = page.locator("#account-navigation");
        async function selectAccountSection(name) {
          if (viewport.width <= 900)
            await page.locator(".account-menu-toggle").click();
          await nav.getByRole("link", { name, exact: true }).click();
        }
        await selectAccountSection("个人资料");
        await page.locator("#account").waitFor({ state: "visible" });
        await page.getByLabel("昵称", { exact: true }).fill("未保存的测试草稿");
        await selectAccountSection("界面与主题");
        await page.locator("#appearance").waitFor({ state: "visible" });
        assert.equal(
          await page.locator(".profile-main > section:visible").count(),
          1,
        );
        assert.equal(await nav.locator("[aria-current='page']").count(), 1);
        await selectAccountSection("个人资料");
        assert.equal(
          await page.getByLabel("昵称", { exact: true }).inputValue(),
          "未保存的测试草稿",
        );
        await selectAccountSection("界面与主题");
        await page.reload({ waitUntil: "networkidle" });
        await page.locator("#appearance").waitFor({ state: "visible" });
        assert.equal(await page.locator(".layout-option").count(), 3);
      });
      await check("advisor-modes-and-comparison-differences", async () => {
        await page.goto(`${base}/advisor`, { waitUntil: "networkidle" });
        assert.ok(
          (await page
            .locator(".advisor-hero h1 > span")
            .first()
            .evaluate((element) =>
              parseFloat(getComputedStyle(element).fontSize),
            )) >= 26,
          "顾问标题不能被旧 span 规则缩成装饰文字",
        );
        assert.equal(
          await page.locator("#advisor-chat-panel").isVisible(),
          true,
        );
        assert.equal(
          await page.locator("#advisor-form-panel").isVisible(),
          false,
        );
        await page.locator(".advisor-prompts button").first().click();
        const draft = await page.getByLabel("向 AI 描述选购需求").inputValue();
        assert.ok(draft.length > 10);
        await page.getByRole("tab", { name: /按条件推荐/ }).click();
        assert.equal(
          await page.locator("#advisor-form-panel").isVisible(),
          true,
        );
        await page.getByRole("tab", { name: /AI 对话咨询/ }).click();
        assert.equal(
          await page.getByLabel("向 AI 描述选购需求").inputValue(),
          draft,
        );
        const box = await page.locator(".advisor-chat-messages").boundingBox();
        assert.ok(box.height < 230, "初始对话不能保留大块固定高度留白");
        await page.route("**/api/products/compare", async (route) => {
          const ids = route.request().postDataJSON();
          const selected = ids.map((id) =>
            catalog.find((product) => product.id === id),
          );
          await route.fulfill({
            json: {
              code: 200,
              data: selected.map((product, index) => ({
                ...product,
                parametersJson: JSON.stringify({
                  相同测试参数: "一致",
                  不同测试参数: index === 0 ? 0 : 1,
                }),
              })),
            },
          });
        });
        await page.goto(`${base}/compare`, { waitUntil: "networkidle" });
        await page.locator(".compare-products input").nth(0).check();
        await page.locator(".compare-products input").nth(1).check();
        assert.equal(
          await page.locator(".compare-selection-chips button").count(),
          2,
        );
        await page
          .getByRole("button", { name: "生成对比结果", exact: true })
          .click();
        await page.locator(".compare-result").waitFor();
        await page.getByLabel("只看不同项", { exact: true }).check();
        assert.equal(
          await page
            .locator(".compare-table tbody tr:visible")
            .filter({ hasText: "相同测试参数" })
            .count(),
          0,
        );
        const different = page
          .locator(".compare-table tbody tr:visible")
          .filter({ hasText: "不同测试参数" });
        assert.equal(await different.count(), 1);
        assert.equal(await different.locator("td").first().innerText(), "0");
        await page
          .getByRole("button", { name: "重新选择商品", exact: true })
          .click();
        assert.equal(await page.locator(".compare-result").count(), 0);
      });
      await context.close();
      console.log(`${viewport.name}/${theme}: 新交互检查完成`);
    }
  }
} finally {
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(report, null, 2),
  );
  report.browserCleanup = await shutdown();
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(report, null, 2),
  );
}
const failures = results.filter((result) => !result.passed);
console.log(
  JSON.stringify(
    {
      total: results.length,
      passed: results.length - failures.length,
      failures,
    },
    null,
    2,
  ),
);
process.exit(failures.length ? 1 : 0);
