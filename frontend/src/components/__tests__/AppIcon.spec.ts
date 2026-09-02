import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import AppIcon from '../AppIcon.vue'
import { registerPixelicons } from '../../assets/icons/pixelarticons'

registerPixelicons()

describe('AppIcon', () => {
  it('renders a pixelarticons icon as an svg', () => {
    const wrapper = mount(AppIcon, { props: { name: 'smile', size: 32 } })
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
  })

  it('forwards extra attributes to the svg', () => {
    const wrapper = mount(AppIcon, {
      props: { name: 'smile' },
      attrs: { class: 'icon-accent', 'data-foo': 'bar' },
    })
    const svg = wrapper.find('svg')
    expect(svg.classes()).toContain('icon-accent')
    expect(svg.attributes('data-foo')).toBe('bar')
  })
})