import { launchAuditBrowser } from "./audit-browser.mjs";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const appUrl = "http://localhost:5173";
const apiUrl = "http://127.0.0.1:8080/api";
const output = path.resolve("../tmp/responsive-audit");
await mkdir(output, { recursive: true });

const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const login = async (username, password) => {
  const response = await fetch(`${apiUrl}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const body = await response.json();
  if (!response.ok || body.code !== 200)
    throw new Error(`${username} 登录失败`);
  return body.data;
};
const user = await login("user", "123456");
const admin = await login("admin", "admin123");
const viewports = [
  { name: "desktop", width: 1440, height: 900 },
  { name: "tablet", width: 820, height: 1180 },
  { name: "mobile", width: 390, height: 844 },
];
const publicRoutes = [
  "/",
  "/products",
  "/compare",
  "/compatibility",
  "/advisor",
  "/trade-in",
  "/cart",
  "/favorites",
  "/history",
];
const userRoutes = ["/profile", "/orders", "/after-sales"];
const adminRoutes = ["/admin"];
const themes = ["classic", "midnight"];
const report = [];

for (const viewport of viewports) {
  for (const theme of themes) {
    for (const account of [user, admin]) {
      const routes =
        account.role === "ADMIN"
          ? [...publicRoutes, ...adminRoutes]
          : [...publicRoutes, ...userRoutes];
      const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        colorScheme: theme === "midnight" ? "dark" : "light",
      });
      const storedUser = {
        ...account,
        theme,
        accentColor: theme === "midnight" ? "#5aa7ff" : "#d9363e",
        backgroundColor: theme === "midnight" ? "#111827" : "#f5f6f8",
      };
      await context.addInitScript(
        ({ token, storedUser }) => {
          localStorage.setItem("token", token);
          localStorage.setItem("user", JSON.stringify(storedUser));
        },
        { token: account.token, storedUser },
      );
      const page = await context.newPage();
      const runtimeErrors = [];
      page.on("pageerror", (error) => runtimeErrors.push(error.message));
      page.on("response", (response) => {
        if (response.status() >= 500)
          runtimeErrors.push(`HTTP ${response.status()} ${response.url()}`);
      });
      for (const route of routes) {
        const started = Date.now();
        let error = "";
        try {
          await page.goto(appUrl + route, {
            waitUntil: "domcontentloaded",
            timeout: 15000,
          });
          await page
            .waitForFunction(
              () => {
                const main = document.querySelector("main");
                return (
                  main &&
                  main.innerText.trim().length >= 30 &&
                  document.documentElement.dataset.theme
                );
              },
              null,
              { timeout: 5000 },
            )
            .catch(() => {});
          await page.waitForTimeout(250);
          const state = await page.evaluate(() => ({
            title: document.title,
            hasMain: !!document.querySelector("main"),
            textLength: document.body.innerText.trim().length,
            horizontalOverflow:
              document.documentElement.scrollWidth >
              document.documentElement.clientWidth + 2,
            theme: document.documentElement.dataset.theme || "",
          }));
          if (!state.hasMain || state.textLength < 30)
            error = "页面内容未正常渲染";
          if (state.horizontalOverflow)
            error = error ? `${error}；页面存在水平溢出` : "页面存在水平溢出";
          if (state.theme !== theme)
            error = error ? `${error}；主题未生效` : "主题未生效";
          const stickyState = await page.evaluate(async () => {
            if (
              document.documentElement.scrollHeight <=
              window.innerHeight + 160
            )
              return { testable: false, top: 0 };
            window.scrollTo(
              0,
              Math.min(
                720,
                document.documentElement.scrollHeight - window.innerHeight,
              ),
            );
            await new Promise((resolve) =>
              requestAnimationFrame(() => requestAnimationFrame(resolve)),
            );
            const top =
              document.querySelector(".site-chrome")?.getBoundingClientRect()
                .top ?? 999;
            window.scrollTo(0, 0);
            return { testable: true, top };
          });
          if (stickyState.testable && Math.abs(stickyState.top) > 2)
            error = error ? `${error}；全局导航未固定` : "全局导航未固定";
          const shouldCapture = [
            "/",
            "/compatibility",
            "/advisor",
            "/profile",
            "/after-sales",
            "/admin",
          ].includes(route);
          if (shouldCapture) {
            const safeRoute = route === "/" ? "home" : route.slice(1);
            await page.screenshot({
              path: path.join(
                output,
                `${viewport.name}-${theme}-${account.role.toLowerCase()}-${safeRoute}.png`,
              ),
              fullPage: false,
            });
          }
        } catch (caught) {
          error = caught instanceof Error ? caught.message : String(caught);
        }
        report.push({
          viewport: viewport.name,
          theme,
          role: account.role,
          route,
          ok: !error,
          error,
          durationMs: Date.now() - started,
        });
      }
      if (runtimeErrors.length)
        report.push({
          viewport: viewport.name,
          theme,
          role: account.role,
          route: "runtime",
          ok: false,
          error: [...new Set(runtimeErrors)].join("；"),
        });
      await context.close();
    }
  }
}
const summary = {
  generatedAt: new Date().toISOString(),
  total: report.length,
  passed: report.filter((item) => item.ok).length,
  failed: report.filter((item) => !item.ok).length,
  failures: report.filter((item) => !item.ok),
  cases: report,
};
await writeFile(
  path.join(output, "report.json"),
  JSON.stringify(summary, null, 2),
);
console.log(
  JSON.stringify(
    {
      total: summary.total,
      passed: summary.passed,
      failed: summary.failed,
      failures: summary.failures,
    },
    null,
    2,
  ),
);
if (summary.failed) process.exitCode = 1;
let shutdownTimer;
try {
  summary.browserCleanup = await Promise.race([
    shutdown(),
    new Promise((_, reject) => {
      shutdownTimer = setTimeout(
        () => reject(new Error("测试浏览器退出超时；已保存页面检查结果")),
        10000,
      );
    }),
  ]);
  await writeFile(path.join(output, "report.json"), JSON.stringify(summary, null, 2));
} catch (error) {
  console.warn(error.message);
  process.exitCode = 1;
} finally {
  clearTimeout(shutdownTimer);
}
process.exit(process.exitCode || 0);
