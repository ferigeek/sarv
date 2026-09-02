import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import SarvLogo from '../components/SarvLogo.vue'

describe('SarvLogo', () => {
  it('renders SARV constructed from four chars with a terminal cursor', () => {
    const wrapper = mount(SarvLogo)

    expect(wrapper.find('[data-testid="sarv-logo"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sarv-char-0"]').text()).toBe('S')
    expect(wrapper.find('[data-testid="sarv-char-1"]').text()).toBe('A')
    expect(wrapper.find('[data-testid="sarv-char-2"]').text()).toBe('R')
    expect(wrapper.find('[data-testid="sarv-char-3"]').text()).toBe('V')
    expect(wrapper.find('.sarv-logo__cursor').exists()).toBe(true)
  })

  it('is accessible via aria-label', () => {
    const wrapper = mount(SarvLogo)
    expect(wrapper.find('[aria-label="Sarv"]').exists()).toBe(true)
  })
})
