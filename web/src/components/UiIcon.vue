<script setup lang="ts">
defineProps<{ name: string; size?: number }>();
const paths: Record<string, string> = {
  cart: "M3 3h2l2.4 11.4a2 2 0 0 0 2 1.6H18a2 2 0 0 0 2-1.6L21 7H6 M10 20h.01 M18 20h.01",
  user: "M20 21v-2a7 7 0 0 0-14 0v2 M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  orders: "M8 4H5v17h14V4h-3 M9 2h6v4H9z M8 11h8 M8 15h6",
  heart:
    "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z",
  history: "M3 11a9 9 0 1 1 2.6 7 M3 4v7h7 M12 7v5l3 2",
  settings: "M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6",
  palette:
    "M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 1.2-3.6 1.5 1.5 0 0 1 .8-2.7h2a4 4 0 0 0 4-4C21 6.6 17 3 12 3 M7 9h.01 M11 6h.01 M16 8h.01",
  search: "M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  close: "M6 6l12 12 M18 6 6 18",
  arrow: "M4 12h16 M13 5l7 7-7 7",
  compare: "M8 3v18 M16 3v18 M4 7h8 M12 17h8",
  spark: "m12 3 2.6 6.4L21 12l-6.4 2.6L12 21l-2.6-6.4L3 12l6.4-2.6Z",
  location:
    "M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0 M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
  lock: "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3",
};
</script>

<template>
  <svg
    class="ui-icon"
    :width="size || 20"
    :height="size || 20"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.arrow" />
  </svg>
</template>
