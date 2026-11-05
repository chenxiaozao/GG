<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import api from "../api";
import EmptyState from "../components/EmptyState.vue";

const orders = ref<any[]>([]);
const requests = ref<any[]>([]);
const loading = ref(true);
const submitting = ref(false);
const actingId = ref<number | null>(null);
const loadError = ref("");
const message = ref("");
const form = reactive({
  orderId: "",
  orderItemId: "",
  type: "RETURN",
  reason: "",
  description: "",
});
const logistics = reactive<
  Record<number, { company: string; logisticsNo: string }>
>({});

const eligibleOrders = computed(() =>
  orders.value.filter((entry) =>
    ["PAID", "SHIPPED", "COMPLETED"].includes(entry.order.status),
  ),
);
const selectedEntry = computed(() =>
  eligibleOrders.value.find((entry) => String(entry.order.id) === form.orderId),
);
const statusText: Record<string, string> = {
  SUBMITTED: "已提交",
  REVIEWING: "审核中",
  WAITING_RETURN: "待寄回",
  RETURN_SHIPPED: "寄回途中",
  PROCESSING: "处理中",
  COMPLETED: "已完成",
  REJECTED: "未通过",
  CANCELLED: "已取消",
};
const typeText: Record<string, string> = {
  REFUND: "仅退款",
  RETURN: "退货退款",
  REPAIR: "维修",
};
const steps = [
  "SUBMITTED",
  "REVIEWING",
  "WAITING_RETURN",
  "RETURN_SHIPPED",
  "PROCESSING",
  "COMPLETED",
];

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    [orders.value, requests.value] = (await Promise.all([
      api.get("/orders"),
      api.get("/after-sales"),
    ])) as any;
  } catch (e: any) {
    loadError.value = e?.message || "售后信息加载失败";
  } finally {
    loading.value = false;
  }
}
function chooseOrder() {
  form.orderItemId = "";
}
async function submit() {
  if (submitting.value || actingId.value !== null) return;
  if (form.description.trim().length < 5) {
    message.value = "问题说明请填写至少 5 个字";
    return;
  }
  message.value = "";
  if (!form.orderId || !form.orderItemId) {
    message.value = "请先选择订单和商品";
    return;
  }
  submitting.value = true;
  try {
    await api.post("/after-sales", {
      ...form,
      orderId: Number(form.orderId),
      orderItemId: Number(form.orderItemId),
    });
    Object.assign(form, {
      orderId: "",
      orderItemId: "",
      type: "RETURN",
      reason: "",
      description: "",
    });
    message.value = "售后申请已提交，可在售后记录中查看进度";
    await load();
  } catch (e: any) {
    message.value = e?.message || "提交失败";
  } finally {
    submitting.value = false;
  }
}
async function cancel(id: number) {
  if (actingId.value !== null || submitting.value) return;
  if (!confirm("确认取消这笔售后申请？")) return;
  actingId.value = id;
  try {
    await api.post(`/after-sales/${id}/cancel`);
    message.value = "已取消售后申请";
    await load();
  } catch (e: any) {
    message.value = e?.message || "操作失败";
  } finally {
    actingId.value = null;
  }
}
async function submitLogistics(id: number) {
  if (actingId.value !== null || submitting.value) return;
  const draft = logistics[id];
  if (!draft?.company.trim() || (draft?.logisticsNo.trim().length || 0) < 5) {
    message.value = "请填写物流公司及至少 5 位的单号";
    return;
  }
  actingId.value = id;
  try {
    await api.post(`/after-sales/${id}/logistics`, {
      company: draft.company.trim(),
      logisticsNo: draft.logisticsNo.trim(),
    });
    message.value = "退回物流已提交";
    delete logistics[id];
    await load();
  } catch (e: any) {
    message.value = e?.message || "物流提交失败";
  } finally {
    actingId.value = null;
  }
}
function progress(request: any) {
  if (["REJECTED", "CANCELLED"].includes(request.status)) return 0;
  return Math.max(0, steps.indexOf(request.status));
}
onMounted(load);
</script>

<template>
  <div class="service-page">
    <div class="service-hero">
      <div class="container">
        <span>AFTER-SALES SERVICE</span>
        <h1>售后服务中心</h1>
        <p>申请退款、退货或维修，并跟踪每一个处理节点。</p>
      </div>
    </div>
    <div class="container">
      <div class="notice" role="status" v-if="message">{{ message }}</div>
    </div>
    <div v-if="loadError" class="container collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div class="container service-layout">
      <section class="card service-form-card">
        <div class="section-title">
          <div>
            <h2>发起售后</h2>
            <p class="muted">仅支持已支付、已发货或已完成的订单</p>
          </div>
        </div>
        <div v-if="loading" class="empty">正在加载可申请订单…</div>
        <EmptyState
          v-else-if="!eligibleOrders.length && !loadError"
          title="暂无可申请订单"
          description="已支付、已发货或已完成的订单可发起售后。"
          action-label="查看订单"
          to="/orders"
        />
        <form
          v-else-if="eligibleOrders.length"
          class="form"
          @submit.prevent="submit"
        >
          <label>选择订单</label
          ><select
            class="input"
            v-model="form.orderId"
            @change="chooseOrder"
            required
          >
            <option value="">请选择</option>
            <option
              v-for="entry in eligibleOrders"
              :key="entry.order.id"
              :value="String(entry.order.id)"
            >
              {{ entry.order.orderNo }} · ¥{{ entry.order.totalAmount }}
            </option>
          </select>
          <label>选择商品</label
          ><select
            class="input"
            v-model="form.orderItemId"
            :disabled="!selectedEntry"
            required
          >
            <option value="">请选择</option>
            <option
              v-for="item in selectedEntry?.items || []"
              :key="item.id"
              :value="String(item.id)"
            >
              {{ item.productName }} × {{ item.quantity }}
            </option>
          </select>
          <label>售后类型</label>
          <div class="service-type-grid">
            <label
              v-for="item in [
                { v: 'REFUND', n: '仅退款', d: '未收货或无需寄回' },
                { v: 'RETURN', n: '退货退款', d: '寄回商品后退款' },
                { v: 'REPAIR', n: '维修', d: '故障检测与维修' },
              ]"
              :key="item.v"
              :class="{ selected: form.type === item.v }"
              ><input type="radio" v-model="form.type" :value="item.v" /><b>{{
                item.n
              }}</b
              ><small>{{ item.d }}</small></label
            >
          </div>
          <label>申请原因</label
          ><select class="input" v-model="form.reason" required>
            <option value="">请选择</option>
            <option>商品故障</option>
            <option>配件缺失</option>
            <option>型号或规格不符</option>
            <option>物流破损</option>
            <option>不符合使用预期</option>
            <option>其他</option>
          </select>
          <label>问题说明</label
          ><textarea
            class="input"
            rows="5"
            minlength="5"
            maxlength="1000"
            v-model="form.description"
            placeholder="请说明故障表现、发生时间和已尝试的处理方式"
            required
          ></textarea>
          <button class="btn" :disabled="submitting || actingId !== null">
            {{ submitting ? "提交中…" : "提交售后申请" }}
          </button>
        </form>
      </section>

      <section class="service-list">
        <div class="section-title">
          <div>
            <h2>我的售后记录</h2>
            <p class="muted">审核、寄回、处理和完成进度会在此更新</p>
          </div>
        </div>
        <div v-if="loading" class="empty">正在加载…</div>
        <article
          v-for="request in requests"
          :key="request.id"
          class="card service-ticket"
        >
          <header>
            <div>
              <small>{{ request.serviceNo }}</small>
              <h3>{{ request.productName }}</h3>
            </div>
            <span :class="['service-status', request.status.toLowerCase()]">{{
              statusText[request.status] || request.status
            }}</span>
          </header>
          <div class="service-meta">
            <span>{{ typeText[request.type] }}</span
            ><span>{{ request.reason }}</span
            ><span>{{
              String(request.createdAt || "")
                .replace("T", " ")
                .slice(0, 16)
            }}</span>
          </div>
          <p>{{ request.description }}</p>
          <div
            v-if="!['REJECTED', 'CANCELLED'].includes(request.status)"
            class="service-progress"
          >
            <i
              v-for="(step, index) in steps"
              :key="step"
              :class="{ done: index <= progress(request) }"
              ><b></b><small>{{ statusText[step] }}</small></i
            >
          </div>
          <div v-if="request.adminReply" class="service-reply">
            <b>平台处理说明</b>
            <p>{{ request.adminReply }}</p>
          </div>
          <form
            v-if="request.status === 'WAITING_RETURN'"
            class="service-logistics"
            @submit.prevent="submitLogistics(request.id)"
          >
            <input
              class="input"
              v-model="
                (logistics[request.id] ||= { company: '', logisticsNo: '' })
                  .company
              "
              aria-label="寄回物流公司"
              required
              maxlength="50"
              :disabled="actingId !== null"
              placeholder="物流公司"
            /><input
              class="input"
              v-model="logistics[request.id].logisticsNo"
              aria-label="寄回物流单号"
              required
              minlength="5"
              maxlength="80"
              :disabled="actingId !== null"
              placeholder="物流单号"
            /><button class="btn" :disabled="actingId !== null">
              {{ actingId === request.id ? "正在提交…" : "提交寄回物流" }}
            </button>
          </form>
          <footer v-if="['SUBMITTED', 'REVIEWING'].includes(request.status)">
            <button
              class="link"
              :disabled="actingId !== null || submitting"
              @click="cancel(request.id)"
            >
              取消申请
            </button>
          </footer>
        </article>
        <EmptyState
          v-if="!loading && !loadError && !requests.length"
          title="暂无售后记录"
          description="提交申请后，可在这里查看审核结果和处理进度。"
        />
      </section>
    </div>
  </div>
</template>
