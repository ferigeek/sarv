import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import App from '../App.vue'
import { createAppRouter } from '../router'

function mountApp() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
    },
  })
  return { wrapper, router }
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('directs unauthenticated users to the login view', async () => {
    const { wrapper, router } = mountApp()
    await router.isReady()
    await flushPromises()

    expect(wrapper.find('[data-testid="login-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-shell"]').exists()).toBe(false)
  })
})