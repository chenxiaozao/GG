<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{ content: string }>();
const lines = computed(() =>
  props.content
    .split("\n")
    .filter((line) => line.trim())
    .map((line) => ({
      heading: /^#{1,6}\s/.test(line),
      bullet: /^\s*[-*]\s/.test(line),
      text: line.replace(/^#{1,6}\s+|^\s*[-*]\s+/, ""),
    })),
);
function segments(text: string) {
  return text
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((value) => ({
      strong: value.startsWith("**") && value.endsWith("**"),
      text:
        value.startsWith("**") && value.endsWith("**")
          ? value.slice(2, -2)
          : value,
    }));
}
</script>
<template>
  <div class="ai-answer">
    <p
      v-for="(line, index) in lines"
      :key="index"
      :class="{
        'ai-answer-heading': line.heading,
        'ai-answer-bullet': line.bullet,
      }"
    >
      <template
        v-for="(part, partIndex) in segments(line.text)"
        :key="partIndex"
      >
        <strong v-if="part.strong">{{ part.text }}</strong
        ><span v-else>{{ part.text }}</span>
      </template>
    </p>
  </div>
</template>
<style scoped>
.ai-answer {
  border: 1px solid var(--ds-line);
  border-radius: 12px;
  padding: 14px 18px;
  background: var(--ds-surface);
  color: var(--ds-ink);
  overflow-wrap: anywhere;
}
.ai-answer > p {
  display: block;
  margin: 0 0 10px;
  padding: 0;
  border: 0;
  background: none;
  font-size: 14px;
  line-height: 1.75;
  white-space: normal;
}
.ai-answer > p:last-child {
  margin-bottom: 0;
}
.ai-answer > .ai-answer-heading {
  margin-top: 16px;
  font-weight: 700;
}
.ai-answer > .ai-answer-bullet {
  padding-left: 14px;
  position: relative;
}
.ai-answer-bullet::before {
  position: absolute;
  left: 0;
  content: "·";
  font-weight: 700;
}
</style>
