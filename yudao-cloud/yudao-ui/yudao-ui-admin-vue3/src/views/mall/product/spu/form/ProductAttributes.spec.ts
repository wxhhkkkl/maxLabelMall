import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ProductAttributes from './ProductAttributes.vue'

/**
 * 商品属性值添加（2026-11-18 重做）
 *
 * 旧实现用 `el-select allow-create`：Chrome 回车会多造一条带空格的空值，Edge 偶尔加不上。
 * 新交互是**输入框 + 「添加」按钮**，这里钉住：
 * · 回车 / 点按钮都能加，且只提交一次
 * · 空值、纯空格不提交
 * · 输入法上屏（isComposing）时回车不提交
 * · 命中已有属性值时直接复用，不再调创建接口
 */

const createPropertyValue = vi.fn()
const getPropertyValueSimpleList = vi.fn()
vi.mock('@/api/mall/product/property', () => ({
  createPropertyValue: (...a: unknown[]) => createPropertyValue(...a),
  getPropertyValueSimpleList: (...a: unknown[]) => getPropertyValueSimpleList(...a)
}))

const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))

function propertyList() {
  return [{ id: 7, name: '尺寸', values: [] as any[] }]
}

async function mountAttributes(existing: any[] = []) {
  createPropertyValue.mockReset()
  createPropertyValue.mockResolvedValue(99)
  getPropertyValueSimpleList.mockReset()
  getPropertyValueSimpleList.mockResolvedValue(existing)
  message.warning.mockReset()

  const w = mount(ProductAttributes, { props: { propertyList: propertyList() } })
  await flushPromises()
  return w
}

const addButton = (w: any) =>
  w.findAll('button').find((b: any) => b.text().includes('+ 添加'))
const confirmButton = (w: any) =>
  w.findAll('button').find((b: any) => b.text() === '添加')

async function openInput(w: any) {
  await addButton(w)!.trigger('click')
  await flushPromises()
}

async function typeValue(w: any, value: string) {
  const input = w.find('input')
  await input.setValue(value)
  return input
}

describe('商品属性值添加', () => {
  it('点「+ 添加」出现输入框与「添加」按钮（不再用可创建的下拉框）', async () => {
    const w = await mountAttributes()

    expect(w.find('input').exists()).toBe(false)
    await openInput(w)

    expect(w.find('input').exists()).toBe(true)
    expect(confirmButton(w), '应有明确的「添加」按钮').toBeTruthy()
  })

  it('输入值后点「添加」→ 创建属性值并显示为标签', async () => {
    const w = await mountAttributes()
    await openInput(w)
    await typeValue(w, 'A4')

    await confirmButton(w)!.trigger('click')
    await flushPromises()

    expect(createPropertyValue).toHaveBeenCalledTimes(1)
    expect(createPropertyValue).toHaveBeenCalledWith({ propertyId: 7, name: 'A4' })
    expect(w.text()).toContain('A4')
    expect(w.emitted('success')).toBeTruthy()
  })

  it('回车提交 → 只创建一次（不会同时被 change/blur 重复触发）', async () => {
    const w = await mountAttributes()
    await openInput(w)
    const input = await typeValue(w, 'A4')

    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(createPropertyValue).toHaveBeenCalledTimes(1)
    expect(w.text()).toContain('A4')
  })

  it('输入的是空格 / 空串 → 回车不提交，也不会出现空标签', async () => {
    const w = await mountAttributes()
    await openInput(w)
    const input = await typeValue(w, '   ')

    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(createPropertyValue).not.toHaveBeenCalled()
    expect(w.findAll('.el-tag')).toHaveLength(1) // 只有属性名那个标签
  })

  it('值首尾有空格 → 提交时 trim 掉', async () => {
    const w = await mountAttributes()
    await openInput(w)
    await typeValue(w, '  A4  ')

    await confirmButton(w)!.trigger('click')
    await flushPromises()

    expect(createPropertyValue).toHaveBeenCalledWith({ propertyId: 7, name: 'A4' })
  })

  it('输入法上屏时按回车（isComposing）→ 不提交', async () => {
    const w = await mountAttributes()
    await openInput(w)
    const input = await typeValue(w, '尺寸值')

    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    Object.defineProperty(event, 'isComposing', { value: true })
    input.element.dispatchEvent(event)
    await flushPromises()

    expect(createPropertyValue).not.toHaveBeenCalled()
  })

  it('命中已有属性值 → 直接复用，不调创建接口', async () => {
    const w = await mountAttributes([{ id: 31, name: 'A4' }])
    await openInput(w)
    await typeValue(w, 'A4')

    await confirmButton(w)!.trigger('click')
    await flushPromises()

    expect(createPropertyValue).not.toHaveBeenCalled()
    expect(w.text()).toContain('A4')
  })

  it('重复添加同名值 → 提示且不重复创建', async () => {
    const w = await mountAttributes()
    await openInput(w)
    await typeValue(w, 'A4')
    await confirmButton(w)!.trigger('click')
    await flushPromises()
    await typeValue(w, 'A4')
    await confirmButton(w)!.trigger('click')
    await flushPromises()

    expect(createPropertyValue).toHaveBeenCalledTimes(1)
    expect(message.warning).toHaveBeenCalledWith('已存在相同属性值，请重试')
  })
})
