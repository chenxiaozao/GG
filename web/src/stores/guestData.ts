/** 描述访客购物车记录。 */
export type GuestCartItem = {
  productId: number;
  quantity: number;
  selectedSku: string;
};

const CART_KEY = "robotmall-guest-cart";
const FAVORITE_KEY = "robotmall-guest-favorites";
const HISTORY_KEY = "robotmall-guest-history";

/** 读取浏览器存储，失败时返回默认值。 */
function read<T>(key: string, fallback: T): T {
  try {
    return JSON.parse(localStorage.getItem(key) || "") as T;
  } catch {
    return fallback;
  }
}

/** 序列化并保存访客数据。 */
function write<T>(key: string, value: T) {
  localStorage.setItem(key, JSON.stringify(value));
}

/** 读取访客购物车。 */
export function getGuestCart() {
  return read<GuestCartItem[]>(CART_KEY, []).filter((item) =>
    Number.isFinite(item.productId),
  );
}

/** 新增或合并访客购物车明细。 */
export function addGuestCart(
  productId: number,
  quantity: number,
  selectedSku: string,
) {
  const cart = getGuestCart();
  const existing = cart.find(
    (item) => item.productId === productId && item.selectedSku === selectedSku,
  );
  if (existing) existing.quantity += quantity;
  else cart.push({ productId, quantity, selectedSku });
  write(CART_KEY, cart.slice(0, 100));
}

/** 更新指定商品规格的数量。 */
export function updateGuestCart(
  productId: number,
  selectedSku: string,
  quantity: number,
) {
  const cart = getGuestCart();
  const item = cart.find(
    (entry) =>
      entry.productId === productId && entry.selectedSku === selectedSku,
  );
  if (item) item.quantity = quantity;
  write(CART_KEY, cart);
}

/** 移除指定商品规格。 */
export function removeGuestCart(productId: number, selectedSku: string) {
  write(
    CART_KEY,
    getGuestCart().filter(
      (item) =>
        item.productId !== productId || item.selectedSku !== selectedSku,
    ),
  );
}

/** 清空访客购物车。 */
export function clearGuestCart() {
  localStorage.removeItem(CART_KEY);
}

/** 读取访客收藏编号。 */
export function getGuestFavorites() {
  return read<number[]>(FAVORITE_KEY, []).filter(Number.isFinite);
}

/** 切换商品收藏状态。 */
export function toggleGuestFavorite(productId: number) {
  const favorites = getGuestFavorites();
  const exists = favorites.includes(productId);
  write(
    FAVORITE_KEY,
    exists
      ? favorites.filter((id) => id !== productId)
      : [productId, ...favorites].slice(0, 100),
  );
  return !exists;
}

/** 移除指定收藏。 */
export function removeGuestFavorite(productId: number) {
  write(
    FAVORITE_KEY,
    getGuestFavorites().filter((id) => id !== productId),
  );
}

/** 清空访客收藏。 */
export function clearGuestFavorites() {
  localStorage.removeItem(FAVORITE_KEY);
}

/** 读取访客浏览历史。 */
export function getGuestHistory() {
  return read<number[]>(HISTORY_KEY, []).filter(Number.isFinite);
}

/** 把商品追加到最近浏览记录。 */
export function recordGuestHistory(productId: number) {
  write(
    HISTORY_KEY,
    [productId, ...getGuestHistory().filter((id) => id !== productId)].slice(
      0,
      20,
    ),
  );
}

/** 移除指定浏览记录。 */
export function removeGuestHistory(productId: number) {
  write(
    HISTORY_KEY,
    getGuestHistory().filter((id) => id !== productId),
  );
}

/** 清空访客浏览历史。 */
export function clearGuestHistory() {
  localStorage.removeItem(HISTORY_KEY);
}

/** 打包供登录后合并的数据。 */
export function guestMergePayload() {
  return {
    cart: getGuestCart(),
    favorites: getGuestFavorites(),
    history: getGuestHistory(),
  };
}

/** 清理已合并的访客本地数据。 */
export function clearAllGuestData() {
  clearGuestCart();
  clearGuestFavorites();
  clearGuestHistory();
}
