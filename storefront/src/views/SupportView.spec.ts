import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: vi.fn() }),
  }
})

import SupportView from './SupportView.vue'

const stubs = {
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}

describe('服务支持 · 快捷入口', () => {
  it('「驱动下载」直接跳到驱动安装页，不再是页内锚点', () => {
    const w = mount(SupportView, { global: { stubs } })
    const quick = w.get('.support-quick')

    expect(quick.text()).toContain('驱动下载')
    expect(quick.text()).not.toContain('设备与驱动')
    expect(quick.find('a[href="/support/driver-install"]').exists()).toBe(true)
  })
})
