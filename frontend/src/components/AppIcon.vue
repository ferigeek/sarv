<script setup lang="ts">
import { useAttrs } from 'vue'
import { Icon } from '@iconify/vue/offline'

withDefaults(defineProps<{ name: string; size?: number | string }>(), {
  size: 24,
})

const attrs = useAttrs()
</script>

<template>
  <Icon
    :icon="`pixelarticons:${name}`"
    :width="size"
    :height="size"
    v-bind="attrs"
  />
</template>