import { reactive } from 'vue';

export const backendState = reactive({ offline: import.meta.env.VITE_OFFLINE_ONLY === 'true', snapshotAt: '', checking: false });
type Snapshot = { generatedAt: string; products: any[]; categories: any[]; brands: any[] };
let cached: Promise<Snapshot> | undefined;
async function snapshot() {
  cached ??= fetch(`${import.meta.env.BASE_URL}catalog-snapshot.json`).then(async response => {
    if (!response.ok) throw new Error('离线目录尚未生成，请开启后端或联系维护者');
    const value = await response.json();
    if (!Array.isArray(value.products) || !Array.isArray(value.categories) || !Array.isArray(value.brands)) throw new Error('离线目录格式不正确');
    return value as Snapshot;
  }).catch(error => { cached = undefined; throw error; });
  return cached;
}
export function supportsOffline(url: string, method = 'get') {
  const path = url.split('?')[0];
  return (method.toLowerCase() === 'get' && (/^\/(categories|brands|products|advisor\/ai-status)$/.test(path) || /^\/products\/\d+(\/reviews|\/accessories)?$/.test(path)))
    || (method.toLowerCase() === 'post' && path === '/products/compare');
}
export async function offlineResponse(url: string, params: Record<string, any> = {}, body?: any) {
  const data = await snapshot(); backendState.offline = true; backendState.snapshotAt = data.generatedAt;
  const path = url.split('?')[0];
  if (path === '/categories') return data.categories;
  if (path === '/brands') return data.brands;
  if (path === '/advisor/ai-status') return { configured: false, offline: true };
  if (path === '/products/compare') {
    const ids = typeof body === 'string' ? JSON.parse(body) : body;
    if (!Array.isArray(ids) || ids.length < 2 || ids.length > 4 || new Set(ids).size !== ids.length) throw new Error('请选择2至4款不重复的同类主机');
    const products = ids.map(id => data.products.find(p => p.id === id));
    if (products.some(p => !p || p.type !== 'ROBOT') || new Set(products.map(p => p.categoryId)).size !== 1) throw new Error('只能比较同类别主机');
    return products;
  }
  const item = path.match(/^\/products\/(\d+)(.*)$/);
  if (item) {
    const product = data.products.find(p => p.id === Number(item[1]));
    if (!product) throw new Error('离线目录中没有该商品');
    // Never invent reviews or official compatibility relations.
    if (item[2]) return [];
    return product;
  }
  const p = { ...Object.fromEntries(new URLSearchParams(url.split('?')[1] || '')), ...params };
  const list = data.products.filter(product => {
    const keyword = String(p.keyword || '').toLowerCase();
    return (!keyword || `${product.name} ${product.subtitle || ''} ${product.brandName || ''}`.toLowerCase().includes(keyword))
      && (!p.categoryId || product.categoryId === Number(p.categoryId)) && (!p.brandId || product.brandId === Number(p.brandId))
      && (p.minPrice == null || p.minPrice === '' || product.price >= Number(p.minPrice))
      && (p.maxPrice == null || p.maxPrice === '' || product.price <= Number(p.maxPrice))
      && (!p.minEndurance || product.enduranceMinutes >= Number(p.minEndurance))
      && (!p.minPayload || product.payloadKg >= Number(p.minPayload))
      && (!p.intelligence || product.intelligenceLevel === p.intelligence);
  });
  return list.sort((a,b) => p.sort === 'priceAsc' ? a.price-b.price : p.sort === 'priceDesc' ? b.price-a.price : p.sort === 'rating' ? b.rating-a.rating : b.sales-a.sales);
}
export async function retryBackend() {
  backendState.checking = true;
  try {
    const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
    const response = await fetch(`${base}/categories`, { signal: AbortSignal.timeout(5000), cache: 'no-store' });
    const body = await response.json();
    if (!response.ok || body.code !== 200) throw new Error('后端仍未开启');
    window.location.reload();
  } finally { backendState.checking = false; }
}
