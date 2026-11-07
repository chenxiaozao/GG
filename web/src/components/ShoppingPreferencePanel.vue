<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import api from "../api";
const state = ref<any>(null),
  busy = ref(false),
  message = ref("");
const form = reactive({
  category: "",
  budget: "" as string | number,
  features: [] as string[],
  scenario: "",
  minEndurance: 0,
});
const categories = [
  "地面清洁",
  "擦窗机器人",
  "庭院与泳池",
  "陪伴娱乐",
  "航拍无人机",
  "教育编程",
  "四足机器人",
  "人形机器人",
  "商用服务",
];
const features = ["自动集尘", "避障", "防缠绕", "拖地", "编程", "遥控"];
function apply(value: any) {
  state.value = value;
  const p = value.preferences || {};
  Object.assign(form, {
    category: p.category || "",
    budget: p.budget ?? "",
    features: p.features || [],
    scenario: [
      "家庭清洁",
      "养宠清洁",
      "儿童学习选购",
      "户外航拍",
      "其他选购",
    ].includes(p.scenario)
      ? p.scenario
      : "",
    minEndurance: p.minEndurance || 0,
  });
}
async function load() {
  try {
    apply(await api.get("/profile/preferences"));
  } catch (e: any) {
    message.value = e.message;
  }
}
async function toggleCollection(event: Event) {
  const input = event.target as HTMLInputElement;
  const next = input.checked;
  input.checked = state.value.collectionEnabled;
  await consent(next, state.value.adminSharing);
}
async function toggleSharing(event: Event) {
  const input = event.target as HTMLInputElement;
  const next = input.checked;
  input.checked = state.value.adminSharing;
  await consent(true, next);
}
async function consent(enabled: boolean, sharing: boolean) {
  if (busy.value) return;
  if (
    !enabled &&
    !window.confirm("关闭收集会同时清空已保存的选购偏好，是否继续？")
  )
    return;
  busy.value = true;
  message.value = "";
  try {
    apply(
      await api.put("/profile/preferences/consent", {
        enabled,
        adminSharing: sharing,
      }),
    );
    message.value = enabled ? "偏好设置已更新" : "已关闭并清空档案";
  } catch (e: any) {
    message.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function save() {
  busy.value = true;
  message.value = "";
  try {
    apply(await api.put("/profile/preferences", form));
    message.value = "偏好已由你确认，后续咨询不会悄悄覆盖";
  } catch (e: any) {
    message.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function clear() {
  if (!window.confirm("删除档案并撤销收集及管理员查看授权？")) return;
  busy.value = true;
  try {
    await api.delete("/profile/preferences");
    await load();
    message.value = "档案与授权均已删除";
  } catch (e: any) {
    message.value = e.message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="preference-panel">
    <h2 tabindex="-1">我的选购偏好</h2>
    <p class="muted">
      默认关闭。只记录你明确说出的品类、预算、必要功能和使用场景；本系统不保存聊天原文，不猜测收入、身份或健康信息，也不会自动更改推荐条件。AI
      问答本身会将有效咨询发送至模型服务，请勿输入隐私。
    </p>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <button v-if="!state" class="btn secondary" @click="load">
      重新加载档案设置
    </button>
    <template v-else>
      <label class="privacy-switch"
        ><input
          type="checkbox"
          :checked="state.collectionEnabled"
          :disabled="busy"
          @change="toggleCollection"
        />允许从之后的选购咨询整理偏好</label
      >
      <label class="privacy-switch"
        ><input
          type="checkbox"
          :checked="state.adminSharing"
          :disabled="busy || !state.collectionEnabled"
          @change="toggleSharing"
        />另外允许管理员查看这份档案（可单独关闭）</label
      >
      <template v-if="state.collectionEnabled">
        <p class="preference-state">
          {{ state.confirmed ? "已由你确认" : "待你确认，不是事实结论" }} ·
          {{ state.updatedAt || "尚无记录" }}
        </p>
        <p v-if="state.preferences?.scenario" class="muted">
          待确认场景：{{ state.preferences.scenario }}
        </p>
        <form class="preference-form" @submit.prevent="save">
          <label
            >关注品类<select class="input" v-model="form.category">
              <option value="">暂不限</option>
              <option v-for="name in categories" :key="name">{{ name }}</option>
            </select></label
          >
          <label
            >预算参考（元）<input
              class="input"
              type="number"
              min="0"
              max="1000000"
              v-model="form.budget"
              placeholder="可留空"
          /></label>
          <label
            >使用场景<select class="input" v-model="form.scenario">
              <option value="">不记录</option>
              <option
                v-for="name in [
                  '家庭清洁',
                  '养宠清洁',
                  '儿童学习选购',
                  '户外航拍',
                  '其他选购',
                ]"
                :key="name"
              >
                {{ name }}
              </option>
            </select></label
          >
          <label
            >最低续航（分钟，0为不记录）<input
              class="input"
              type="number"
              min="0"
              max="1440"
              v-model.number="form.minEndurance"
          /></label>
          <fieldset>
            <legend>必要功能</legend>
            <div class="required-features">
              <label v-for="name in features" :key="name"
                ><input
                  type="checkbox"
                  :value="name"
                  v-model="form.features"
                />{{ name }}</label
              >
            </div>
          </fieldset>
          <p class="muted preference-wide">
            来源：{{
              state.preferences?.evidenceSource || "尚未记录"
            }}。只把明确表达整理为待确认项；你可以在此纠正。
          </p>
          <div class="preference-actions preference-wide">
            <button class="btn" :disabled="busy">确认并保存我的偏好</button
            ><button
              type="button"
              class="btn secondary"
              :disabled="busy"
              @click="clear"
            >
              删除档案与授权
            </button>
          </div>
        </form>
      </template>
      <p v-else class="empty">当前不收集选购偏好，管理员也无法查看。</p>
    </template>
  </section>
</template>
