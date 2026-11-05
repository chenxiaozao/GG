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
} = useCollection("favorites");
</script>

<template>
  <div class="container content-medium collection-page">
    <div class="record-heading">
      <div>
        <h1>我的收藏</h1>
        <p>
          {{
            auth.logged
              ? "收藏数据仅属于当前登录账号"
              : "游客收藏保存在本机，登录后自动合并"
          }}
        </p>
      </div>
      <button
        v-if="products.length"
        class="btn secondary"
        :disabled="busy || loading"
        @click="clearAll"
      >
        清空收藏
      </button>
    </div>
    <div v-if="!auth.logged && products.length" class="guest-data-tip">
      <span>游客收藏</span>
      <p>登录后同步到你的账号，不会覆盖原有收藏。</p>
      <router-link :to="{ path: '/login', query: { redirect: '/favorites' } }"
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
      ><span>已选择 {{ selectedIds.length }} 件</span
      ><button
        class="btn secondary"
        :disabled="busy || !selectedIds.length"
        @click="removeSelected"
      >
        移除所选
      </button>
    </div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div v-if="loading" class="empty">正在加载收藏…</div>
    <ProductGrid
      v-else
      :products="products"
      manageable
      selectable
      :busy="busy || loading"
      :selected-ids="selectedIds"
      action-label="取消收藏"
      @remove="remove"
      @toggle-select="toggleSelect"
    />
    <EmptyState
      v-if="!loading && !loadError && !products.length"
      title="还没有收藏商品"
      description="在商品详情点击收藏，把感兴趣的型号留在这里，方便下次继续比较。"
      action-label="去发现商品"
      to="/products"
    />
  </div>
</template>
