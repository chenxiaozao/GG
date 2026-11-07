<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref } from "vue";
import api from "../api";
const props = defineProps<{ user: any }>();
const emit = defineEmits<{ close: []; updated: [] }>();
const dialog = ref<HTMLDialogElement | null>(null),
  busy = ref(false),
  message = ref(""),
  profile = ref<any>(null),
  audit = ref<any[]>([]);
const form = reactive({
  adminPassword: "",
  newPassword: "",
  phone: "",
  reason: "用户申请",
});
const preferenceLabels: Record<string, string> = {
  category: "关注品类",
  budget: "预算参考",
  features: "必要功能",
  scenario: "使用场景",
  minEndurance: "最低续航",
  evidenceSource: "信息来源",
};
const actionLabels: Record<string, string> = {
  VIEW_PREFERENCE: "查看偏好授权",
  RESET_PASSWORD: "重置密码",
  UPDATE_UNVERIFIED_PHONE: "维护未验证联系方式",
};
async function loadAudit() {
  try {
    audit.value = (await api.get(
      `/admin/accounts/${props.user.id}/audit`,
    )) as any[];
  } catch (e: any) {
    message.value = e.message;
  }
}
async function viewProfile() {
  busy.value = true;
  message.value = "";
  try {
    profile.value = await api.get(
      `/admin/accounts/${props.user.id}/preferences`,
    );
    await loadAudit();
  } catch (e: any) {
    message.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function act(kind: "reset-password" | "phone") {
  if (busy.value) return;
  if (
    !window.confirm(
      kind === "reset-password"
        ? "确认重置密码、撤销该用户所有现有登录并要求改密？"
        : "确认修改为未验证联系方式？这不是短信绑定。",
    )
  )
    return;
  busy.value = true;
  message.value = "";
  try {
    const body = {
      adminPassword: form.adminPassword,
      reason: form.reason,
      ...(kind === "reset-password"
        ? { newPassword: form.newPassword }
        : { phone: form.phone }),
    };
    const result: any =
      kind === "phone"
        ? await api.put(`/admin/accounts/${props.user.id}/phone`, body)
        : await api.post(
            `/admin/accounts/${props.user.id}/reset-password`,
            body,
          );
    message.value = result.message;
    form.newPassword = "";
    form.adminPassword = "";
    form.phone = "";
    emit("updated");
    await loadAudit();
  } catch (e: any) {
    message.value = e.message;
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  dialog.value?.showModal();
  loadAudit();
});
onBeforeUnmount(() => {
  form.adminPassword = "";
  form.newPassword = "";
  dialog.value?.close();
});
</script>
<template>
  <dialog
    ref="dialog"
    class="account-maintenance-dialog"
    aria-labelledby="account-maintenance-title"
    @cancel.prevent="!busy && emit('close')"
  >
    <header>
      <div>
        <h2 id="account-maintenance-title">账户维护 · {{ user.username }}</h2>
        <p>只维护普通用户；敏感操作需再次验证管理员密码。</p>
      </div>
      <button
        type="button"
        class="btn secondary"
        :disabled="busy"
        @click="emit('close')"
        aria-label="关闭账户维护"
      >
        关闭
      </button>
    </header>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <div class="account-maintenance-grid">
      <section>
        <h3>密码与联系方式</h3>
        <label
          >本次操作原因<select class="input" v-model="form.reason">
            <option
              v-for="reason in ['用户申请', '账号找回', '资料纠错', '安全处理']"
              :key="reason"
            >
              {{ reason }}
            </option>
          </select></label
        >
        <label
          >你的管理员密码<input
            class="input"
            type="password"
            autocomplete="current-password"
            v-model="form.adminPassword"
        /></label>
        <form @submit.prevent="act('reset-password')">
          <label
            >新临时密码<input
              class="input"
              type="password"
              autocomplete="new-password"
              minlength="12"
              maxlength="64"
              v-model="form.newPassword"
              required
          /></label>
          <p class="muted">
            至少12位，重置后必须改密；系统无法查看原密码。请通过可信渠道告知用户，勿写入操作原因。
          </p>
          <button class="btn" :disabled="busy || !form.adminPassword">
            重置并撤销旧登录
          </button>
        </form>
        <form @submit.prevent="act('phone')">
          <label
            >新手机号（留空会清除）<input
              class="input"
              inputmode="tel"
              maxlength="11"
              v-model="form.phone"
              placeholder="只填联系方式，不是短信验证"
          /></label>
          <p class="muted">
            当前：{{ user.maskedPhone || "未填写" }} ·
            {{
              user.phoneVerified ? "已验证" : "未验证"
            }}。本功能不会建立手机号登录绑定。
          </p>
          <button class="btn secondary" :disabled="busy || !form.adminPassword">
            保存未验证联系方式
          </button>
        </form>
      </section>
      <section>
        <h3>选购偏好档案</h3>
        <p class="muted">
          只有用户主动允许管理员查看才展示，不开放聊天原文。查看行为也会记录。
        </p>
        <button class="btn secondary" :disabled="busy" @click="viewProfile">
          按用户授权查看
        </button>
        <template v-if="profile"
          ><p v-if="!profile.visible" class="empty">
            用户未授权查看，档案内容不会返回。
          </p>
          <template v-else
            ><p>
              {{
                profile.confirmed ? "用户已确认" : "待用户确认，不是事实结论"
              }}
              · {{ profile.updatedAt || "暂无记录" }}
            </p>
            <dl class="preference-values">
              <template v-for="(value, key) in profile.preferences" :key="key"
                ><dt>{{ preferenceLabels[String(key)] || key }}</dt>
                <dd>
                  {{ Array.isArray(value) ? value.join("、") : value }}
                </dd></template
              >
            </dl></template
          ></template
        >
        <h3>最近操作记录</h3>
        <p v-if="!audit.length" class="muted">暂无记录</p>
        <ul class="account-audit-list">
          <li v-for="row in audit" :key="row.id">
            {{ row.createdAt }} · 操作员 #{{ row.actorId }} ·
            {{ actionLabels[row.action] || row.action }} · {{ row.reason }}
          </li>
        </ul>
      </section>
    </div>
  </dialog>
</template>
