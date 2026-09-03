import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import SarvMark from '../SarvMark.vue'

describe('SarvMark', () => {
  it('renders the Sarv brand mark image', () => {
    const wrapper = mount(SarvMark)
    const img = wrapper.find('[data-testid="sarv-mark"]')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toContain('icon_sarv_128')
    expect(img.attributes('alt')).toBe('Sarv logo')
  })

  it('applies the requested size to width and height', () => {
    const wrapper = mount(SarvMark, { props: { size: 64 } })
    const img = wrapper.find('[data-testid="sarv-mark"]')
    expect(img.attributes('width')).toBe('64')
    expect(img.attributes('height')).toBe('64')
  })
})
