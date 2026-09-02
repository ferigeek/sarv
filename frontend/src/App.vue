<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'
</script>

<template>
  <main class="boot-shell" data-testid="app-shell">
    <header class="panel boot-brand">
      <AppIcon name="smile" :size="32" class="boot-brand-icon" />
      <h1 class="boot-title">SARV</h1>
      <p class="boot-status">/ system initializing…</p>
    </header>
  </main>
</template>

<style scoped>
.boot-shell {
  height: 100%;
  display: grid;
  place-items: center;
}

.boot-brand {
  display: grid;
  grid-template-columns: auto auto;
  grid-template-rows: auto auto;
  align-items: center;
  gap: var(--sarv-space-2) var(--sarv-space-3);
  padding: var(--sarv-space-6) var(--sarv-space-7);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
}

.boot-title {
  font-size: 2rem;
  letter-spacing: 0.35em;
  color: var(--sarv-green);
  text-shadow: var(--sarv-glow);
}

.boot-status {
  grid-column: 1 / -1;
  justify-self: center;
  color: var(--sarv-text-dim);
  animation: sarv-blink 1.6s steps(2) infinite;
}

.boot-brand-icon {
  color: var(--sarv-green);
}

@keyframes sarv-blink {
  50% {
    opacity: 0;
  }
}
</style>