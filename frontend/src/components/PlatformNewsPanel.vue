<script setup lang="ts">
interface NewsItem {
  version: string
  title: string
  date: string
}

const items: NewsItem[] = [
  { version: 'v0.3', title: 'Feed ranking added', date: '2026-08-14' },
  { version: 'v0.2', title: 'JWT auth & media upload', date: '2026-07-30' },
  { version: 'v0.1', title: 'Sarv bootstrap', date: '2026-07-10' },
]
</script>

<template>
  <section class="panel right-panel right-panel--alt" data-testid="platform-news">
    <header class="right-panel__header">
      <span class="right-panel__title">PLATFORM // NEWS</span>
      <span class="right-panel__meta">SYS.LOG</span>
    </header>
    <ol class="right-panel__list">
      <li
        v-for="(it, i) in items"
        :key="it.version"
        class="news-row"
        :data-testid="`platform-news-${i}`"
      >
        <span class="news-row__version">{{ it.version }}</span>
        <span class="news-row__title">{{ it.title }}</span>
        <span class="news-row__date">{{ it.date }}</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.right-panel {
  padding: var(--sarv-space-4);
}

.right-panel--alt {
  background: var(--sarv-bg-alt);
}

.right-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--sarv-space-3);
  border-bottom: 1px solid var(--sarv-border);
  margin-bottom: var(--sarv-space-3);
}

.right-panel__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-green);
}

.right-panel__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.right-panel__list {
  display: grid;
  gap: 2px;
}

.news-row {
  display: grid;
  grid-template-columns: 46px 1fr auto;
  align-items: center;
  gap: var(--sarv-space-2);
  padding: 7px 8px;
  border: 1px solid transparent;
  cursor: default;
}

.news-row:hover {
  background: var(--sarv-panel);
  border-color: var(--sarv-border);
}

.news-row__version {
  font-size: 11px;
  font-weight: 700;
  color: var(--sarv-green-dim);
  letter-spacing: 0.06em;
}

.news-row__title {
  font-size: 12px;
  color: var(--sarv-text);
}

.news-row__date {
  font-size: 10px;
  color: var(--sarv-text-dim);
}
</style>
