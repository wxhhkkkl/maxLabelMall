import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import OtherForm from './OtherForm.vue'

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
