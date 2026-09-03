<script setup lang="ts">
import { ref } from 'vue'

import NavigationMenu from './NavigationMenu.vue'
import PostCreateModal from './PostCreateModal.vue'
import SearchSection from './SearchSection.vue'
import UserSummary from './UserSummary.vue'

const showCreate = ref(false)
const emit = defineEmits<{ created: [id: number] }>()
</script>

<template>
  <aside class="left-sidebar" data-testid="left-sidebar">
    <section class="panel left-block" data-testid="left-search">
      <SearchSection />
    </section>

    <section class="panel left-block" data-testid="left-user-summary">
      <UserSummary />
    </section>

    <section class="panel left-block" data-testid="left-create-post">
      <button
        class="btn btn-primary left-create-btn"
        type="button"
        data-testid="left-create-post-btn"
        @click="showCreate = true"
      >
        + create post
      </button>
    </section>

    <section class="panel left-block">
      <NavigationMenu />
    </section>

    <PostCreateModal v-if="showCreate" @close="showCreate = false" @created="(id) => emit('created', id)" />
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

.left-create-btn {
  width: 100%;
  justify-content: center;
}
</style>
