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

/**
 * 环境过滤：`wx_pub`（公众号 JSAPI）只能在微信内置浏览器里跑，`wx_native`（扫码）
 * 只适合微信外。**不匹配的环境整条不渲染** —— 注意不能渲染成 `comingSoon`
 * （那是「后台没启用」的语义，而这里是「后台启用了、但当前浏览器执行不了」，
 * 两者对用户的含义完全不同）。
 *
 * 可用性仍是后端说了算：这里只在后端返回的启用集合内部做**减法**。
 */
describe('buildChannelOptions —— 按运行环境过滤', () => {
  it('微信外不渲染 wx_pub（后端启用了也不行）', () => {
    const opts = buildChannelOptions(['alipay_pc', 'wx_pub'], false)
    expect(opts.map((o) => o.code)).toEqual(['alipay_pc', 'wx_native'])
    expect(opts.find((o) => o.code === 'wx_pub')).toBeUndefined()
  })

  it('微信内渲染 wx_pub，且不给扫码渠道留占位', () => {
    const opts = buildChannelOptions(['alipay_pc', 'wx_pub'], true)
    expect(opts.map((o) => o.code)).toEqual(['alipay_pc', 'wx_pub'])
    expect(opts.find((o) => o.code === 'wx_native')).toBeUndefined()
  })

  it('微信内 wx_pub 启用时可选，文案与扫码渠道区分开', () => {
    const opts = buildChannelOptions(['wx_pub'], true)
    expect(opts.find((o) => o.code === 'wx_pub')).toEqual({
      code: 'wx_pub',
      label: '微信支付（公众号）',
      enabled: true,
      comingSoon: false,
    })
  })

  it('微信内后端没启用 wx_pub 时，才轮到「即将上线」占位', () => {
    const opts = buildChannelOptions([], true)
    expect(opts.find((o) => o.code === 'wx_pub')).toMatchObject({ enabled: false, comingSoon: true })
  })

  it('未知编码不受环境过滤影响 —— 后端新配的渠道照样显示', () => {
    const opts = buildChannelOptions(['wx_lite'], true)
    expect(opts.map((o) => o.code)).toContain('wx_lite')
  })
})
