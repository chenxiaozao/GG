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
/** 用服务端响应刷新本人选购画像。 */
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
/** 读取本人画像。 */
async function load() {
  try {
    apply(await api.get("/profile/preferences"));
  } catch (e: any) {
    message.value = e.message;
  }
}
/** 提交用户确认后的偏好。 */
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
/** 清除画像并刷新页面状态。 */
async function clear() {
  if (!window.confirm("清空选购偏好？后续有效咨询仍可整理新的偏好。")) return;
  busy.value = true;
  try {
    await api.delete("/profile/preferences");
    await load();
    message.value = "选购偏好已清空";
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
      画像只整理你明确表达的品类、预算、必要功能和使用场景。管理员可以查看画像，但看不到聊天正文。你可随时修改或清空；已确认的偏好不会被聊天自动改写。
    </p>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <button v-if="!state" class="btn secondary" @click="load">
      重新加载档案设置
    </button>
    <template v-else>
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
              ><input type="checkbox" :value="name" v-model="form.features" />{{
                name
              }}</label
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
            清空档案
          </button>
        </div>
      </form>
    </template>
  </section>
</template>
