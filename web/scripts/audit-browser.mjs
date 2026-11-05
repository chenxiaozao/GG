import { chromium } from "playwright-core";

// Own an isolated test process; never attach to or close the user's Chrome window.
export async function launchAuditBrowser(options) {
  const server = await chromium.launchServer(options);
  let browser;
  try {
    browser = await chromium.connect(server.wsEndpoint());
  } catch (error) {
    await server.kill();
    throw error;
  }
  async function shutdown() {
    const child = server.process();
    const terminated = () => child.exitCode !== null || child.signalCode !== null;
    async function bounded(operation, milliseconds) {
      let timer;
      try {
        return await Promise.race([
          operation.then(() => true),
          new Promise((resolve) => {
            timer = setTimeout(() => resolve(false), milliseconds);
          }),
        ]);
      } finally {
        clearTimeout(timer);
      }
    }
    let forced = false;
    const graceful = await bounded(server.close(), 5000);
    // Playwright may await temporary-directory cleanup after Chrome has exited.
    // Check the owned child itself; never await that cleanup without a deadline.
    if (!graceful && !terminated()) {
      forced = true;
      await bounded(server.kill(), 2000);
    }
    if (!terminated()) throw new Error("本次专用测试浏览器进程尚未退出");
    if (!graceful)
      console.warn("专用测试浏览器已退出，但清理等待超时；页面检查报告已保留。");
    return { forced, terminated: true, cleanupTimedOut: !graceful };
  }
  return { browser, shutdown };
}
