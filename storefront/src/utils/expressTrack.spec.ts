import { describe, expect, it } from 'vitest'

import {
  EXPRESS_MAP_NOT_AVAILABLE,
  EXPRESS_MAP_NOT_CONFIGURED,
  EXPRESS_QUERY_NOT_SUPPORTED,
  mapErrorText,
  trackErrorText,
} from './expressTrack'

/**
 * 物流失败的文案映射（010 / US37 / FR-259 / FR-261）。
 *
 * 「不支持查询」与「暂时拿不到」是两件事：前者是数据问题（快递公司没配编码），
 * 后者是三方/网络问题。客户看到的提示必须能区分，否则会以为系统总在抽风。
 */
describe('trackErrorText', () => {
  it('编码不支持 → 「该快递公司暂不支持查询」', () => {
    expect(trackErrorText({ code: EXPRESS_QUERY_NOT_SUPPORTED })).toBe(
      '该快递公司暂不支持查询',
    )
  })

  it('其它错误（三方报错 / 网络 / 未知）→ 统一降级文案', () => {
    expect(trackErrorText({ code: 1_011_004_102 })).toBe('暂时无法获取物流信息，请稍后重试')
    expect(trackErrorText(new Error('boom'))).toBe('暂时无法获取物流信息，请稍后重试')
    expect(trackErrorText(undefined)).toBe('暂时无法获取物流信息，请稍后重试')
  })
})

describe('mapErrorText', () => {
  it('未配置发货地址 / 地图模板 → 明确说「暂未配置」', () => {
    expect(mapErrorText({ code: EXPRESS_MAP_NOT_CONFIGURED })).toBe('物流地图暂未配置，请联系管理员')
  })

  it('历史单没有地区 / 无法生成 → 明确说「暂无法生成」', () => {
    expect(mapErrorText({ code: EXPRESS_MAP_NOT_AVAILABLE })).toBe('该订单暂无法生成物流地图')
  })

  it('快递公司不支持 / 其它失败 → 对应文案', () => {
    expect(mapErrorText({ code: EXPRESS_QUERY_NOT_SUPPORTED })).toBe('该快递公司暂不支持查询')
    expect(mapErrorText(new Error('boom'))).toBe('暂时无法获取物流地图，请稍后重试')
  })
})
