<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import api from "../api";
import EmptyState from "../components/EmptyState.vue";

const orders = ref<any[]>([]);
const reviews = ref<any[]>([]);
const message = ref("");
const loading = ref(false);
const loadError = ref("");
const statusFilter = ref("");
const actingId = ref<number | null>(null);
const visibleOrders = computed(() =>
  orders.value.filter(
    (entry) => !statusFilter.value || entry.order.status === statusFilter.value,
  ),
);
const submittingItemId = ref<number | null>(null);
const reviewDrafts = reactive<
  Record<number, { rating: number; content: string }>
>({});
const statusText: Record<string, string> = {
  PENDING: "待支付",
  PAID: "待发货",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
};

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    [orders.value, reviews.value] = (await Promise.all([
      api.get("/orders"),
      api.get("/reviews/mine"),
    ])) as any;
  } catch (e: any) {
    loadError.value = e?.message || "订单加载失败";
  } finally {
    loading.value = false;
  }
}

function isReviewed(orderItemId: number) {
  return reviews.value.some((review) => review.orderItemId === orderItemId);
}

function reviewDraft(orderItemId: number) {
  if (!reviewDrafts[orderItemId]) {
    reviewDrafts[orderItemId] = { rating: 5, content: "" };
  }
  return reviewDrafts[orderItemId];
}

async function submitReview(orderId: number, item: any) {
  if (submittingItemId.value !== null || actingId.value !== null) return;
  const draft = reviewDraft(item.id);
  const content = draft.content.trim();
  if (!content) {
    message.value = "请先填写评价内容";
    return;
  }
  submittingItemId.value = item.id;
  try {
    await api.post("/reviews", {
      orderId,
      productId: item.productId,
      rating: draft.rating,
      content,
    });
    message.value = "评价发布成功，商品评分已更新";
    delete reviewDrafts[item.id];
    await load();
  } catch (e: any) {
    message.value = e?.message || "评价提交失败";
  } finally {
    submittingItemId.value = null;
  }
}

async function act(id: number, action: string) {
  if (actingId.value !== null || submittingItemId.value !== null) return;
  if (action === "cancel" && !confirm("确定取消这笔订单吗？")) return;
  if (action === "receive" && !confirm("请确认已收到商品，是否确认收货？"))
    return;
  actingId.value = id;
  try {
    await api.post(`/orders/${id}/${action}`);
    message.value =
      action === "pay"
        ? "模拟支付已完成"
        : action === "cancel"
          ? "订单已取消"
          : "已确认收货";
    await load();
  } catch (e: any) {
    message.value = e?.message || "操作失败";
  } finally {
    actingId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <div class="container orders-page">
    <h1>我的订单</h1>
    <nav class="page-tabs" aria-label="按订单状态筛选">
      <button
        :class="{ active: !statusFilter }"
        :aria-pressed="!statusFilter"
        @click="statusFilter = ''"
      >
        全部 {{ orders.length }}
      </button>
      <button
        v-for="(label, status) in statusText"
        :key="status"
        :class="{ active: statusFilter === status }"
        :aria-pressed="statusFilter === status"
        @click="statusFilter = String(status)"
      >
        {{ label }}
        {{ orders.filter((entry) => entry.order.status === status).length }}
      </button>
    </nav>
    <div class="notice" role="status" v-if="message">{{ message }}</div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div v-if="loading" class="empty">正在加载订单…</div>
    <div class="card" v-for="entry in visibleOrders" :key="entry.order.id">
      <div class="order-head">
        <b>{{ entry.order.orderNo }}</b>
        <span class="badge">{{
          statusText[entry.order.status] || entry.order.status
        }}</span>
      </div>
      <p class="muted">{{ entry.order.receiverInfo }}</p>
      <p v-if="entry.order.logisticsNo" class="order-logistics">
        {{ entry.order.logisticsCompany }} · {{ entry.order.logisticsNo }}
      </p>
      <div v-for="item in entry.items" :key="item.id" class="order-item">
        <p>
          <router-link :to="`/product/${item.productId}`">{{
            item.productName
          }}</router-link>
          · {{ item.selectedSku || "标准版" }} × {{ item.quantity }}　¥{{
            item.price
          }}
        </p>
        <div v-if="entry.order.status === 'COMPLETED'" class="review-box">
          <span v-if="isReviewed(item.id)" class="reviewed">✓ 已评价</span>
          <template v-else>
            <div class="review-fields">
              <label>
                评分
                <select
                  class="input review-score"
                  v-model.number="reviewDraft(item.id).rating"
                >
                  <option :value="5">5 星</option>
                  <option :value="4">4 星</option>
                  <option :value="3">3 星</option>
                  <option :value="2">2 星</option>
                  <option :value="1">1 星</option>
                </select>
              </label>
              <textarea
                class="input"
                maxlength="500"
                rows="2"
                v-model="reviewDraft(item.id).content"
                aria-label="商品评价内容"
                placeholder="分享这件商品的实际使用体验"
              ></textarea>
              <button
                class="btn"
                :disabled="submittingItemId !== null || actingId !== null"
                @click="submitReview(entry.order.id, item)"
              >
                {{ submittingItemId === item.id ? "提交中…" : "发表评价" }}
              </button>
            </div>
          </template>
        </div>
      </div>
      <b>订单金额 ¥{{ entry.order.totalAmount }}</b>
      <p class="order-actions">
        <button
          v-if="entry.order.status === 'PENDING'"
          class="btn"
          :disabled="actingId !== null || submittingItemId !== null"
          @click="act(entry.order.id, 'pay')"
        >
          模拟支付
        </button>
        <button
          v-if="entry.order.status === 'PENDING'"
          class="btn secondary"
          :disabled="actingId !== null"
          @click="act(entry.order.id, 'cancel')"
        >
          取消订单
        </button>
        <button
          v-if="entry.order.status === 'SHIPPED'"
          class="btn"
          :disabled="actingId !== null"
          @click="act(entry.order.id, 'receive')"
        >
          确认收货
        </button>
        <router-link
          v-if="['PAID', 'SHIPPED', 'COMPLETED'].includes(entry.order.status)"
          class="btn secondary"
          to="/after-sales"
          >申请售后</router-link
        >
      </p>
    </div>
    <EmptyState
      v-if="!loading && !loadError && !visibleOrders.length"
      :title="statusFilter ? '没有此状态的订单' : '还没有订单'"
      description="可以继续浏览商品，也可以切换状态查看其他订单。"
      action-label="继续选购"
      to="/products"
    />
  </div>
</template>
