<script setup lang="ts">
interface Topic {
  tag: string
  posts: string
}

const topics: Topic[] = [
  { tag: 'cypress', posts: '12.4k posts' },
  { tag: 'matrix', posts: '9.1k posts' },
  { tag: 'linux', posts: '8.3k posts' },
  { tag: 'hacker', posts: '6.7k posts' },
  { tag: 'binary', posts: '5.2k posts' },
]
</script>

<template>
  <section class="panel right-panel" data-testid="hot-topics">
    <header class="right-panel__header">
      <span class="right-panel__title">HOT TOPICS</span>
      <span class="right-panel__meta">001010</span>
    </header>
    <ol class="right-panel__list">
      <li
        v-for="(t, i) in topics"
        :key="t.tag"
        class="topic-row"
        :data-testid="`hot-topic-${i}`"
      >
        <span class="topic-row__index">0{{ i + 1 }}</span>
        <span class="topic-row__tag">#{{ t.tag }}</span>
        <span class="topic-row__posts">{{ t.posts }}</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.right-panel {
  padding: var(--sarv-space-4);
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

.topic-row {
  display: grid;
  grid-template-columns: 28px 1fr auto;
  align-items: center;
  gap: var(--sarv-space-2);
  padding: 7px 8px;
  border: 1px solid transparent;
  cursor: default;
}

.topic-row:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
}

.topic-row__index {
  font-size: 10px;
  color: var(--sarv-text-faint);
}

.topic-row__tag {
  font-size: 13px;
  color: var(--sarv-text);
  letter-spacing: 0.02em;
}

.topic-row__posts {
  font-size: 11px;
  color: var(--sarv-text-dim);
}
</style>
