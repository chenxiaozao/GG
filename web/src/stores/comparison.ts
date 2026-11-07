import { defineStore } from "pinia";
interface ComparisonItem {
  id: number;
  name: string;
  categoryId: number;
  categoryName: string;
  type: string;
}
export const useComparison = defineStore("comparison", {
  state: () => ({ items: [] as ComparisonItem[], message: "" }),
  actions: {
    toggle(p: any) {
      this.message = "";
      const id = Number(p.id);
      if (
        !Number.isSafeInteger(id) ||
        id <= 0 ||
        !Number.isSafeInteger(Number(p.categoryId)) ||
        Number(p.categoryId) <= 0
      ) {
        this.message = "商品资料不完整，暂不能加入对比。";
        return;
      }
      if (this.items.some((x) => x.id === id)) {
        this.items = this.items.filter((x) => x.id !== id);
        return;
      }
      if (p.type !== "ROBOT") {
        this.message = "配件不加入主机参数对比，请使用配件适配。";
        return;
      }
      if (
        this.items.length &&
        Number(this.items[0].categoryId) !== Number(p.categoryId)
      ) {
        this.message = `请先完成或清空「${this.items[0].categoryName || "当前分类"}」的对比，再选择其他分类。`;
        return;
      }
      if (this.items.length >= 4) {
        this.message = "最多对比4款，请先移除一款。";
        return;
      }
      this.items.push({
        id,
        name: String(p.name || "未命名商品"),
        categoryId: Number(p.categoryId),
        categoryName: String(p.categoryName || "当前分类"),
        type: p.type,
      });
    },
    clear() {
      this.items = [];
      this.message = "";
    },
    remove(id: number) {
      this.items = this.items.filter((p) => p.id !== id);
      this.message = "";
    },
  },
});
