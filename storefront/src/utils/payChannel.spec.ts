import { describe, expect, it } from 'vitest'

import { buildChannelOptions } from './payChannel'

/**
 * 支付渠道选项的构造。
 *
 * ⚠️ 设计要点：**可用性由后端定**（`/app-api/pay/channel/get-enable-code-list`），
 * 前端只负责「编码 → 中文名 + 展示顺序」这段纯展示映射。
 * 所以这里测的是「给定后端返回的启用集合，前端渲染出什么」。
 */
describe('buildChannelOptions —— 渠道编码映射为展示项', () => {
  it('已知渠道即使后端没启用也占位（微信未开通时界面要留入口）', () => {
    const opts = buildChannelOptions(['alipay_pc'])
    expect(opts.map((o) => o.code)).toEqual(['alipay_pc', 'wx_native'])
    expect(opts[0]).toMatchObject({ label: '支付宝', enabled: true, comingSoon: false })
    expect(opts[1]).toMatchObject({ label: '微信支付', enabled: false, comingSoon: true })
  })

  it('两个都启用时都可选', () => {
    const opts = buildChannelOptions(['alipay_pc', 'wx_native'])
    expect(opts.every((o) => o.enabled)).toBe(true)
    expect(opts.every((o) => o.comingSoon === false)).toBe(true)
  })

  it('后端返回了未知编码时也要显示 —— 免得新配的渠道在界面上凭空消失', () => {
    const opts = buildChannelOptions(['alipay_pc', 'wx_lite'])
    expect(opts.map((o) => o.code)).toContain('wx_lite')
    // 未知编码没有中文名，回落显示编码本身
    expect(opts.find((o) => o.code === 'wx_lite')).toMatchObject({
      label: 'wx_lite',
      enabled: true,
      comingSoon: false,
    })
  })

  it('顺序固定：支付宝在前、微信在后，未知的后置', () => {
    const opts = buildChannelOptions(['wx_lite', 'alipay_pc'])
    expect(opts.map((o) => o.code)).toEqual(['alipay_pc', 'wx_native', 'wx_lite'])
  })

  it('后端一个都没返回时，仍然给出两个占位项、都不可选', () => {
    const opts = buildChannelOptions([])
    expect(opts).toHaveLength(2)
    expect(opts.every((o) => !o.enabled)).toBe(true)
  })

  it('不硬编码 mock —— 上一版把渠道写死成 mock（SC-010），那个常量已删除', async () => {
    const pay = await import('@/api/pay')
    expect('MOCK_CHANNEL_CODE' in pay).toBe(false)
  })
})
