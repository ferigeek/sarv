<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { ApiError } from '@/api/client'
import { uploadMedia } from '@/api/media'
import { updateMe } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { Gender } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const step = ref<1 | 2>(1)

/* Step 1 — mandatory */
const username = ref('')
const password = ref('')
const email = ref('')
const displayName = ref('')
const gender = ref<Gender>('RATHER_NOT_TO_SAY')
const error = ref('')
const loading = ref(false)

/* kept for step 2 update */
const registeredGender = ref<Gender>('RATHER_NOT_TO_SAY')
const registeredDisplayName = ref('')

/* Step 2 — optional */
const bio = ref('')
const location = ref('')
const file = ref<File | null>(null)
const fileName = ref('')

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0] ?? null
  file.value = f
  fileName.value = f?.name ?? ''
  error.value = ''
}

async function onStep1Submit() {
  error.value = ''

  if (
    !username.value.trim() ||
    !password.value ||
    !email.value.trim() ||
    !displayName.value.trim() ||
    !gender.value
  ) {
    error.value = 'All mandatory fields are required.'
    return
  }

  if (password.value.length < 8) {
    error.value = 'Password must be at least 8 characters.'
    return
  }

  loading.value = true
  try {
    await auth.register({
      username: username.value.trim(),
      password: password.value,
      email: email.value.trim(),
      displayName: displayName.value.trim(),
      gender: gender.value,
    })
    registeredGender.value = gender.value
    registeredDisplayName.value = displayName.value.trim()
    step.value = 2
    error.value = ''
  } catch (e) {
    const apiErr = e as ApiError
    error.value = apiErr.detail || 'Registration failed. Please try again.'
  } finally {
    loading.value = false
  }
}

async function onStep2Submit() {
  error.value = ''
  loading.value = true
  try {
    let profilePictureId: number | null = null

    if (file.value) {
      const media = await uploadMedia(file.value)
      profilePictureId = media.id
    }

    const hasOptional =
      bio.value.trim() !== '' || location.value.trim() !== '' || profilePictureId !== null

    if (hasOptional) {
      await updateMe({
        displayName: registeredDisplayName.value || auth.user?.displayName || displayName.value.trim(),
        gender: registeredGender.value,
        bio: bio.value.trim() || null,
        location: location.value.trim() || null,
        profilePictureId,
      })
      // keep auth user in sync if profile was updated
      if (auth.token) await auth.fetchMe()
    }

    const redirect = route.query.redirect as string | undefined
    await router.push(redirect ?? { name: 'feed' })
  } catch (e) {
    const apiErr = e as ApiError
    error.value = apiErr.detail || 'Could not save profile. Please try again.'
  } finally {
    loading.value = false
  }
}

async function onSkip() {
  const redirect = route.query.redirect as string | undefined
  await router.push(redirect ?? { name: 'feed' })
}
</script>

<template>
  <main class="register-view" data-testid="register-view">
    <section class="panel auth-box">
      <h1 class="auth-brand">SARV</h1>

      <!-- Step 1 -->
      <template v-if="step === 1">
        <p class="auth-hint">create your account — step 1 of 2</p>

        <form class="auth-form" @submit.prevent="onStep1Submit">
          <label class="field">
            <span class="field-label">username *</span>
            <input
              v-model="username"
              class="field-input"
              type="text"
              autocomplete="username"
              placeholder="ferigeek"
              data-testid="register-username"
            />
          </label>

          <label class="field">
            <span class="field-label">password *</span>
            <input
              v-model="password"
              class="field-input"
              type="password"
              autocomplete="new-password"
              placeholder="at least 8 characters"
              data-testid="register-password"
            />
          </label>

          <label class="field">
            <span class="field-label">email *</span>
            <input
              v-model="email"
              class="field-input"
              type="email"
              autocomplete="email"
              placeholder="feri@example.com"
              data-testid="register-email"
            />
          </label>

          <label class="field">
            <span class="field-label">display name *</span>
            <input
              v-model="displayName"
              class="field-input"
              type="text"
              placeholder="Feri Geek"
              data-testid="register-displayName"
            />
          </label>

          <label class="field">
            <span class="field-label">gender *</span>
            <select
              v-model="gender"
              class="field-input"
              data-testid="register-gender"
            >
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="RATHER_NOT_TO_SAY">Rather not to say</option>
            </select>
          </label>

          <p v-if="error" class="auth-error" data-testid="register-error">{{ error }}</p>

          <button
            type="submit"
            class="btn btn-primary auth-submit"
            :disabled="loading"
            data-testid="register-submit"
          >
            {{ loading ? 'creating…' : 'continue' }}
          </button>
        </form>

        <p class="auth-switch">
          already have an account?
          <RouterLink to="/login" class="auth-link" data-testid="register-to-login">sign in</RouterLink>
        </p>
      </template>

      <!-- Step 2 -->
      <template v-else>
        <p class="auth-hint">optional profile — step 2 of 2</p>

        <form class="auth-form" @submit.prevent="onStep2Submit">
          <label class="field">
            <span class="field-label">bio</span>
            <textarea
              v-model="bio"
              class="field-input field-textarea"
              placeholder="tell something about yourself (max 255)"
              maxlength="255"
              data-testid="register-bio"
            />
          </label>

          <label class="field">
            <span class="field-label">location</span>
            <input
              v-model="location"
              class="field-input"
              type="text"
              placeholder="Tehran"
              maxlength="30"
              data-testid="register-location"
            />
          </label>

          <label class="field">
            <span class="field-label">profile picture</span>
            <input
              class="field-input"
              type="file"
              accept="image/*"
              data-testid="register-file"
              @change="onFileChange"
            />
            <span v-if="fileName" class="file-name" data-testid="register-file-name">{{ fileName }}</span>
          </label>

          <p v-if="error" class="auth-error" data-testid="register-error">{{ error }}</p>

          <div class="auth-actions">
            <button
              type="button"
              class="btn"
              :disabled="loading"
              data-testid="register-skip"
              @click="onSkip"
            >
              skip
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="loading"
              data-testid="register-complete"
            >
              {{ loading ? 'saving…' : 'complete' }}
            </button>
          </div>
        </form>
      </template>
    </section>
  </main>
</template>

<style scoped>
.register-view {
  height: 100%;
  display: grid;
  place-items: center;
  padding: var(--sarv-space-4);
}

.auth-box {
  width: 100%;
  max-width: 420px;
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

.file-name {
  font-size: 12px;
  color: var(--sarv-text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.auth-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sarv-space-3);
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
  .register-view {
    padding: var(--sarv-space-3);
    padding-bottom: calc(var(--sarv-space-3) + env(safe-area-inset-bottom));
  }

  .auth-box {
    padding: var(--sarv-space-5) var(--sarv-space-4);
  }

  .auth-submit,
  .auth-actions .btn {
    min-height: 48px;
    justify-content: center;
  }
}

@media (max-width: 380px) {
  .auth-actions {
    grid-template-columns: 1fr;
  }
}
</style>
