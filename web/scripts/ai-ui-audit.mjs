import { launchAuditBrowser } from "./audit-browser.mjs";
import assert from "node:assert/strict";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const output = path.resolve("../tmp/ai-ui-audit");
await mkdir(output, { recursive: true });
const live = JSON.parse(
  await readFile("../tmp/ai-live-audit/report.json", "utf8"),
);
const recommendation = live.results.find(
  (result) => result.passed && result.products?.length,
);
assert.ok(recommendation, "需要先完成真实 AI 推荐测试");
const catalog = await fetch("http://127.0.0.1:8080/api/products")
  .then((r) => r.json())
  .then((r) => r.data);
const cards = recommendation.products.map((record) =>
  catalog.find((product) => product.id === record.id),
);
assert.ok(cards.every(Boolean));
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const cases = [];
try {
  for (const viewport of [
    { name: "desktop", width: 1440, height: 900 },
    { name: "tablet", width: 820, height: 1180 },
    { name: "mobile", width: 390, height: 844 },
  ]) {
    for (const theme of ["classic", "midnight", "custom"]) {
      const context = await browser.newContext({
        viewport,
        reducedMotion: "reduce",
      });
      await context.addInitScript(
        (theme) =>
          localStorage.setItem(
            "robotmall-theme",
            JSON.stringify({
              theme,
              accentColor: "#ffd54f",
              backgroundColor: theme === "classic" ? "#f5f6f8" : "#10121a",
            }),
          ),
        theme,
      );
      const page = await context.newPage();
      let calls = 0;
      await page.route("**/api/advisor/chat", async (route) => {
        calls++;
        // Explicitly exercise a provider response later than the old 10-second timeout.
        if (viewport.name === "desktop" && theme === "classic")
          await new Promise((resolve) => setTimeout(resolve, 11000));
        await route.fulfill({
          json: {
            code: 200,
            message: "success",
            data: {
              provider: "DeepSeek",
              products: cards,
              answer:
                "**测试重点**\n- 预算内候选商品\n<img src=x onerror=window.aiInjected=true>\n" +
                recommendation.answer,
            },
          },
        });
      });
      let error = "";
      try {
        await page.goto("http://localhost:5173/advisor", {
          waitUntil: "networkidle",
        });
        await page
          .locator(".advisor-chat-input textarea")
          .fill("虚构场景：预算3000，推荐商品");
        await page
          .getByRole("button", { name: "发送咨询", exact: true })
          .click();
        await page
          .getByText("DeepSeek 已连接", { exact: true })
          .waitFor({ timeout: 30000 });
        assert.equal(calls, 1);
        assert.equal(
          await page.locator(".ai-product-card").count(),
          cards.length,
        );
        assert.equal(
          await page
            .locator(".ai-answer strong")
            .filter({ hasText: "测试重点" })
            .count(),
          1,
        );
        assert.equal(
          await page.evaluate(() => window.aiInjected),
          undefined,
          "模型文字不能执行 HTML",
        );
        assert.equal(await page.locator(".ai-answer img").count(), 0);
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 2,
          ),
          false,
        );
        const href = await page
          .locator(".ai-product-card")
          .first()
          .getAttribute("href");
        assert.equal(href, `/product/${cards[0].id}`);
        await page.screenshot({
          path: path.join(output, `${viewport.name}-${theme}.png`),
        });
        await page.locator(".ai-product-card").first().click();
        await page.waitForURL(`**/product/${cards[0].id}`);
        await page
          .getByRole("heading", { name: cards[0].name, exact: true })
          .waitFor();
      } catch (caught) {
        error = caught.message;
      }
      cases.push({ viewport: viewport.name, theme, passed: !error, error });
      await context.close();
    }
    console.log(`${viewport.name}: AI 排版、慢响应、转跳及安全检查完成`);
  }
} finally {
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        isolatedAiResponse: true,
        usesPreviouslyVerifiedRecommendation: true,
        cases,
      },
      null,
      2,
    ),
  );
  const cleanup = await shutdown();
  const saved = JSON.parse(await readFile(path.join(output, "report.json"), "utf8"));
  saved.browserCleanup = cleanup;
  await writeFile(path.join(output, "report.json"), JSON.stringify(saved, null, 2));
}
console.log(
  JSON.stringify({
    total: cases.length,
    passed: cases.filter((x) => x.passed).length,
    failures: cases.filter((x) => !x.passed),
  }),
);
if (cases.some((x) => !x.passed)) process.exitCode = 1;
process.exit(process.exitCode || 0);
