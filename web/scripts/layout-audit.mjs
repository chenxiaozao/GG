import { launchAuditBrowser } from "./audit-browser.mjs";
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const output = path.resolve("../tmp/layout-audit");
await mkdir(output, { recursive: true });
const response = await fetch("http://127.0.0.1:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "user", password: "123456" }),
});
const login = await response.json();
assert.equal(login.code, 200);
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const themes = [
  {
    name: "classic",
    theme: "classic",
    accentColor: "#d9363e",
    backgroundColor: "#f5f6f8",
    scheme: "light",
  },
  {
    name: "midnight",
    theme: "midnight",
    accentColor: "#5aa7ff",
    backgroundColor: "#111827",
    scheme: "dark",
  },
  {
    name: "warm",
    theme: "warm",
    accentColor: "#c65d32",
    backgroundColor: "#f7f1e7",
    scheme: "light",
  },
  {
    name: "custom-dark",
    theme: "custom",
    accentColor: "#ffd54f",
    backgroundColor: "#10121a",
    scheme: "dark",
  },
  {
    name: "custom-light",
    theme: "custom",
    accentColor: "#236850",
    backgroundColor: "#eef7f2",
    scheme: "light",
  },
];
const cases = [];
try {
  for (const viewport of [
    { name: "desktop", width: 1440, height: 900 },
    { name: "tablet", width: 820, height: 1180 },
    { name: "mobile", width: 390, height: 844 },
  ]) {
    for (const interfaceLayout of ["standard", "editorial", "compact"]) {
      for (const palette of themes) {
        const context = await browser.newContext({
          viewport,
          reducedMotion: "reduce",
        });
        const stored = { ...login.data, ...palette, interfaceLayout };
        await context.addInitScript((user) => {
          localStorage.setItem("user", JSON.stringify(user));
          localStorage.setItem("token", user.token);
        }, stored);
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", (error) => errors.push(error.message));
        for (const route of [
          "/",
          "/products",
          "/profile",
          "/favorites",
          "/advisor",
        ]) {
          let error = "";
          try {
            await page.goto("http://localhost:5173" + route, {
              waitUntil: "networkidle",
            });
            assert.equal(
              await page.locator("html").getAttribute("data-layout"),
              interfaceLayout,
            );
            assert.equal(
              await page.locator("html").getAttribute("data-color-scheme"),
              palette.scheme,
            );
            assert.equal(
              await page.evaluate(
                () => document.documentElement.scrollWidth > innerWidth + 2,
              ),
              false,
              "页面水平溢出",
            );
            if (route === "/profile") {
              if (viewport.width <= 900) await page.locator('.account-menu-toggle').click();
              await page
                .locator("#account-navigation")
                .getByRole("link", { name: "界面与主题", exact: true })
                .click();
              await page.locator("#appearance").waitFor({ state: "visible" });
              assert.equal(
                await page
                  .getByRole("button", { name: /使用此布局|当前使用/ })
                  .filter({ has: page.locator(".layout-preview") })
                  .count(),
                3,
              );
              assert.equal(
                await page
                  .locator(".layout-option[aria-pressed='true']")
                  .count(),
                1,
              );
            }
            if (route === "/products" && interfaceLayout === "compact") {
              assert.equal(
                await page
                  .locator(".product-card-link")
                  .first()
                  .evaluate((element) => getComputedStyle(element).display),
                "grid",
              );
            }
            if (
              ["/", "/profile"].includes(route) &&
              palette.name === "classic"
            ) {
              await page.screenshot({
                path: path.join(
                  output,
                  `${viewport.name}-${interfaceLayout}-${route === "/" ? "home" : "profile"}.png`,
                ),
                fullPage: false,
              });
            }
            assert.deepEqual(errors, []);
          } catch (caught) {
            error = caught.message;
          }
          cases.push({
            viewport: viewport.name,
            interfaceLayout,
            palette: palette.name,
            route,
            passed: !error,
            error,
          });
        }
        await context.close();
      }
      console.log(`${viewport.name}/${interfaceLayout}: 5 套配色检查完成`);
    }
  }
} finally {
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        browser: "Chrome",
        usesLiveApi: true,
        total: cases.length,
        passed: cases.filter((x) => x.passed).length,
        failures: cases.filter((x) => !x.passed),
        cases,
      },
      null,
      2,
    ),
  );
  await shutdown();
}
const failures = cases.filter((x) => !x.passed);
console.log(
  JSON.stringify({
    total: cases.length,
    passed: cases.length - failures.length,
    failures,
  }),
);
if (failures.length) process.exitCode = 1;
process.exit(process.exitCode || 0);
