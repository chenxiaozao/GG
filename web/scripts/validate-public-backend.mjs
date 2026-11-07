import { pathToFileURL } from 'node:url';

export function backendUrl(value) {
  if (!value?.trim()) return null;
  const url = new URL(value.trim());
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/api') {
    throw new Error('API_BASE_URL 必须是 https://后端域名/api，不能含凭据、查询参数或其他路径。');
  }
  if (['localhost', '127.0.0.1', '0.0.0.0', '[::1]'].includes(url.hostname)) {
    throw new Error('GitHub Pages 不能连接你的本机地址，请设置可公开访问的 HTTPS 后端。');
  }
  return url;
}

export async function verifyPublicBackend(url, origin, request = fetch) {
  const response = await request(`${url.href}/health`, { signal: AbortSignal.timeout(15000), redirect: 'error' });
  if (!response.ok) throw new Error('后端健康检查未通过，先检查云端容器、DNS 和有效 HTTPS。');
  const body = await response.json();
  if (body.code !== 200 || body.data?.status !== 'UP') throw new Error('该地址不是已就绪的商城后端。');
  const preflight = await request(`${url.href}/auth/login`, {
    method: 'OPTIONS', redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { Origin: origin, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type,x-token' },
  });
  const allowed = preflight.headers.get('access-control-allow-origin');
  const methods = (preflight.headers.get('access-control-allow-methods') || '').toLowerCase().split(/\s*,\s*/);
  const headers = (preflight.headers.get('access-control-allow-headers') || '').toLowerCase().split(/\s*,\s*/);
  if (!preflight.ok || allowed !== origin || !methods.includes('post') || !['content-type','x-token'].every(h => headers.includes(h))) {
    throw new Error('Pages 跨域预检失败：核对后端 ALLOWED_ORIGINS（仅域名来源，不含仓库路径）。');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const url = backendUrl(process.env.API_URL);
    if (!url) console.log('没有公开后端地址：将发布只读目录版，不启用账号/订单/AI。');
    else if (process.argv.includes('--check-health')) {
      await verifyPublicBackend(url, process.env.FRONTEND_ORIGIN || 'https://chenxiaozao.github.io');
      console.log('HTTPS 后端就绪，Pages 跨域预检通过（未调用 AI，也未写用户数据）。');
    }
  } catch (error) {
    // Never print the raw environment variable, which may have been mis-filled with a secret.
    console.error(error instanceof TypeError ? '后端地址或 HTTPS 连接无效，请检查域名和证书。' : error.message);
    process.exitCode = 1;
  }
}
