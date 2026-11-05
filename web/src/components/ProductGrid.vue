<script setup lang="ts">
import ProductImage from "./ProductImage.vue";
defineProps<{
  products: any[];
  manageable?: boolean;
  actionLabel?: string;
  selectable?: boolean;
  selectedIds?: number[];
  busy?: boolean;
  loading?: boolean;
}>();
defineEmits<{ remove: [product: any]; toggleSelect: [product: any] }>();
function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", {
    maximumFractionDigits: 2,
  });
}
function formatViewedAt(value: string) {
  return String(value).replace("T", " ").slice(0, 16);
}
</script>

<template>
  <div
    v-if="loading"
    class="grid product-skeleton-grid"
    aria-busy="true"
    aria-label="正在加载商品"
  >
    <div
      v-for="number in 6"
      :key="number"
      class="card product-skeleton"
      aria-hidden="true"
    >
      <div></div>
      <span></span><span></span><b></b>
    </div>
  </div>
  <div v-else class="grid">
    <article
      v-for="product in products"
      :key="product.id"
      class="card product-card"
      :class="{ 'product-card-manage': manageable }"
    >
      <button
        v-if="selectable"
        :disabled="busy"
        class="product-select"
        :class="{ selected: selectedIds?.includes(Number(product.id)) }"
        type="button"
        @click="$emit('toggleSelect', product)"
        :aria-pressed="selectedIds?.includes(Number(product.id)) || false"
        :aria-label="`${selectedIds?.includes(Number(product.id)) ? '取消选择' : '选择'} ${product.name}`"
      >
        {{ selectedIds?.includes(Number(product.id)) ? "✓" : "" }}
      </button>
      <router-link class="product-card-link" :to="`/product/${product.id}`">
        <div class="product-image-wrap">
          <ProductImage :product="product" show-badge /><span
            v-if="product.stock > 0 && product.stock < 20"
            class="stock-tag"
            >仅剩 {{ product.stock }} 件</span
          >
        </div>
        <div class="product-meta">
          <span>{{ product.brandName || "严选品牌" }}</span
          ><span>{{
            product.categoryName ||
            (product.type === "ACCESSORY" ? "机器人配件" : "机器人")
          }}</span>
        </div>
        <h3>{{ product.name }}</h3>
        <div class="product-subtitle">
          {{ product.subtitle || "专业机器人商品" }}
        </div>
        <div class="product-price-row">
          <div class="price"><i>¥</i>{{ formatPrice(product.price) }}</div>
          <del v-if="Number(product.marketPrice) > Number(product.price)"
            >¥{{ formatPrice(product.marketPrice) }}</del
          >
        </div>
        <div class="product-stats">
          <span class="rating"
            >★ {{ Number(product.rating || 0).toFixed(1) }}</span
          ><span>演示销量 {{ product.sales || 0 }}</span>
        </div>
        <p v-if="product.lastViewedAt" class="product-viewed-at">
          最近浏览 {{ formatViewedAt(product.lastViewedAt) }}
        </p>
        <div v-else class="product-card-entry">
          了解这款商品 <span aria-hidden="true">↗</span>
        </div>
      </router-link>
      <button
        v-if="manageable"
        :disabled="busy"
        class="product-manage-action"
        @click="$emit('remove', product)"
      >
        {{ actionLabel || "移除" }}
      </button>
    </article>
  </div>
</template>
