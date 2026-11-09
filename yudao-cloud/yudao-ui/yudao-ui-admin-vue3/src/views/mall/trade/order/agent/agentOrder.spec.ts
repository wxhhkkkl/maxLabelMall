import { describe, expect, it } from 'vitest'
import {
  agentDisplayName,
  buildAgentCreateReq,
  payPollingAction,
  paymentModeLabel,
  validateAgentOrder
} from './agentOrder'

describe('agentOrder', () => {
  it('缺少客户 / 商品 / 地址 / 支付方式时给出明确提示', () => {
    expect(validateAgentOrder({ items: [], paymentMode: 'wx_native' })).toBe('请先选择客户')
    expect(validateAgentOrder({ userId: 1, items: [], paymentMode: 'wx_native' })).toBe(
      '请至少添加一件商品'
    )
    expect(
      validateAgentOrder({
        userId: 1,
        items: [{ skuId: 1, count: 0 }],
        deliveryType: 3,
        paymentMode: 'wx_native'
      })
    ).toBe('商品数量必须大于 0')
    expect(
      validateAgentOrder({
        userId: 1,
        items: [{ skuId: 1, count: 1 }],
        deliveryType: 1,
        paymentMode: 'wx_native'
      })
    ).toBe('请选择客户的收货地址')
    expect(
      validateAgentOrder({
        userId: 1,
        items: [{ skuId: 1, count: 1 }],
        deliveryType: 3
      })
    ).toBe('请选择支付方式')
  })

  it('虚拟商品不要求地址', () => {
    expect(
      validateAgentOrder({
        userId: 1,
        items: [{ skuId: 1, count: 1 }],
        deliveryType: 3,
        paymentMode: 'alipay_pc'
      })
    ).toBeNull()
  })

  it('在线支付不传 payChannelCode；对公转账传 offline', () => {
    const online = buildAgentCreateReq({
      userId: 1,
      items: [{ skuId: 11, count: 2 }],
      addressId: 1024,
      paymentMode: 'wx_native'
    })
    expect(online.payChannelCode).toBeUndefined()
    expect('payChannelCode' in online).toBe(true) // 显式字段值为 undefined，序列化时省略

    const offline = buildAgentCreateReq({
      userId: 1,
      items: [{ skuId: 11, count: 2 }],
      paymentMode: 'offline'
    })
    expect(offline.payChannelCode).toBe('offline')
  })

  it('支付方式文案', () => {
    expect(paymentModeLabel('wx_native')).toBe('微信扫码')
    expect(paymentModeLabel('alipay_pc')).toBe('支付宝扫码')
    expect(paymentModeLabel('offline')).toBe('对公转账')
    expect(paymentModeLabel()).toBe('未选择')
  })

  it('支付轮询：已支付 / 已关闭 / 继续等', () => {
    expect(payPollingAction({ payStatus: true, orderStatus: 0 })).toBe('paid')
    expect(payPollingAction({ payStatus: false, orderStatus: 20 })).toBe('closed')
    expect(payPollingAction({ payStatus: false, orderStatus: 0 })).toBe('continue')
  })

  it('代下单人：昵称优先，编号兜底', () => {
    expect(agentDisplayName('运营小王', 1)).toBe('运营小王')
    expect(agentDisplayName(null, 1)).toBe('后台用户#1')
    expect(agentDisplayName()).toBe('—')
  })
})
