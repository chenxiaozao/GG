<script setup lang="ts">
defineProps<{ title: string; description?: string; actionLabel?: string; to?: string }>();
</script>

<template>
  <div class="service-empty" role="status">
    <span class="service-empty-mark" aria-hidden="true">◇</span>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <router-link v-if="to && actionLabel" class="btn secondary" :to="to">{{ actionLabel }}</router-link>
    <slot />
  </div>
</template>
