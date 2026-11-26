import { mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { describe, expect, it } from 'vitest'

import type { InvoiceApplyFields } from '@/api/mall/trade/invoice'
import InvoiceFieldsForm from './InvoiceFieldsForm.vue'

/**
 * 发票字段组件（023，从 `InvoiceEditForm` 抽取）
 *
 * 钉住与后端 `InvoiceValidator` 一致的必填矩阵：
 * · 企业 + 普票/专票 → 名称 + 税号；个人 + 普票 → 只要名称；收据 → 只要名称
 * · 个人不能开专票（自动回落到普票）
 * · 企业授权订单禁用「个人」
 */

function mountFields(over: InvoiceApplyFields = {}, disablePersonal = false) {
  const form = reactive<InvoiceApplyFields>({
    type: 1,
    titleKind: 1,
    titleName: '',
    taxNo: '',
    ...over
  })
  const wrapper = mount(InvoiceFieldsForm, { props: { form, disablePersonal } })
  return { wrapper, form }
}

describe('InvoiceFieldsForm', () => {
  it('企业普票：名称 + 税号齐全才通过', async () => {
    const { wrapper, form } = mountFields()
    form.titleName = '南京路桥机械有限公司'
    form.taxNo = '91320104660690975E'
    await nextTick()

    await expect((wrapper.vm as any).validate()).resolves.toBeUndefined()
  })

  it('企业普票缺税号：校验失败并点名税号', async () => {
    const { wrapper } = mountFields({ titleName: '南京路桥机械有限公司' })
    await nextTick()

    await expect((wrapper.vm as any).validate()).rejects.toMatchObject({
      taxNo: expect.any(Object)
    })
  })

  it('个人抬头：税号选填；已选专票时自动回落到普票', async () => {
    const { wrapper, form } = mountFields({ type: 2, titleKind: 2, titleName: '张三' })
    await nextTick()

    expect(form.type).toBe(1) // 个人不能开专票
    await expect((wrapper.vm as any).validate()).resolves.toBeUndefined()
  })

  it('收据：只要名称，不要求税号', async () => {
    const { wrapper } = mountFields({ type: 3, titleName: '某某公司' })
    await nextTick()

    await expect((wrapper.vm as any).validate()).resolves.toBeUndefined()
  })

  it('企业授权订单：禁用「个人」', async () => {
    const { wrapper } = mountFields({}, true)
    await nextTick()

    const personal = wrapper.findAll('.el-radio').find((radio) => radio.text().includes('个人'))
    expect(personal, '找不到个人抬头选项').toBeTruthy()
    expect(personal!.classes()).toContain('is-disabled')
  })

  it('邮箱格式错误：校验失败', async () => {
    const { wrapper } = mountFields({
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      email: 'not-an-email'
    })
    await nextTick()

    await expect((wrapper.vm as any).validate()).rejects.toMatchObject({
      email: expect.any(Object)
    })
  })
})
