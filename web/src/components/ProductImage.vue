<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { resourceUrl } from "../resourceUrl";

defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    product: {
      name?: string;
      image?: string;
      imageKind?: string;
      imageNote?: string;
    };
    showBadge?: boolean;
    eager?: boolean;
  }>(),
  { showBadge: false, eager: false },
);
const emit = defineEmits<{ unavailable: [value: boolean] }>();
const failed = ref(false);
const source = computed(() =>
  failed.value || !props.product.image
    ? resourceUrl()
    : resourceUrl(props.product.image),
);
const label = computed(() => {
  if (failed.value || !props.product.image) return "图片暂缺";
  if (props.product.imageKind === "REFERENCE") return "参考图";
  if (
    props.product.imageKind === "ILLUSTRATION" ||
    /\.svg(?:$|\?)/.test(props.product.image)
  )
    return "示意图";
  return "";
});
watch(
  () => props.product.image,
  () => {
    failed.value = false;
    emit("unavailable", !props.product.image);
  },
);
function onError() {
  // Stop after one fallback, even if the placeholder itself cannot be loaded.
  if (failed.value) return;
  failed.value = true;
  emit("unavailable", true);
}
</script>

<template>
  <img
    v-bind="$attrs"
    :src="source"
    :alt="`${product.name || '商品'}${label ? `（${label}）` : ''}`"
    :loading="eager ? 'eager' : 'lazy'"
    decoding="async"
    @error="onError"
  />
  <span
    v-if="showBadge && label"
    class="product-media-badge"
    :title="product.imageNote"
  >
    {{ label }}
  </span>
</template>

<style scoped>
.product-media-badge {
  position: absolute;
  bottom: 9px;
  left: 9px;
  padding: 3px 8px;
  border: 1px solid #dce2e8;
  border-radius: 6px;
  background: #fffffff0;
  color: #526071;
  font-size: 11px;
  line-height: 1.4;
  pointer-events: none;
}
</style>
