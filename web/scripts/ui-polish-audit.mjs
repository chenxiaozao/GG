import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { launchAuditBrowser } from "./audit-browser.mjs";
const output = "../tmp/ui-polish-audit";
await mkdir(output, { recursive: true });
const login = await fetch("http://127.0.0.1:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "user", password: "123456" }),
}).then((r) => r.json());
assert.equal(login.code, 200);
const products = await fetch("http://127.0.0.1:8080/api/products").then((r) =>
  r.json(),
);
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const results = [];
try {
  for (const width of [1440, 390])
    for (const theme of ["classic", "warm", "midnight"]) {
      const context = await browser.newContext({
        viewport: { width, height: 900 },
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
              accentColor:
                theme === "classic"
                  ? "#d9363e"
                  : theme === "warm"
                    ? "#c65d32"
                    : "#5aa7ff",
              backgroundColor:
                theme === "classic"
                  ? "#f5f6f8"
                  : theme === "warm"
                    ? "#f7f1e7"
                    : "#111827",
            }),
          );
        },
        { user: login.data, theme },
      );
      const page = await context.newPage();
      page.setDefaultTimeout(8000);
      try {
        await page.goto("http://127.0.0.1:5173/", { waitUntil: "networkidle" });
        const searchInput = page.locator(".home-search-v3 input");
        for (const focused of [false, true]) {
          if (focused) await searchInput.focus();
          assert.equal(
            await searchInput.evaluate(
              (el) => getComputedStyle(el).borderTopWidth,
            ),
            "0px",
          );
        }
        await page.goto(
          `http://127.0.0.1:5173/product/${products.data[0].id}`,
          { waitUntil: "networkidle" },
        );
        const stepper = page.locator(".quantity-stepper");
        const box = await stepper.boundingBox();
        const plus = await stepper.locator("button").last().boundingBox();
        assert.ok(plus.x + plus.width <= box.x + box.width + 1);
        assert.equal(
          await stepper
            .locator("input")
            .evaluate((el) => getComputedStyle(el).borderTopWidth),
          "0px",
        );
        await page.goto("http://127.0.0.1:5173/profile#account", {
          waitUntil: "networkidle",
        });
        await page.locator("#account").waitFor({ state: "visible" });
        if (login.data.avatar) {
          const preview = page.locator(".avatar-editor-preview img");
          await preview.waitFor();
          assert.equal(
            await preview.evaluate((el) => el.complete && el.naturalWidth > 0),
            true,
            "stored avatar must load",
          );
        }
        await page.screenshot({
          path: `${output}/${width}-${theme}-profile.png`,
          fullPage: true,
        });
        await page.goto("http://127.0.0.1:5173/advisor", {
          waitUntil: "networkidle",
        });
        await page.evaluate(() =>
          window.scrollTo(0, document.body.scrollHeight),
        );
        const topButton = page.locator(".back-to-top");
        await topButton.waitFor({ state: "visible" });
        assert.equal(
          await topButton.evaluate((el) => getComputedStyle(el).position),
          "fixed",
        );
        const topBox = await topButton.boundingBox();
        assert.ok(topBox.x > width / 2 && topBox.x + topBox.width <= width - 8);
        if (width > 900) {
          const sidebar = await page
            .locator("#account-navigation")
            .boundingBox();
          const footer = await page.locator(".site-footer").boundingBox();
          assert.ok(
            footer.x >= sidebar.x + sidebar.width + 16,
            "footer must not overlap sidebar",
          );
        }
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 2,
          ),
          false,
        );
        await page.screenshot({
          path: `${output}/${width}-${theme}-footer.png`,
        });
        await topButton.click();
        await page.waitForFunction(() => scrollY < 5);
        results.push({ width, theme, passed: true });
      } catch (error) {
        results.push({ width, theme, passed: false, error: error.message });
      }
      await context.close();
    }
} finally {
  await writeFile(
    `${output}/report.json`,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        avatarPath: login.data.avatar || null,
        results,
        note: "Read-only UI checks; detail view may append ordinary browsing history. No profile save, payment or external AI request.",
      },
      null,
      2,
    ),
  );
  await shutdown();
}
console.log(JSON.stringify(results));
process.exit(results.every((r) => r.passed) ? 0 : 1);
