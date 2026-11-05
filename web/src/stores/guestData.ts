export type GuestCartItem = {
  productId: number;
  quantity: number;
  selectedSku: string;
};

const CART_KEY = "robotmall-guest-cart";
const FAVORITE_KEY = "robotmall-guest-favorites";
const HISTORY_KEY = "robotmall-guest-history";

function read<T>(key: string, fallback: T): T {
  try {
    return JSON.parse(localStorage.getItem(key) || "") as T;
  } catch {
    return fallback;
  }
}

function write<T>(key: string, value: T) {
  localStorage.setItem(key, JSON.stringify(value));
}

export function getGuestCart() {
  return read<GuestCartItem[]>(CART_KEY, []).filter((item) => Number.isFinite(item.productId));
}

export function addGuestCart(productId: number, quantity: number, selectedSku: string) {
  const cart = getGuestCart();
  const existing = cart.find((item) => item.productId === productId && item.selectedSku === selectedSku);
  if (existing) existing.quantity += quantity;
  else cart.push({ productId, quantity, selectedSku });
  write(CART_KEY, cart.slice(0, 100));
}

export function updateGuestCart(productId: number, selectedSku: string, quantity: number) {
  const cart = getGuestCart();
  const item = cart.find((entry) => entry.productId === productId && entry.selectedSku === selectedSku);
  if (item) item.quantity = quantity;
  write(CART_KEY, cart);
}

export function removeGuestCart(productId: number, selectedSku: string) {
  write(CART_KEY, getGuestCart().filter((item) => item.productId !== productId || item.selectedSku !== selectedSku));
}

export function clearGuestCart() {
  localStorage.removeItem(CART_KEY);
}

export function getGuestFavorites() {
  return read<number[]>(FAVORITE_KEY, []).filter(Number.isFinite);
}

export function toggleGuestFavorite(productId: number) {
  const favorites = getGuestFavorites();
  const exists = favorites.includes(productId);
  write(FAVORITE_KEY, exists ? favorites.filter((id) => id !== productId) : [productId, ...favorites].slice(0, 100));
  return !exists;
}

export function removeGuestFavorite(productId: number) {
  write(FAVORITE_KEY, getGuestFavorites().filter((id) => id !== productId));
}

export function clearGuestFavorites() {
  localStorage.removeItem(FAVORITE_KEY);
}

export function getGuestHistory() {
  return read<number[]>(HISTORY_KEY, []).filter(Number.isFinite);
}

export function recordGuestHistory(productId: number) {
  write(HISTORY_KEY, [productId, ...getGuestHistory().filter((id) => id !== productId)].slice(0, 20));
}

export function removeGuestHistory(productId: number) {
  write(HISTORY_KEY, getGuestHistory().filter((id) => id !== productId));
}

export function clearGuestHistory() {
  localStorage.removeItem(HISTORY_KEY);
}

export function guestMergePayload() {
  return { cart: getGuestCart(), favorites: getGuestFavorites(), history: getGuestHistory() };
}

export function clearAllGuestData() {
  clearGuestCart();
  clearGuestFavorites();
  clearGuestHistory();
}
