<script setup lang="ts">
import markUrl from '@/assets/brand/icon_sarv_128.png'

withDefaults(defineProps<{ size?: number; alt?: string }>(), {
  size: 32,
  alt: 'Sarv logo',
})
</script>

<template>
  <img
    :src="markUrl"
    :alt="alt"
    class="sarv-mark"
    data-testid="sarv-mark"
    :width="size"
    :height="size"
  />
</template>

<style scoped>
.sarv-mark {
  display: inline-block;
  flex-shrink: 0;
  image-rendering: pixelated;
}
</style>
