import { onMounted, ref } from "vue";
import api from "../api";
import { useAuthStore } from "../stores/auth";
import {
  clearGuestFavorites,
  clearGuestHistory,
  getGuestFavorites,
  getGuestHistory,
  removeGuestFavorite,
  removeGuestHistory,
} from "../stores/guestData";

export function useCollection(kind: "favorites" | "history") {
  const auth = useAuthStore();
  const products = ref<any[]>([]);
  const selectedIds = ref<number[]>([]);
  const message = ref("");
  const loading = ref(true);
  const busy = ref(false);
  const loadError = ref("");
  const label = kind === "favorites" ? "收藏" : "浏览记录";
  const readGuest = kind === "favorites" ? getGuestFavorites : getGuestHistory;
  const removeGuest =
    kind === "favorites" ? removeGuestFavorite : removeGuestHistory;
  const clearGuest =
    kind === "favorites" ? clearGuestFavorites : clearGuestHistory;
  async function load() {
    loading.value = true;
    loadError.value = "";
    try {
      if (auth.logged) {
        const rows = (await api.get(`/${kind}`)) as any[];
        products.value =
          kind === "history"
            ? rows
                .filter((r) => r.product)
                .map((r) => ({ ...r.product, lastViewedAt: r.viewTime }))
            : rows;
      } else {
        const results = await Promise.allSettled(
          readGuest().map((id) => api.get(`/products/${id}`)),
        );
        products.value = results
          .filter(
            (r): r is PromiseFulfilledResult<any> => r.status === "fulfilled",
          )
          .map((r) => r.value);
        if (results.some((r) => r.status === "rejected"))
          loadError.value =
            "部分商品暂时无法加载，请重试。你的本机记录没有被删除。";
      }
      selectedIds.value = selectedIds.value.filter((id) =>
        products.value.some((p) => Number(p.id) === id),
      );
    } catch (e: any) {
      loadError.value = e?.message || `${label}加载失败`;
    } finally {
      loading.value = false;
    }
  }
  function toggleSelect(product: any) {
    if (busy.value || loading.value) return;
    const id = Number(product.id);
    selectedIds.value = selectedIds.value.includes(id)
      ? selectedIds.value.filter((v) => v !== id)
      : [...selectedIds.value, id];
  }
  function selectAll() {
    if (!busy.value)
      selectedIds.value =
        selectedIds.value.length === products.value.length
          ? []
          : products.value.map((p) => Number(p.id));
  }
  async function removeIds(ids: number[]) {
    if (busy.value || loading.value) return;
    busy.value = true;
    try {
      const results = await Promise.allSettled(
        ids.map(async (id) => {
          if (auth.logged) await api.delete(`/${kind}/${id}`);
          else removeGuest(id);
          return id;
        }),
      );
      const removed = results
        .filter(
          (r): r is PromiseFulfilledResult<number> => r.status === "fulfilled",
        )
        .map((r) => r.value);
      products.value = products.value.filter(
        (p) => !removed.includes(Number(p.id)),
      );
      selectedIds.value = selectedIds.value.filter(
        (id) => !removed.includes(id),
      );
      message.value =
        removed.length === ids.length
          ? `已移除 ${removed.length} 项${label}`
          : `已移除 ${removed.length} 项，${ids.length - removed.length} 项未成功，请重试。`;
    } finally {
      busy.value = false;
    }
  }
  async function remove(product: any) {
    await removeIds([Number(product.id)]);
  }
  async function removeSelected() {
    if (
      busy.value ||
      !selectedIds.value.length ||
      !confirm(`确定移除选中的 ${selectedIds.value.length} 项${label}吗？`)
    )
      return;
    await removeIds([...selectedIds.value]);
  }
  async function clearAll() {
    if (
      busy.value ||
      loading.value ||
      !confirm(
        `确定清空${auth.logged ? "当前账号" : "本机游客"}的全部${label}吗？`,
      )
    )
      return;
    busy.value = true;
    try {
      if (auth.logged) await api.delete(`/${kind}`);
      else clearGuest();
      products.value = [];
      selectedIds.value = [];
      loadError.value = "";
      message.value = `已清空${auth.logged ? "当前账号" : "本机游客"}的${label}`;
    } catch (e: any) {
      message.value = e?.message || "清空失败";
    } finally {
      busy.value = false;
    }
  }
  onMounted(load);
  return {
    auth,
    products,
    selectedIds,
    message,
    loading,
    busy,
    loadError,
    load,
    toggleSelect,
    selectAll,
    remove,
    removeSelected,
    clearAll,
  };
}
