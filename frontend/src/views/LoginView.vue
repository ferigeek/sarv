<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@/api/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''

  if (!username.value.trim() || !password.value) {
    error.value = 'Username and password are required.'
    return
  }

  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    const redirect = route.query.redirect as string | undefined
    await router.push(redirect ?? { name: 'feed' })
  } catch (e) {
    const apiErr = e as ApiError
    if (apiErr.status === 401) {
      error.value = 'Invalid username or password.'
    } else if (apiErr.detail) {
      error.value = apiErr.detail
    } else {
      error.value = 'Login failed. Please try again.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-view" data-testid="login-view">
    <section class="panel auth-box">
      <h1 class="auth-brand">SARV</h1>
      <p class="auth-hint">sign in to continue</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <label class="field">
          <span class="field-label">username</span>
          <input
            v-model="username"
            class="field-input"
            type="text"
            autocomplete="username"
            placeholder="ferigeek"
            data-testid="login-username"
          />
        </label>

        <label class="field">
          <span class="field-label">password</span>
          <input
            v-model="password"
            class="field-input"
            type="password"
            autocomplete="current-password"
            placeholder="••••••••"
            data-testid="login-password"
          />
        </label>

        <p v-if="error" class="auth-error" data-testid="login-error">{{ error }}</p>

        <button
          type="submit"
          class="btn btn-primary auth-submit"
          :disabled="loading"
          data-testid="login-submit"
        >
          {{ loading ? 'signing in…' : 'sign in' }}
        </button>
      </form>

      <p class="auth-switch">
        no account?
        <RouterLink to="/register" class="auth-link" data-testid="login-to-register"
          >create one</RouterLink
        >
      </p>
    </section>
  </main>
</template>

<style scoped>
.login-view {
  height: 100%;
  display: grid;
  place-items: center;
  padding: var(--sarv-space-4);
}

.auth-box {
  width: 100%;
  max-width: 380px;
  padding: var(--sarv-space-6) var(--sarv-space-6);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
}

.auth-brand {
  font-size: 2rem;
  letter-spacing: 0.35em;
  color: var(--sarv-green);
  text-shadow: var(--sarv-glow);
  text-align: center;
}

.auth-hint {
  margin-top: var(--sarv-space-2);
  color: var(--sarv-text-dim);
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.auth-form {
  margin-top: var(--sarv-space-5);
  display: grid;
  gap: var(--sarv-space-4);
}

.field {
  display: grid;
  gap: var(--sarv-space-1);
}

.field-label {
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sarv-text-dim);
}

.field-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border-bright);
  color: var(--sarv-text);
  outline: none;
}

.field-input::placeholder {
  color: var(--sarv-text-faint);
}

.field-input:focus {
  border-color: var(--sarv-green-dim);
  box-shadow: 0 0 0 1px var(--sarv-green-dim);
}

.field-textarea {
  min-height: 72px;
  resize: vertical;
}

.auth-error {
  padding: 8px 10px;
  background: color-mix(in srgb, var(--sarv-red) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--sarv-red) 40%, transparent);
  color: #ff8fa3;
  font-size: 12px;
  line-height: 1.4;
}

.auth-submit {
  justify-content: center;
  width: 100%;
  margin-top: var(--sarv-space-1);
}

.auth-switch {
  margin-top: var(--sarv-space-5);
  text-align: center;
  color: var(--sarv-text-dim);
  font-size: 12px;
}

.auth-link {
  color: var(--sarv-green);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 640px) {
  .login-view {
    padding: var(--sarv-space-3);
    padding-bottom: calc(var(--sarv-space-3) + env(safe-area-inset-bottom));
  }

  .auth-box {
    padding: var(--sarv-space-5) var(--sarv-space-4);
  }

  .auth-submit {
    min-height: 48px;
  }
}
</style>
