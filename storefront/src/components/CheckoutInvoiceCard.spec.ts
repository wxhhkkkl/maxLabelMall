import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { InvoiceTitleKind, InvoiceType, type InvoiceApplyForm, type InvoiceTitle } from '@/types/invoice'
import CheckoutInvoiceCard from './CheckoutInvoiceCard.vue'

// 抬头簿（US23）：卡片挂载时会去拉已保存的抬头
const listInvoiceTitles = vi.fn()
vi.mock('@/api/invoice', () => ({
  listInvoiceTitles: (...a: unknown[]) => listInvoiceTitles(...a),
  createInvoiceTitle: vi.fn(),
  updateInvoiceTitle: vi.fn(),
  deleteInvoiceTitle: vi.fn(),
  createOrderInvoice: vi.fn(),
}))

beforeEach(() => {
  listInvoiceTitles.mockReset()
  listInvoiceTitles.mockResolvedValue([])
})

/**
 * 结算页的发票卡片（T382 / FR-153 / FR-155 / FR-156）。
 *
 * 两条最要紧的：
 * · **默认不开票** —— 未勾选时不渲染任何开票字段，也不提示（不打扰客户）
 * · **专票才出现那四项** —— 与后端 `InvoiceValidator` 的必填矩阵同一个规则，
 *   普票时若把它们渲染出来，客户会以为"不填就提交不了"
 */
const emptyForm = (over: Partial<InvoiceApplyForm> = {}): InvoiceApplyForm => ({
  type: InvoiceType.NORMAL,
  titleKind: InvoiceTitleKind.COMPANY,
  titleName: '',
  taxNo: '',
  titleAddress: '',
  titlePhone: '',
  bankName: '',
  ...over,
})

/**
 * 挂载卡片，**并模拟真实父组件的 v-model 接线**。
 *
 * ⚠️ 两个都不能省：
 *  · `reactive`：卡片里有的地方是**原地改字段**（票种、输入框），传普通对象视图不会重渲染
 *  · `onUpdate:form` / `onUpdate:enabled`：卡片有的地方是**整体替换表单对象**
 *    （选已保存的抬头时）。defineModel 在没有监听者时只改自己的本地值 ——
 *    父组件（测试）看不到，测试会得到假的"什么都没发生"。
 *    CheckoutView 用的是 `v-model:form="invoiceForm"`，这里的桩必须等价。
 */
function mountCard(enabled: boolean, over: Partial<InvoiceApplyForm> = {}) {
  const model = reactive(emptyForm(over))
  let enabledState = enabled
  const w = mount(CheckoutInvoiceCard, {
    props: {
      enabled,
      form: model,
      'onUpdate:form': (next: InvoiceApplyForm) => Object.assign(model, next),
      'onUpdate:enabled': (next: boolean) => { enabledState = next },
    },
  })
  return { w, model }
}

describe('结算页发票卡片', () => {
  it('默认不开票：不渲染票种与抬头字段，也不出校验提示', () => {
    const { w } = mountCard(false)
    expect(w.find('.invoice-fields').exists()).toBe(false)
    expect(w.find('#invoiceTitleName').exists()).toBe(false)
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('勾选后才出现票种、抬头类型与字段', () => {
    const { w } = mountCard(true)
    expect(w.find('.invoice-fields').exists()).toBe(true)
    expect(w.find('#invoiceTitleName').exists()).toBe(true)
    expect(w.findAll('.invoice-type')).toHaveLength(3) // 普票 / 专票 / 收据（Q61）
    expect(w.findAll('.invoice-kind')).toHaveLength(2)
  })

  it('普票：只要求抬头名称，不渲染地址 / 电话 / 开户行', () => {
    const { w } = mountCard(true)
    expect(w.find('#invoiceTitleName').exists()).toBe(true)
    expect(w.find('#invoiceTitleAddress').exists()).toBe(false)
    expect(w.find('#invoiceTitlePhone').exists()).toBe(false)
    expect(w.find('#invoiceBankName').exists()).toBe(false)
  })

  it('专票：多出地址 / 电话 / 开户行三项', async () => {
    const { w, model } = mountCard(true)
    await w.findAll('.invoice-type')[1]!.trigger('click')

    expect(model.type).toBe(InvoiceType.SPECIAL)
    expect(w.find('#invoiceTitleAddress').exists()).toBe(true)
    expect(w.find('#invoiceTitlePhone').exists()).toBe(true)
    expect(w.find('#invoiceBankName').exists()).toBe(true)
  })

  it('必填不全时给出**点名缺哪一项**的提示（FR-156）', async () => {
    const { w, model } = mountCard(true)
    // 名称为空时，两种票都先点名「抬头名称」（它是共同的必填项）
    expect(w.get('[role="alert"]').text()).toContain('抬头名称')

    // 填上名称 → 下一个缺的是纳税人识别号（**两种票都必填**，2026-10-15 口径）
    model.titleName = '南京路桥机械有限公司'
    await w.vm.$nextTick()
    await w.findAll('.invoice-type')[1]!.trigger('click')
    expect(w.get('[role="alert"]').text()).toContain('纳税人识别号')

    // **只要再填税号就够** —— 专票的地址/电话/开户行是选填（不必填也能提交）
    model.taxNo = '91320104660690975E'
    await w.vm.$nextTick()
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('**选「个人」后不再要求税号** —— 只填姓名就通过（Q38）', async () => {
    const { w, model } = mountCard(true)
    model.titleName = '张三'
    await w.vm.$nextTick()
    // 企业档下：还差纳税人识别号
    expect(w.get('[role="alert"]').text()).toContain('纳税人识别号')

    await w.findAll('.invoice-kind')[1]!.trigger('click')

    expect(model.titleKind).toBe(InvoiceTitleKind.PERSONAL)
    // 个人档下：只填了姓名，提示就该消失 —— 这正是 Q38 要解决的"个人客户存不下抬头"
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('「个人 + 专票」根本选不出来：选了专票，「个人」就置灰', async () => {
    const { w } = mountCard(true)
    await w.findAll('.invoice-type')[1]!.trigger('click') // 选专票

    expect(w.findAll('.invoice-kind')[1]!.attributes('disabled')).toBeDefined()
    // 普票那一个照常可选（置灰的只有冲突的那一个，别把整组都禁掉）
    expect(w.findAll('.invoice-kind')[0]!.attributes('disabled')).toBeUndefined()
  })

  it('反过来也一样：选了「个人」，「专票」就置灰', async () => {
    const { w } = mountCard(true)
    await w.findAll('.invoice-kind')[1]!.trigger('click') // 选个人

    expect(w.findAll('.invoice-type')[1]!.attributes('disabled')).toBeDefined()
    expect(w.findAll('.invoice-type')[0]!.attributes('disabled')).toBeUndefined()
  })

  it('勾选框改动向父组件抛出 update:enabled（值由父组件持有）', async () => {
    const { w } = mountCard(false)
    await w.get('input[type="checkbox"]').setValue(true)
    expect(w.emitted('update:enabled')?.[0]).toEqual([true])
  })
})

describe('结算页发票卡片 · 与抬头选择器的接线（US23 / T420）', () => {
  it('选择器挑一条 → 字段被填上，**票种保留客户当前的选择**、**抬头类型跟着那条抬头走**', async () => {
    // 选择器自己的行为（按票种筛 / 新增 / 默认预选）由 InvoiceTitlePicker.spec.ts 覆盖，
    // 这里只钉**接线**：卡片把选中的抬头填进表单时，不能把客户选的票种改掉
    const { w, model } = mountCard(true)
    await flushPromises()
    model.type = InvoiceType.SPECIAL

    w.findComponent({ name: 'InvoiceTitlePicker' }).vm.$emit('picked', {
      id: 3,
      type: InvoiceType.NORMAL,
      titleKind: InvoiceTitleKind.COMPANY,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      defaultStatus: true,
    })
    await w.vm.$nextTick()

    expect(model.titleName).toBe('南京路桥机械有限公司')
    expect(model.taxNo).toBe('91320104660690975E')
    expect(model.type).toBe(InvoiceType.SPECIAL) // ← 没有被抬头自带的票种改掉
  })

  it('挑中一条**个人**抬头 → 表单的抬头类型跟着变成个人（否则下一步会去要他填税号）', async () => {
    const { w, model } = mountCard(true)
    await flushPromises()

    w.findComponent({ name: 'InvoiceTitlePicker' }).vm.$emit('picked', {
      id: 5,
      type: InvoiceType.NORMAL,
      titleKind: InvoiceTitleKind.PERSONAL,
      titleName: '张三',
      taxNo: null,
      defaultStatus: false,
    })
    await w.vm.$nextTick()

    expect(model.titleKind).toBe(InvoiceTitleKind.PERSONAL)
    expect(w.find('[role="alert"]').exists()).toBe(false) // 个人抬头没有税号，也不该提示
  })

  it('把**当前票种**交给选择器（选专票时只列专票抬头）', async () => {
    const { w, model } = mountCard(true)
    await flushPromises()
    model.type = InvoiceType.SPECIAL
    await w.vm.$nextTick()

    expect(w.findComponent({ name: 'InvoiceTitlePicker' }).props('type')).toBe(InvoiceType.SPECIAL)
  })
})

describe('结算页发票卡片 —— 收据与邮箱（Q61 / Q62）', () => {
  it('收据：企业也只要名称，不要求税号', async () => {
    const { w, model } = mountCard(true)
    await w.findAll('.invoice-type')[2]!.trigger('click')
    expect(model.type).toBe(InvoiceType.RECEIPT)

    model.titleName = '北京某某科技有限公司'
    await w.vm.$nextTick()
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })

  it('邮箱选填；填了非法邮箱要点名', async () => {
    const { w, model } = mountCard(true)
    model.titleName = '南京路桥机械有限公司'
    model.taxNo = '91320104660690975E'
    model.email = 'not-an-email'
    await w.vm.$nextTick()
    expect(w.get('[role="alert"]').text()).toContain('邮箱格式不正确')

    model.email = 'finance@example.com'
    await w.vm.$nextTick()
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })
})
