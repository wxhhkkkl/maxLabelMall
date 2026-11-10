import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildExportQuickRange } from './orderExport'

/**
 * 订单导出弹窗（2026-11-10）。
 *
 * · 快捷项选完直接算出时间范围
 * · 没选范围就点导出 → 提示，不发请求
 * · 有范围 → 只把 createTime 交给后端（口径：仅按时间范围导出）
 */

const exportOrderExcel = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  exportOrderExcel: (...args: unknown[]) => exportOrderExcel(...args)
}))

const excelDownload = vi.fn()
vi.mock('@/utils/download', () => ({
  default: { excel: (...args: unknown[]) => excelDownload(...args) }
}))

const warning = vi.fn()
const success = vi.fn()
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ warning, success })
}))

const OrderExportForm = (await import('./OrderExportForm.vue')).default

/** 打开弹窗（弹窗内容在打开后才渲染） */
async function openForm() {
  const wrapper = mount(OrderExportForm, { global: { plugins: [createPinia()] } })
  ;(wrapper.vm as any).open()
  await flushPromises()
  return wrapper
}

/** 按按钮文案找按钮（忽略空白） */
function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const normalized = text.replace(/\s/g, '')
  return wrapper.findAll('button').find((button) => button.text().replace(/\s/g, '') === normalized)
}

beforeEach(() => {
  exportOrderExcel.mockReset()
  excelDownload.mockReset()
  warning.mockReset()
  success.mockReset()
})

describe('订单导出弹窗', () => {
  it('选「近 1 周」再导出：按快捷范围请求接口并下载', async () => {
    exportOrderExcel.mockResolvedValue(new Blob())
    const wrapper = await openForm()

    const quick = buttonByText(wrapper, '近1周')
    expect(quick, '找不到「近 1 周」').toBeTruthy()
    await quick!.trigger('click')
    await flushPromises()

    await buttonByText(wrapper, '导出')!.trigger('click')
    await flushPromises()

    expect(exportOrderExcel).toHaveBeenCalledWith({
      createTime: buildExportQuickRange('week')
    })
    expect(excelDownload).toHaveBeenCalled()
  })

  it('没选时间范围就导出：提示且不发请求', async () => {
    const wrapper = await openForm()

    await buttonByText(wrapper, '导出')!.trigger('click')
    await flushPromises()

    expect(warning).toHaveBeenCalled()
    expect(exportOrderExcel).not.toHaveBeenCalled()
  })
})
