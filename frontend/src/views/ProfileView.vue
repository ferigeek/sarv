<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type { ApiError } from '@/api/client'
import { follow, getFollowing, unfollow } from '@/api/follows'
import { getMediaBlob, uploadMedia } from '@/api/media'
import { getUser, updateMe } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { Gender, UserResponse } from '@/types/api'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const auth = useAuthStore()

const profile = ref<UserResponse | null>(null)
const loading = ref(true)
const error = ref('')
const isSelf = computed(() => Boolean(auth.user && profile.value && auth.user.id === profile.value.id))

/* Follow state derived from the current user's following list ( no isFollowing field on the API).
   FollowingView fetches a fresh page when the profile opens so we can know whether we follow them. */
const followingIds = ref<number[]>([])
const followLoading = ref(false)

const avatarUrl = ref<string | null>(null)
let avatarObjectUrl: string | null = null
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string | null>(null)
let avatarPreviewObjectUrl: string | null = null

/* Edit form ( self only) */
const editMode = ref(false)
const formDisplayName = ref('')
const formBio = ref('')
const formLocation = ref('')
const formGender = ref<Gender>('RATHER_NOT_TO_SAY')
const saving = ref(false)
const saveError = ref('')
const saveOk = ref(false)

const profileId = computed(() => {
  const raw = route.params.id
  if (raw === undefined || raw === '' || (Array.isArray(raw) && raw.length === 0)) return auth.user?.id ?? null
  return Number(raw)
})

const isFollowing = computed(() => Boolean(profile.value && followingIds.value.includes(profile.value.id)))

function clearAvatar() {
  if (avatarObjectUrl) {
    URL.revokeObjectURL(avatarObjectUrl)
    avatarObjectUrl = null
  }
  avatarUrl.value = null
}

function clearAvatarPreview() {
  if (avatarPreviewObjectUrl) {
    URL.revokeObjectURL(avatarPreviewObjectUrl)
    avatarPreviewObjectUrl = null
  }
  avatarPreview.value = null
}

async function loadFollowing() {
  if (!profile.value || isSelf.value) {
    followingIds.value = []
    return
  }
  try {
    followingIds.value = []
    // Fetch a fresh first page; we only track whether the current user follows this profile.
    const page = await getFollowing(auth.user!.id, { page: 0, size: 50 })
    followingIds.value = page.content.map((u) => u.id)
  } catch {
    followingIds.value = []
  }
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  editMode.value = false
  saveOk.value = false
  saveError.value = ''
  try {
    const id = profileId.value
    if (id === null) throw new Error('missing id')
    profile.value = await getUser(id)
    clearAvatar()
    if (profile.value.profilePictureId) {
      try {
        const blob = await getMediaBlob(profile.value.profilePictureId)
        avatarObjectUrl = URL.createObjectURL(blob)
        avatarUrl.value = avatarObjectUrl
      } catch {
        avatarUrl.value = null
      }
    }
    if (isSelf.value) {
      formDisplayName.value = profile.value.displayName
      formBio.value = profile.value.bio ?? ''
      formLocation.value = profile.value.location ?? ''
      formGender.value = profile.value.gender
    }
    await loadFollowing()
  } catch (e) {
    const err = e as ApiError
    error.value = err.detail ?? err.title ?? 'Failed to load profile.'
    profile.value = null
  } finally {
    loading.value = false
  }
}

function onAvatarPick(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  avatarFile.value = f
  clearAvatarPreview()
  avatarPreviewObjectUrl = URL.createObjectURL(f)
  avatarPreview.value = avatarPreviewObjectUrl
  saveOk.value = false
}

async function onToggleFollow() {
  if (!profile.value || isSelf.value || followLoading.value) return
  followLoading.value = true
  try {
    if (isFollowing.value) {
      await unfollow(profile.value.id)
    } else {
      await follow(profile.value.id)
    }
    await loadFollowing()
  } finally {
    followLoading.value = false
  }
}

async function onSave() {
  if (!profile.value || saving.value) return
  saving.value = true
  saveError.value = ''
  saveOk.value = false
  try {
    let profilePictureId: number | null = null
    if (avatarFile.value) {
      const media = await uploadMedia(avatarFile.value)
      profilePictureId = media.id
    } else {
      profilePictureId = profile.value.profilePictureId
    }
    const updated = await updateMe({
      displayName: formDisplayName.value.trim(),
      gender: formGender.value,
      bio: formBio.value.trim() || null,
      location: formLocation.value.trim() || null,
      profilePictureId,
    })
    profile.value = updated
    await auth.fetchMe()
    saveOk.value = true
    editMode.value = false
    avatarFile.value = null
    clearAvatarPreview()
    // Reload avatar so the freshly uploaded one shows
    clearAvatar()
    if (updated.profilePictureId) {
      const blob = await getMediaBlob(updated.profilePictureId)
      avatarObjectUrl = URL.createObjectURL(blob)
      avatarUrl.value = avatarObjectUrl
    }
  } catch (e) {
    const err = e as ApiError
    saveError.value = err.detail ?? err.title ?? 'Could not save profile.'
  } finally {
    saving.value = false
  }
}

watch(() => route.params.id, loadProfile, { immediate: true })

onMounted(() => {
  void loadProfile()
})

onBeforeUnmount(() => {
  clearAvatar()
  clearAvatarPreview()
})
</script>

<template>
  <section class="profile-view" data-testid="profile-view">
    <div v-if="loading" class="profile-state" data-testid="profile-loading">loading profile…</div>

    <div v-else-if="error" class="profile-state profile-state--error" data-testid="profile-error">
      {{ error }}
      <button class="btn" type="button" data-testid="profile-retry" @click="loadProfile">retry</button>
    </div>

    <template v-else-if="profile">
      <header class="profile-header panel">
        <div class="profile-header__main">
          <div class="profile-avatar" data-testid="profile-avatar" aria-hidden="true">
            <img
              v-if="avatarPreview || avatarUrl"
              :src="(avatarPreview || avatarUrl) ?? undefined"
              alt=""
              class="profile-avatar__img"
              data-testid="profile-avatar-img"
            />
            <AppIcon v-else name="user" :size="32" />
          </div>

          <div class="profile-header__info">
            <span class="profile-name" data-testid="profile-name">{{ profile.displayName }}</span>
            <span class="profile-username" data-testid="profile-username">@{{ profile.username }}</span>
            <p v-if="profile.bio" class="profile-bio" data-testid="profile-bio">{{ profile.bio }}</p>
            <p v-if="profile.location" class="profile-location" data-testid="profile-location">
              ◈ {{ profile.location }}
            </p>
            <span v-if="profile.gender" class="profile-gender" data-testid="profile-gender">{{ profile.gender }}</span>
          </div>
        </div>

        <div class="profile-header__actions">
          <template v-if="isSelf">
            <button
              class="btn"
              type="button"
              data-testid="profile-edit-toggle"
              @click="editMode = !editMode"
            >
              {{ editMode ? 'close editor' : 'edit profile' }}
            </button>
          </template>
          <template v-else>
            <button
              class="btn"
              :class="{ 'btn-primary': !isFollowing }"
              type="button"
              data-testid="profile-follow-btn"
              :disabled="followLoading"
              @click="onToggleFollow"
            >
              {{ followLoading ? '…' : isFollowing ? 'unfollow' : 'follow' }}
            </button>
          </template>
        </div>
      </header>

      <!-- Self edit form -->
      <section v-if="isSelf && editMode" class="panel profile-edit" data-testid="profile-edit-form">
        <label class="field">
          <span class="field-label">display name</span>
          <input
            v-model="formDisplayName"
            class="field-input"
            type="text"
            data-testid="profile-edit-displayName"
          />
        </label>

        <label class="field">
          <span class="field-label">bio</span>
          <textarea
            v-model="formBio"
            class="field-input field-textarea"
            maxlength="255"
            data-testid="profile-edit-bio"
          ></textarea>
        </label>

        <label class="field">
          <span class="field-label">location</span>
          <input
            v-model="formLocation"
            class="field-input"
            type="text"
            maxlength="30"
            data-testid="profile-edit-location"
          />
        </label>

        <label class="field">
          <span class="field-label">gender</span>
          <select v-model="formGender" class="field-input" data-testid="profile-edit-gender">
            <option value="MALE">Male</option>
            <option value="FEMALE">Female</option>
            <option value="RATHER_NOT_TO_SAY">Rather not to say</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">profile picture</span>
          <input
            class="field-input"
            type="file"
            accept="image/*"
            data-testid="profile-edit-file"
            @change="onAvatarPick"
          />
          <span v-if="avatarFile" class="file-name" data-testid="profile-edit-file-name">{{ avatarFile.name }}</span>
        </label>

        <p v-if="saveError" class="profile-save-error" data-testid="profile-edit-error">{{ saveError }}</p>
        <p v-if="saveOk" class="profile-save-ok" data-testid="profile-edit-ok">profile updated ✓</p>

        <div class="profile-edit__actions">
          <button class="btn" type="button" :disabled="saving" data-testid="profile-edit-cancel" @click="editMode = false">
            cancel
          </button>
          <button
            class="btn btn-primary"
            type="button"
            :disabled="saving"
            data-testid="profile-edit-save"
            @click="onSave"
          >
            {{ saving ? 'saving…' : 'save changes' }}
          </button>
        </div>
      </section>

      <!-- Posts by user — backend endpoint not available yet; rendered but inert -->
      <section class="panel profile-posts" data-testid="profile-posts">
        <header class="profile-posts__header">
          <span class="profile-posts__title">POSTS</span>
          <span class="profile-posts__meta">BY {{ profile.username }}</span>
        </header>
        <div class="profile-posts__body" data-testid="profile-posts-inert">
          posts by this user — coming soon
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.profile-view {
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  min-height: 100%;
  align-content: start;
}

.profile-state {
  padding: var(--sarv-space-6);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-panel);
}

.profile-state--error {
  color: #ff8fa3;
  display: grid;
  gap: var(--sarv-space-3);
  justify-items: center;
}

.profile-header {
  display: grid;
  gap: var(--sarv-space-4);
  padding: var(--sarv-space-5);
}

.profile-header__main {
  display: flex;
  gap: var(--sarv-space-4);
  align-items: center;
}

.profile-avatar {
  display: grid;
  place-items: center;
  width: 72px;
  height:  72px;
  flex-shrink: 0;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border);
  color: var(--sarv-text-dim);
  overflow: hidden;
}

.profile-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.profile-header__info {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--sarv-text);
}

.profile-username {
  font-size: 13px;
  color: var(--sarv-text-dim);
}

.profile-bio {
  font-size: 13px;
  color: var(--sarv-text);
  margin-top: var(--sarv-space-2);
  white-space: pre-wrap;
}

.profile-location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--sarv-text-dim);
}

.profile-gender {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--sarv-text-faint);
  text-transform: uppercase;
}

.profile-header__actions {
  display: flex;
  justify-content: flex-end;
}

.profile-edit {
  display: grid;
  gap: var(--sarv-space-4);
  padding: var(--sarv-space-5);
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

.profile-save-error {
  padding: 8px 10px;
  background: color-mix(in srgb, var(--sarv-red) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--sarv-red) 40%, transparent);
  color: #ff8fa3;
  font-size: 12px;
}

.profile-save-ok {
  padding: 8px 10px;
  background: color-mix(in srgb, var(--sarv-green) 10%, transparent);
  border:  1px solid color-mix(in srgb, var(--sarv-green) 30%, transparent);
  color: var(--sarv-green);
  font-size: 12px;
}

.profile-edit__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sarv-space-3);
}

.profile-posts {
  padding: var(--sarv-space-4);
}

.profile-posts__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--sarv-space-3);
  border-bottom: 1px solid var(--sarv-border);
}

.profile-posts__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.profile-posts__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.profile-posts__body {
  padding: var(--sarv-space-4);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-bg);
}

@media (max-width: 640px) {
  .profile-header {
    padding: var(--sarv-space-4);
  }

  .profile-header__main {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-avatar {
    width: 56px;
    height: 56px;
  }

  .profile-name {
    font-size: 16px;
  }

  .profile-header__actions {
    justify-content: stretch;
  }

  .profile-header__actions .btn {
    flex: 1;
    min-height: 44px;
    justify-content: center;
  }

  .profile-edit {
    padding: var(--sarv-space-4);
  }

  .profile-edit__actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .profile-edit__actions .btn {
    min-height: 44px;
    justify-content: center;
  }
}
</style>
