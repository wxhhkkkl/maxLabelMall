import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import OtherForm from './OtherForm.vue'

// 表单挂载即拉取会员等级选项
vi.mock('@/api/member/level', () => ({
  // 只返回启用等级：3 用来模拟「已选中但已停用」
  getSimpleLevelList: vi.fn().mockResolvedValue([{ id: 5, name: '金卡' }])
}))

// OtherForm 依赖 unplugin-auto-import 注入的 `required`，vitest 下手工补上
;(globalThis as any).required = { required: true, message: '必填', trigger: 'blur' }

describe('商品编辑 - 企业专享', () => {
  it('展示企业专享开关与说明', () => {
    const wrapper = mount(OtherForm, {
      props: { propFormData: { enterpriseOnly: true } }
    })
    expect(wrapper.text()).toContain('企业专享')
    expect(wrapper.text()).toContain('仅企业中心可见')
  })
})

describe('商品编辑 - 浏览等级', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('默认公开，并展示三档文案', async () => {
    const wrapper = mount(OtherForm, { props: { propFormData: {} } })
    await flushPromises()
    expect(wrapper.text()).toContain('浏览等级')
    expect(wrapper.text()).toContain('公开')
    expect(wrapper.text()).toContain('全部登录会员')
    expect(wrapper.text()).toContain('指定等级会员')
  })

  it('指定等级会员时展示等级多选', async () => {
    const wrapper = mount(OtherForm, {
      props: { propFormData: { browseLevelType: 2, browseLevelIds: [3] } }
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('指定等级')
  })

  it('已选中但已停用的等级仍以占位标签回显', async () => {
    const wrapper = mount(OtherForm, {
      props: { propFormData: { browseLevelType: 2, browseLevelIds: [3, 5] } }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('金卡')
    expect(wrapper.text()).toContain('等级 #3（已停用）')
  })

  it('指定等级但未勾选时校验失败', async () => {
    const wrapper = mount(OtherForm, {
      props: { propFormData: { browseLevelType: 2, browseLevelIds: [] } }
    })
    await flushPromises()
    await expect((wrapper.vm as any).validate()).rejects.toBeTruthy()
  })

  it('回填后校验通过，等级与模式保持不变', async () => {
    const data: any = { browseLevelType: 2, browseLevelIds: [3, 5] }
    const wrapper = mount(OtherForm, { props: { propFormData: data } })
    await flushPromises()
    await (wrapper.vm as any).validate()
    expect(data.browseLevelType).toBe(2)
    expect(data.browseLevelIds).toEqual([3, 5])
  })
})
