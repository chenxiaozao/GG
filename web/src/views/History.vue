<script setup lang="ts">
import ProductGrid from "../components/ProductGrid.vue";
import EmptyState from "../components/EmptyState.vue";
import { useCollection } from "../composables/useCollection";
const {
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
} = useCollection("history");
</script>

<template>
  <div class="container content-medium collection-page history-page">
    <div class="record-heading">
      <div>
        <h1>浏览记录</h1>
        <p>
          {{
            auth.logged
              ? "浏览数据仅属于当前登录账号"
              : "游客记录保存在本机，登录后自动合并"
          }}
        </p>
      </div>
      <button
        v-if="products.length"
        class="btn secondary"
        :disabled="busy || loading"
        @click="clearAll"
      >
        清空记录
      </button>
    </div>
    <div v-if="!auth.logged && products.length" class="guest-data-tip">
      <span>本机浏览记录</span>
      <p>登录后将同步到你的账号，最多保留 20 件最近商品。</p>
      <router-link :to="{ path: '/login', query: { redirect: '/history' } }"
        >登录并同步 →</router-link
      >
    </div>
    <div class="notice" role="status" v-if="message">{{ message }}</div>
    <div v-if="products.length" class="batch-toolbar">
      <label
        ><input
          type="checkbox"
          :disabled="busy || loading"
          :checked="selectedIds.length === products.length"
          @change="selectAll"
        />
        全选</label
      ><span>已选择 {{ selectedIds.length }} 条</span
      ><button
        class="btn secondary"
        :disabled="busy || !selectedIds.length"
        @click="removeSelected"
      >
        删除所选
      </button>
    </div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div v-if="loading" class="empty">正在加载浏览记录…</div>
    <ProductGrid
      v-else
      :products="products"
      manageable
      selectable
      :busy="busy || loading"
      :selected-ids="selectedIds"
      action-label="删除记录"
      @remove="remove"
      @toggle-select="toggleSelect"
    />
    <EmptyState
      v-if="!loading && !loadError && !products.length"
      title="从一次浏览开始"
      description="你看过的商品会按最近浏览顺序保存在这里，随时可以返回查看或删除记录。"
      action-label="浏览商品"
      to="/products"
    />
  </div>
</template>
