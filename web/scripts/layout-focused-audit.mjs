import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { launchAuditBrowser } from "./audit-browser.mjs";

const output = path.resolve("../tmp/layout-focused-audit");
await mkdir(output, { recursive: true });
const { data: user } = await fetch("http://127.0.0.1:8080/api/auth/login", {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "user", password: "123456" }),
}).then(response => response.json());
assert.ok(user?.token);
const { browser, shutdown } = await launchAuditBrowser({ headless: true, executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" });
const report = { generatedAt: new Date().toISOString(), results: [], note: "本轮三种布局的个人中心入口及主题面板专项检查；不代表全站全部布局/配色组合验收。" };
try {
  for (const viewport of [{ name: "desktop", width: 1440, height: 1000 }, { name: "mobile", width: 390, height: 844 }]) {
    for (const layout of ["standard", "editorial", "compact"]) {
      for (const theme of ["classic", "midnight"]) {
        const context = await browser.newContext({ viewport, reducedMotion: "reduce" });
        await context.addInitScript(({ user, layout, theme }) => {
          localStorage.setItem("token", user.token);
          localStorage.setItem("user", JSON.stringify({ ...user, theme, interfaceLayout: layout, accentColor: theme === "classic" ? "#d9363e" : "#5aa7ff", backgroundColor: theme === "classic" ? "#f5f6f8" : "#111827" }));
        }, { user, layout, theme });
        const page = await context.newPage();
        page.setDefaultTimeout(8000);
        let error = "";
        try {
          await page.goto("http://127.0.0.1:5173/profile", { waitUntil: "domcontentloaded", timeout: 12000 });
          await page.locator("#overview").waitFor({ state: "visible" });
          if (viewport.width <= 900) await page.locator('.account-menu-toggle').click();
          await page.locator("#account-navigation").getByRole("link", { name: "界面与主题", exact: true }).click();
          await page.locator("#appearance").waitFor({ state: "visible" });
          assert.equal(await page.locator(".layout-option").count(), 3);
          assert.equal(await page.locator(".profile-main > section:visible").count(), 1);
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false);
          assert.equal(await page.locator("html").getAttribute("data-layout"), layout);
          await page.screenshot({ path: path.join(output, `${viewport.name}-${layout}-${theme}.png`) });
        } catch (caught) { error = caught.message; }
        report.results.push({ viewport: viewport.name, layout, theme, passed: !error, error });
        await writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
        await context.close();
      }
    }
  }
} finally {
  report.browserCleanup = await shutdown();
  await writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
}
const failures = report.results.filter(result => !result.passed);
console.log(JSON.stringify({ total: report.results.length, passed: report.results.length - failures.length, failures }, null, 2));
process.exit(failures.length ? 1 : 0);
