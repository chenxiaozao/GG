<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import api from "../api";
import EmptyState from "../components/EmptyState.vue";

const dashboard = ref<any>(null);
const users = ref<any[]>([]);
const products = ref<any[]>([]);
const orders = ref<any[]>([]);
const categories = ref<any[]>([]);
const brands = ref<any[]>([]);
const afterSales = ref<any[]>([]);
const tab = ref("dash");
const message = ref("");
const loading = ref(true);
const loadError = ref("");
const busy = ref("");
const editingId = ref<number | null>(null);
const editor = ref<HTMLDialogElement | null>(null);
const editorError = ref("");
watch(editorError, (error) => {
  if (error)
    nextTick(() =>
      editor.value
        ?.querySelector(".inline-error")
        ?.scrollIntoView({ block: "center" }),
    );
});
const query = ref("");
const productState = ref("");
const productCategory = ref("");
const productPage = ref(1);
const pageSize = 12;
const orderQuery = ref("");
const orderStatus = ref("");
const shippingId = ref<number | null>(null);
const shippingForm = reactive({ logisticsCompany: "", logisticsNo: "" });
const shippingError = ref("");
const userQuery = ref("");
const serviceStatus = ref("");
const statusText: Record<string, string> = {
  PENDING: "待支付",
  PAID: "待发货",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
};
const afterSaleStatusText: Record<string, string> = {
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
const afterSaleDrafts = reactive<
  Record<number, { status: string; adminReply: string; approvedAmount: number }>
>({});
const defaults = () => ({
  name: "",
  subtitle: "",
  type: "ROBOT",
  price: 0,
  marketPrice: null,
  stock: 0,
  rating: 0,
  sales: 0,
  image: "/products/placeholder.svg",
  enabled: false,
  description: "",
  parametersJson: "{}",
  enduranceMinutes: 0,
  payloadKg: 0,
  intelligenceLevel: "MEDIUM",
  skuOptions: "标准版",
  categoryId: "",
  brandId: "",
  sourceUrl: "",
  warranty: "",
  shippingNote: "",
});
const form = reactive<any>(defaults());
const matches = (values: unknown[], text: string) =>
  values.join(" ").toLowerCase().includes(text.trim().toLowerCase());
const filteredProducts = computed(() =>
  products.value.filter(
    (p) =>
      matches([p.name, p.subtitle, p.brandName, p.categoryName], query.value) &&
      (!productState.value || String(p.enabled) === productState.value) &&
      (!productCategory.value ||
        String(p.categoryId) === productCategory.value),
  ),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filteredProducts.value.length / pageSize)),
);
const pagedProducts = computed(() =>
  filteredProducts.value.slice(
    (productPage.value - 1) * pageSize,
    productPage.value * pageSize,
  ),
);
const filteredOrders = computed(() =>
  orders.value.filter(
    (o) =>
      matches([o.orderNo, o.userId, o.logisticsNo], orderQuery.value) &&
      (!orderStatus.value || o.status === orderStatus.value),
  ),
);
const filteredUsers = computed(() =>
  users.value.filter((u) => matches([u.username, u.nickname], userQuery.value)),
);
const filteredServices = computed(() =>
  afterSales.value.filter(
    (s) => !serviceStatus.value || s.status === serviceStatus.value,
  ),
);
watch([query, productState, productCategory], () => {
  productPage.value = 1;
});
watch(pages, (count) => {
  productPage.value = Math.min(productPage.value, count);
});
watch(tab, () => {
  shippingId.value = null;
  shippingError.value = "";
});
const money = (amount: unknown) =>
  Number(amount || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
const dateText = (value: unknown) =>
  String(value || "—")
    .replace("T", " ")
    .slice(0, 16);
const maxDailySales = () =>
  Math.max(
    1,
    ...(dashboard.value?.dailySales || []).map((item: any) =>
      Number(item.amount || 0),
    ),
  );
const maxCategorySales = () =>
  Math.max(
    1,
    ...Object.values(dashboard.value?.categorySales || {}).map(Number),
  );

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    [
      dashboard.value,
      users.value,
      products.value,
      orders.value,
      categories.value,
      brands.value,
      afterSales.value,
    ] = (await Promise.all([
      api.get("/admin/dashboard"),
      api.get("/admin/users"),
      api.get("/admin/products"),
      api.get("/admin/orders"),
      api.get("/categories"),
      api.get("/brands"),
      api.get("/admin/after-sales"),
    ])) as any;
  } catch (e: any) {
    loadError.value = e?.message || "管理数据加载失败，请重试";
  } finally {
    loading.value = false;
  }
}
async function openEditor(product?: any) {
  if (product?.id)
    product = products.value.find((p) => p.id === product.id) || product;
  editingId.value = product?.id ?? null;
  const values = defaults();
  for (const key of Object.keys(values))
    form[key] = product?.[key] ?? (values as any)[key];
  editorError.value = "";
  await nextTick();
  editor.value?.showModal();
  if (editor.value) editor.value.scrollTop = 0;
}
function closeEditor() {
  if (busy.value === "save") return;
  editor.value?.close();
}
async function save() {
  if (busy.value) return;
  editorError.value = "";
  try {
    const parameters = JSON.parse(form.parametersJson || "{}");
    if (
      !parameters ||
      Array.isArray(parameters) ||
      typeof parameters !== "object"
    )
      throw new Error();
  } catch {
    editorError.value =
      '分类参数需要填写 JSON 对象，例如：{"续航时间":"120分钟"}';
    return;
  }
  busy.value = "save";
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      categoryId: Number(form.categoryId),
      brandId: Number(form.brandId),
    };
    if (editingId.value)
      await api.put("/admin/products/" + editingId.value, payload);
    else await api.post("/admin/products", payload);
    message.value = editingId.value ? "商品修改已保存" : "商品已新增";
    editor.value?.close();
    await load();
  } catch (e: any) {
    editorError.value = e?.message || "保存失败，填写内容已保留";
  } finally {
    busy.value = "";
  }
}
async function remove(product: any) {
  if (
    busy.value ||
    !product.enabled ||
    !confirm("确认下架“" + product.name + "”？历史订单不会受影响。")
  )
    return;
  busy.value = "product-" + product.id;
  try {
    await api.delete("/admin/products/" + product.id);
    message.value = "商品已下架";
    await load();
  } catch (e: any) {
    message.value = e?.message || "下架失败";
  } finally {
    busy.value = "";
  }
}
async function toggle(user: any) {
  if (busy.value || user.role === "ADMIN") return;
  if (
    !confirm(
      "确认" +
        (user.enabled ? "禁用" : "启用") +
        "账号“" +
        user.username +
        "”？",
    )
  )
    return;
  busy.value = "user-" + user.id;
  try {
    await api.put("/admin/users/" + user.id + "/toggle");
    message.value = "用户状态已更新";
    await load();
  } catch (e: any) {
    message.value = e?.message || "操作失败";
  } finally {
    busy.value = "";
  }
}
function startShipping(order: any) {
  shippingId.value = order.id;
  Object.assign(shippingForm, { logisticsCompany: "", logisticsNo: "" });
  shippingError.value = "";
  nextTick(() =>
    document.getElementById("shipping-company-" + order.id)?.focus(),
  );
}
async function ship(order: any) {
  if (busy.value) return;
  const payload = {
    logisticsCompany: shippingForm.logisticsCompany.trim(),
    logisticsNo: shippingForm.logisticsNo.trim(),
  };
  if (!payload.logisticsCompany || !payload.logisticsNo) {
    shippingError.value = "物流公司和单号不能为空";
    return;
  }
  busy.value = "ship-" + order.id;
  shippingError.value = "";
  try {
    await api.post("/admin/orders/" + order.id + "/ship", payload);
    shippingId.value = null;
    message.value = "发货信息已保存，订单进入待收货状态";
    await load();
  } catch (e: any) {
    shippingError.value = e?.message || "发货失败，物流信息已保留";
  } finally {
    busy.value = "";
  }
}
function afterSaleDraft(item: any) {
  if (!afterSaleDrafts[item.id])
    afterSaleDrafts[item.id] = {
      status: item.status === "SUBMITTED" ? "REVIEWING" : item.status,
      adminReply: item.adminReply || "",
      approvedAmount: Number(item.approvedAmount ?? item.requestedAmount ?? 0),
    };
  return afterSaleDrafts[item.id];
}
function allowedAfterSaleStatuses(item: any): string[] {
  if (Array.isArray(item.allowedStatuses)) return item.allowedStatuses;
  // Compatibility with older servers; the current server remains the authority.
  const options: Record<string, string[]> = {
    SUBMITTED: ["REVIEWING", "WAITING_RETURN", "PROCESSING", "REJECTED"],
    REVIEWING: ["REVIEWING", "WAITING_RETURN", "PROCESSING", "REJECTED"],
    WAITING_RETURN: ["WAITING_RETURN", "REJECTED"],
    RETURN_SHIPPED: ["RETURN_SHIPPED", "PROCESSING", "REJECTED"],
    PROCESSING: ["PROCESSING", "COMPLETED", "REJECTED"],
  };
  return (options[item.status] || []).filter(status =>
    !(item.type === "REFUND" && status === "WAITING_RETURN") &&
    !(item.type === "RETURN" && status === "PROCESSING" && ["SUBMITTED", "REVIEWING"].includes(item.status)),
  );
}
async function updateAfterSale(item: any) {
  if (busy.value) return;
  const draft = afterSaleDraft(item);
  if (!draft.adminReply.trim()) {
    message.value = "请填写售后处理说明";
    return;
  }
  if (
    !Number.isFinite(draft.approvedAmount) ||
    draft.approvedAmount < 0 ||
    draft.approvedAmount > Number(item.requestedAmount || 0)
  ) {
    message.value = "核准金额不能超过申请金额或小于零";
    return;
  }
  busy.value = "service-" + item.id;
  try {
    await api.put("/admin/after-sales/" + item.id, {
      ...draft,
      adminReply: draft.adminReply.trim(),
    });
    delete afterSaleDrafts[item.id];
    message.value = "售后进度已更新";
    await load();
  } catch (e: any) {
    message.value = e?.message || "售后处理失败";
  } finally {
    busy.value = "";
  }
}
function imageFallback(event: Event) {
  const img = event.target as HTMLImageElement;
  if (!img.src.endsWith("/products/placeholder.svg"))
    img.src = "/products/placeholder.svg";
}
onMounted(load);
</script>

<template>
  <div class="container admin-page">
    <header class="admin-heading">
      <div>
        <span class="service-eyebrow">运营工作空间</span>
        <h1>商城运营中心</h1>
        <p>管理商品、订单与服务，让每笔操作都有明确去向。</p>
      </div>
      <button class="btn secondary" :disabled="loading || !!busy" @click="load">
        刷新数据
      </button>
    </header>
    <div v-if="message" class="notice" role="status">{{ message }}</div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <nav class="page-tabs" aria-label="运营管理模块">
      <button
        v-for="item in [
          { id: 'dash', name: '工作台' },
          { id: 'product', name: '商品管理' },
          { id: 'user', name: '用户管理' },
          { id: 'order', name: '订单管理' },
          { id: 'service', name: '售后管理' },
        ]"
        :key="item.id"
        :class="{ active: tab === item.id }"
        :aria-pressed="tab === item.id"
        @click="tab = item.id"
      >
        {{ item.name
        }}<small v-if="item.id === 'service' && dashboard?.afterSalePending">
          {{ dashboard.afterSalePending }}</small
        >
      </button>
    </nav>
    <p v-if="loading" class="service-loading" role="status">
      正在更新管理数据…
    </p>
    <template v-if="tab === 'dash' && dashboard">
      <div class="stat-grid admin-stat-grid">
        <div
          v-for="item in [
            { name: '用户', value: dashboard.totalUsers },
            { name: '商品', value: dashboard.totalProducts },
            { name: '订单', value: dashboard.totalOrders },
            { name: '待处理售后', value: dashboard.afterSalePending },
            { name: '有效订单转化', value: dashboard.paidConversionRate + '%' },
            { name: '已完成销售额', value: '¥' + money(dashboard.totalSales) },
          ]"
          :key="item.name"
          class="stat"
        >
          <span>{{ item.name }}</span
          ><strong>{{ item.value }}</strong>
        </div>
      </div>
      <div class="admin-dashboard-grid">
        <section class="card admin-chart">
          <h2>近 7 日有效订单金额</h2>
          <div
            v-if="
              !dashboard.dailySales.some((day: any) => Number(day.amount) > 0)
            "
            class="chart-empty"
          >
            近 7 日暂无有效订单金额
          </div>
          <div v-else class="sales-bars">
            <div v-for="day in dashboard.dailySales" :key="day.date">
              <b
                :style="{
                  height: (Number(day.amount) * 100) / maxDailySales() + '%',
                }"
                :title="'¥' + money(day.amount)"
              ></b
              ><span>{{ day.date.slice(5) }}</span
              ><small>{{ day.orders }} 单</small>
            </div>
          </div>
        </section>
        <section class="card admin-chart">
          <h2>订单状态分布</h2>
          <div class="metric-list">
            <p
              v-for="(count, status) in dashboard.statusDistribution"
              :key="status"
            >
              <span>{{ statusText[String(status)] || status }}</span
              ><i
                ><b
                  :style="{
                    width:
                      (Number(count) * 100) /
                        Math.max(1, dashboard.totalOrders) +
                      '%',
                  }"
                ></b></i
              ><strong>{{ count }}</strong>
            </p>
            <div v-if="!dashboard.totalOrders" class="chart-empty">
              暂无订单
            </div>
          </div>
        </section>
        <section class="card admin-chart">
          <h2>分类销售件数</h2>
          <div class="metric-list">
            <p v-for="(count, name) in dashboard.categorySales" :key="name">
              <span>{{ name }}</span
              ><i
                ><b
                  :style="{
                    width: (Number(count) * 100) / maxCategorySales() + '%',
                  }"
                ></b></i
              ><strong>{{ count }}</strong>
            </p>
            <div
              v-if="!Object.keys(dashboard.categorySales).length"
              class="chart-empty"
            >
              暂无销售数据
            </div>
          </div>
        </section>
        <section class="card admin-chart">
          <h2>低库存预警</h2>
          <div v-if="dashboard.lowStockProducts.length" class="low-stock-list">
            <p v-for="item in dashboard.lowStockProducts" :key="item.id">
              <button
                class="text-action"
                @click="
                  tab = 'product';
                  openEditor(item);
                "
              >
                {{ item.name }}</button
              ><b>{{ item.stock }} 件</b>
            </p>
          </div>
          <div v-else class="chart-empty">当前无低库存商品</div>
        </section>
      </div>
    </template>

    <section v-if="tab === 'product'" class="admin-catalog">
      <div class="admin-tools">
        <label class="tool-search"
          ><span class="sr-only">搜索商品</span
          ><input
            class="input"
            v-model="query"
            placeholder="搜索名称、型号或品牌" /></label
        ><label
          ><span class="sr-only">商品状态</span
          ><select class="input" v-model="productState">
            <option value="">全部状态</option>
            <option value="true">上架商品</option>
            <option value="false">下架商品</option>
          </select></label
        ><label
          ><span class="sr-only">商品分类</span
          ><select class="input" v-model="productCategory">
            <option value="">全部分类</option>
            <option v-for="c in categories" :key="c.id" :value="String(c.id)">
              {{ c.name }}
            </option>
          </select></label
        ><button class="btn" :disabled="!!busy" @click="openEditor()">
          新增商品
        </button>
      </div>
      <div class="admin-list-meta">
        <span>找到 {{ filteredProducts.length }} 件商品</span
        ><button
          v-if="query || productState || productCategory"
          class="text-action"
          @click="
            query = '';
            productState = '';
            productCategory = '';
          "
        >
          清除筛选
        </button>
      </div>
      <div
        v-if="filteredProducts.length"
        class="table-wrap admin-table-scroll"
        tabindex="0"
        aria-label="商品列表，可横向滚动"
      >
        <table class="table">
          <thead>
            <tr>
              <th>商品</th>
              <th>分类 / 品牌</th>
              <th>价格</th>
              <th>库存</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in pagedProducts" :key="p.id">
              <td>
                <div class="admin-product-cell">
                  <img
                    :src="resourceUrl(p.image)"
                    alt=""
                    @error="imageFallback"
                  />
                  <div>
                    <b>{{ p.name }}</b
                    ><small>#{{ p.id }}</small>
                  </div>
                </div>
              </td>
              <td>
                {{ p.categoryName || "—"
                }}<small class="table-subline">{{ p.brandName || "—" }}</small>
              </td>
              <td>¥{{ money(p.price) }}</td>
              <td :class="{ 'danger-text': p.stock < 6 }">{{ p.stock }}</td>
              <td>
                <span class="status-pill" :class="{ inactive: !p.enabled }">{{
                  p.enabled ? "上架" : "下架"
                }}</span>
              </td>
              <td>
                <div class="table-actions">
                  <button
                    class="text-action"
                    :disabled="!!busy"
                    @click="openEditor(p)"
                  >
                    编辑</button
                  ><button
                    v-if="p.enabled"
                    class="text-action destructive"
                    :disabled="!!busy"
                    @click="remove(p)"
                  >
                    下架
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-else-if="!loading && !loadError"
        title="没有匹配的商品"
        description="试试调整关键词、分类或上架状态。"
      />
      <nav v-if="pages > 1" class="list-pagination" aria-label="商品列表翻页">
        <span>第 {{ productPage }} / {{ pages }} 页</span
        ><button
          class="btn secondary"
          :disabled="productPage === 1"
          @click="productPage--"
        >
          上一页</button
        ><button
          class="btn secondary"
          :disabled="productPage === pages"
          @click="productPage++"
        >
          下一页
        </button>
      </nav>
    </section>

    <section v-if="tab === 'user'">
      <div class="admin-tools">
        <label class="tool-search"
          ><span class="sr-only">搜索用户</span
          ><input
            class="input"
            v-model="userQuery"
            placeholder="搜索账号或昵称" /></label
        ><span class="muted">{{ filteredUsers.length }} 个账号</span>
      </div>
      <div
        v-if="filteredUsers.length"
        class="table-wrap admin-table-scroll"
        tabindex="0"
        aria-label="用户列表，可横向滚动"
      >
        <table class="table">
          <thead>
            <tr>
              <th>账号</th>
              <th>昵称</th>
              <th>角色</th>
              <th>状态</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in filteredUsers" :key="u.id">
              <td>{{ u.username }}</td>
              <td>{{ u.nickname }}</td>
              <td>{{ u.role === "ADMIN" ? "管理员" : "普通用户" }}</td>
              <td>
                <span class="status-pill" :class="{ inactive: !u.enabled }">{{
                  u.enabled ? "启用" : "禁用"
                }}</span>
              </td>
              <td>{{ dateText(u.createTime) }}</td>
              <td>
                <button
                  class="text-action"
                  :disabled="u.role === 'ADMIN' || !!busy"
                  @click="toggle(u)"
                >
                  {{
                    u.role === "ADMIN"
                      ? "管理员受保护"
                      : u.enabled
                        ? "禁用账号"
                        : "启用账号"
                  }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-else-if="!loading && !loadError"
        title="没有匹配的账号"
        description="试试其他账号名称或昵称。"
      />
    </section>

    <section v-if="tab === 'order'" class="admin-order-list">
      <div class="admin-tools">
        <label class="tool-search"
          ><span class="sr-only">搜索订单</span
          ><input
            class="input"
            v-model="orderQuery"
            placeholder="搜索订单号、用户 ID 或物流单号" /></label
        ><label
          ><span class="sr-only">订单状态</span
          ><select class="input" v-model="orderStatus">
            <option value="">全部状态</option>
            <option
              v-for="(label, status) in statusText"
              :key="status"
              :value="status"
            >
              {{ label }}
            </option>
          </select></label
        ><span class="muted">{{ filteredOrders.length }} 笔订单</span>
      </div>
      <article v-for="o in filteredOrders" :key="o.id" class="card admin-order">
        <header class="order-head">
          <b>{{ o.orderNo }}</b
          ><span class="status-pill">{{
            statusText[o.status] || o.status
          }}</span>
        </header>
        <div class="order-facts">
          <span>用户 #{{ o.userId }}</span
          ><span>{{ dateText(o.createdAt) }}</span
          ><strong>¥{{ money(o.totalAmount) }}</strong>
        </div>
        <p class="order-receiver">{{ o.receiverInfo || "未提供收货信息" }}</p>
        <p v-if="o.logisticsNo" class="order-logistics">
          {{ o.logisticsCompany }} · {{ o.logisticsNo }}
        </p>
        <button
          v-if="o.status === 'PAID' && shippingId !== o.id"
          class="btn secondary"
          :disabled="!!busy"
          @click="startShipping(o)"
        >
          填写物流并发货
        </button>
        <form
          v-if="shippingId === o.id"
          class="shipping-form"
          @submit.prevent="ship(o)"
        >
          <h3>填写本次发货信息</h3>
          <div class="shipping-fields">
            <label
              ><span>物流公司</span
              ><input
                :id="'shipping-company-' + o.id"
                class="input"
                v-model="shippingForm.logisticsCompany"
                required
                maxlength="50"
                :disabled="!!busy"
                placeholder="例如：顺丰速运" /></label
            ><label
              ><span>物流单号</span
              ><input
                class="input"
                v-model="shippingForm.logisticsNo"
                required
                maxlength="80"
                :disabled="!!busy"
                placeholder="填写实际或演示物流单号"
            /></label>
          </div>
          <p v-if="shippingError" class="inline-error" role="alert">
            {{ shippingError }}
          </p>
          <footer>
            <button
              class="btn secondary"
              type="button"
              :disabled="!!busy"
              @click="shippingId = null"
            >
              取消</button
            ><button class="btn" :disabled="!!busy">
              {{ busy === "ship-" + o.id ? "正在保存…" : "确认发货" }}
            </button>
          </footer>
        </form>
      </article>
      <EmptyState
        v-if="!loading && !loadError && !filteredOrders.length"
        title="暂无匹配订单"
        description="用户提交订单后会显示在这里，也可以调整搜索条件。"
      />
    </section>

    <section v-if="tab === 'service'" class="admin-service-list">
      <div class="admin-tools">
        <label
          ><span class="sr-only">售后状态</span
          ><select class="input" v-model="serviceStatus">
            <option value="">全部售后状态</option>
            <option
              v-for="(label, status) in afterSaleStatusText"
              :key="status"
              :value="status"
            >
              {{ label }}
            </option>
          </select></label
        ><span class="muted">{{ filteredServices.length }} 笔申请</span>
      </div>
      <article
        v-for="item in filteredServices"
        :key="item.id"
        class="card admin-service-card"
      >
        <header>
          <div>
            <small>{{ item.serviceNo }}</small>
            <h3>{{ item.productName }}</h3>
          </div>
          <span class="status-pill">{{
            afterSaleStatusText[item.status] || item.status
          }}</span>
        </header>
        <div class="service-meta">
          <span>订单 #{{ item.orderId }}</span
          ><span>{{ typeText[item.type] || item.type }}</span
          ><span>{{ item.reason }}</span
          ><span>申请金额 ¥{{ money(item.requestedAmount) }}</span>
        </div>
        <p class="muted">{{ item.description }}</p>
        <p v-if="item.returnLogisticsNo">
          寄回物流：{{ item.returnLogisticsCompany }} ·
          {{ item.returnLogisticsNo }}
        </p>
        <form
          v-if="!['COMPLETED', 'REJECTED', 'CANCELLED'].includes(item.status)"
          class="admin-service-form"
          @submit.prevent="updateAfterSale(item)"
        >
          <label
            ><span>处理状态</span
            ><select
              class="input"
              v-model="afterSaleDraft(item).status"
              aria-label="处理状态"
              :disabled="!!busy"
            >
              <option v-for="status in allowedAfterSaleStatuses(item)" :key="status" :value="status">
                {{ afterSaleStatusText[status] || status }}
              </option>
            </select></label
          ><label
            ><span>核准退款金额</span
            ><input
              class="input"
              type="number"
              min="0"
              :max="item.requestedAmount || 0"
              step="0.01"
              v-model.number="afterSaleDraft(item).approvedAmount"
              aria-label="核准退款金额"
              :disabled="!!busy"
              required /></label
          ><label class="service-reply-field"
            ><span>处理说明</span
            ><textarea
              class="input"
              rows="2"
              maxlength="1000"
              v-model="afterSaleDraft(item).adminReply"
              aria-label="处理说明"
              :disabled="!!busy"
              required
              placeholder="说明处理结果及用户下一步需要做什么"
            ></textarea></label
          ><button class="btn" :disabled="!!busy">
            {{ busy === "service-" + item.id ? "正在保存…" : "更新进度" }}
          </button>
        </form>
        <div v-else-if="item.adminReply" class="service-reply">
          <b>处理说明</b>
          <p>{{ item.adminReply }}</p>
        </div>
      </article>
      <EmptyState
        v-if="!loading && !loadError && !filteredServices.length"
        title="暂无匹配售后申请"
        description="新的申请及后续处理记录会显示在这里。"
      />
    </section>

    <dialog
      ref="editor"
      class="product-editor"
      aria-labelledby="product-editor-title"
      @cancel.prevent="closeEditor"
    >
      <form @submit.prevent="save">
        <header class="editor-header">
          <div>
            <span class="service-eyebrow">商品资料</span>
            <h2 id="product-editor-title">
              {{ editingId ? "编辑商品" : "新增商品" }}
            </h2>
          </div>
          <button
            class="editor-close"
            type="button"
            aria-label="关闭商品编辑"
            :disabled="busy === 'save'"
            @click="closeEditor"
          >
            ×
          </button>
        </header>
        <div class="editor-body">
          <p v-if="editorError" class="inline-error" role="alert">
            {{ editorError }}
          </p>
          <fieldset :disabled="busy === 'save'">
            <legend>基本信息</legend>
            <div class="editor-grid">
              <label class="editor-wide"
                ><span>商品名称 *</span
                ><input
                  class="input"
                  v-model="form.name"
                  maxlength="120"
                  required /></label
              ><label class="editor-wide"
                ><span>一句话介绍</span
                ><input
                  class="input"
                  v-model="form.subtitle"
                  maxlength="240" /></label
              ><label
                ><span>商品类型</span
                ><select class="input" v-model="form.type">
                  <option value="ROBOT">机器人主机</option>
                  <option value="ACCESSORY">配件</option>
                </select></label
              ><label
                ><span>商品分类 *</span
                ><select class="input" v-model="form.categoryId" required>
                  <option value="">请选择</option>
                  <option v-for="c in categories" :key="c.id" :value="c.id">
                    {{ c.name }}
                  </option>
                </select></label
              ><label
                ><span>品牌 *</span
                ><select class="input" v-model="form.brandId" required>
                  <option value="">请选择</option>
                  <option v-for="b in brands" :key="b.id" :value="b.id">
                    {{ b.name }}
                  </option>
                </select></label
              ><label class="editor-checkbox"
                ><input type="checkbox" v-model="form.enabled" /><span
                  >在商城上架显示</span
                ></label
              >
            </div>
          </fieldset>
          <fieldset :disabled="busy === 'save'">
            <legend>价格与规格</legend>
            <div class="editor-grid">
              <label
                ><span>商城价格（元）*</span
                ><input
                  class="input"
                  type="number"
                  min="0"
                  step="0.01"
                  v-model.number="form.price"
                  required /></label
              ><label
                ><span>参考价格（元）</span
                ><input
                  class="input"
                  type="number"
                  :min="form.price"
                  step="0.01"
                  v-model.number="form.marketPrice" /></label
              ><label
                ><span>库存（件）*</span
                ><input
                  class="input"
                  type="number"
                  min="0"
                  step="1"
                  v-model.number="form.stock"
                  required /></label
              ><label
                ><span>配置选项</span
                ><input
                  class="input"
                  v-model="form.skuOptions"
                  placeholder="标准版,高配版"
                /><small>多种规格用英文逗号分隔</small></label
              ><label
                ><span>续航（分钟）</span
                ><input
                  class="input"
                  type="number"
                  min="0"
                  v-model.number="form.enduranceMinutes" /></label
              ><label
                ><span>载重（kg）</span
                ><input
                  class="input"
                  type="number"
                  min="0"
                  step="0.1"
                  v-model.number="form.payloadKg" /></label
              ><label
                ><span>智能程度</span
                ><select class="input" v-model="form.intelligenceLevel">
                  <option value="MEDIUM">基础智能</option>
                  <option value="HIGH">高级智能</option>
                  <option value="PRO">专业智能</option>
                </select></label
              >
            </div>
          </fieldset>
          <fieldset :disabled="busy === 'save'">
            <legend>图片与服务说明</legend>
            <div class="editor-grid">
              <label class="editor-wide"
                ><span>图片路径或公开图片链接</span
                ><input class="input" v-model="form.image" /><small
                  >使用项目内图片路径或允许引用的公开图片地址</small
                ></label
              ><label class="editor-wide"
                ><span>商品资料来源</span
                ><input
                  class="input"
                  v-model="form.sourceUrl"
                  placeholder="品牌官网或公开商品资料地址" /></label
              ><label
                ><span>保修说明</span
                ><input class="input" v-model="form.warranty" /></label
              ><label
                ><span>配送说明</span
                ><input class="input" v-model="form.shippingNote" /></label
              ><label class="editor-wide"
                ><span>产品说明</span
                ><textarea
                  class="input"
                  rows="3"
                  v-model="form.description"
                  maxlength="5000"
                ></textarea>
              </label>
            </div>
          </fieldset>
          <details class="editor-advanced">
            <summary>高级：分类参数</summary>
            <label
              ><span>参数 JSON</span
              ><textarea
                class="input code-input"
                aria-label="参数 JSON"
                rows="5"
                v-model="form.parametersJson"
                :disabled="busy === 'save'"
                placeholder='{"续航时间":"120分钟"}'
              ></textarea
              ><small
                >填写键值对象，用于商品详情与参数对比。不填写时保留 {}。</small
              ></label
            >
          </details>
        </div>
        <footer class="editor-footer">
          <span>* 为必填项</span>
          <div>
            <button
              class="btn secondary"
              type="button"
              :disabled="busy === 'save'"
              @click="closeEditor"
            >
              取消</button
            ><button class="btn" :disabled="busy === 'save'">
              {{
                busy === "save"
                  ? "正在保存…"
                  : editingId
                    ? "保存修改"
                    : "创建商品"
              }}
            </button>
          </div>
        </footer>
      </form>
    </dialog>
  </div>
</template>
