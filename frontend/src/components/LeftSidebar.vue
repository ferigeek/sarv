<script setup lang="ts">
// Phase 4 will wire search, user summary, create-post and navigation.
// This placeholder preserves the required left-sidebar order and inert behavior
// so the shell can be built and tested now.
</script>

<template>
  <aside class="left-sidebar" data-testid="left-sidebar">
    <section class="panel left-block" data-testid="left-search">
      <header class="left-block__header">SEARCH</header>
      <div class="left-block__body left-block__body--muted">search — upcoming</div>
    </section>

    <section class="panel left-block" data-testid="left-user-summary">
      <header class="left-block__header">YOU</header>
      <div class="left-block__body left-block__body--muted">user summary — upcoming</div>
    </section>

    <section class="panel left-block" data-testid="left-create-post">
      <button class="btn btn-primary left-create-btn" type="button" data-testid="left-create-post-btn">
        + create post
      </button>
    </section>

    <nav class="panel left-block" data-testid="left-navigation" aria-label="User navigation">
      <header class="left-block__header">NAVIGATION</header>
      <ul class="left-nav__list">
        <li class="left-nav__item" data-testid="left-nav-profile">view profile</li>
        <li class="left-nav__item" data-testid="left-nav-liked">liked posts</li>
        <li class="left-nav__item" data-testid="left-nav-following">following</li>
        <li class="left-nav__item" data-testid="left-nav-followers">followers</li>
      </ul>
    </nav>
  </aside>
</template>

<style scoped>
.left-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-height: 0;
  overflow-y: auto;
  background: var(--sarv-border);
}

.left-block {
  padding: var(--sarv-space-4);
  flex-shrink: 0;
}

.left-block__header {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-green);
  padding-bottom: var(--sarv-space-3);
  border-bottom: 1px solid var(--sarv-border);
  margin-bottom: var(--sarv-space-3);
}

.left-block__body {
  font-size: 12px;
  color: var(--sarv-text);
}

.left-block__body--muted {
  color: var(--sarv-text-dim);
}

.left-create-btn {
  width: 100%;
  justify-content: center;
}

.left-nav__list {
  display: grid;
  gap: 2px;
}

.left-nav__item {
  padding: 8px 8px;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--sarv-text-dim);
  border: 1px solid transparent;
  cursor: default;
}

.left-nav__item:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
  color: var(--sarv-text);
}
</style>
