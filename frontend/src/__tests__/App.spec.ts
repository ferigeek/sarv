import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

import App from '../App.vue'
import router from '../router'
import { registerPixelicons } from '../assets/icons/pixelarticons'

registerPixelicons()

function mountApp() {
  return mount(App, {
    global: {
      plugins: [createPinia(), router],
    },
  })
}

describe('App', () => {
  it('boots under router and pinia and renders the shell', () => {
    const wrapper = mountApp()
    expect(wrapper.find('[data-testid="app-shell"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('SARV')
  })

  it('renders a pixelarticons icon as an svg', () => {
    const wrapper = mountApp()
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
  })
})