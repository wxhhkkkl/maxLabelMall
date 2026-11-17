import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LicenseSummaryCards from './LicenseSummaryCards.vue'

/**
 * 看板卡片（020 / US67 / Q163）
 *
 * 钉三件事：
 * · 个人卡片**只显示订阅会员**（Q163，席位会员不再在看板单独展示）
 * · 当年卡片按「新获得 / 延续」两组，各自 个人会员 + 企业席位 + 合计
 * · 合计 = 个人会员数 + 企业席位数（两者单位不同，不是客户数）
 */
const SUMMARY = {
  personalActiveCount: 128,
  personalSeatOnlyCount: 23,
  orgActiveCount: 12,
  orgSeatActiveCount: 86,
  orgSeatTotalCount: 120,
  yearNewCount: 34,
  yearRenewCount: 21,
  yearTotalCount: 49,
  yearNewPersonalCount: 30,
  yearNewOrgSeatCount: 40,
  yearRenewPersonalCount: 18,
  yearRenewOrgSeatCount: 15,
  year: 2026
}

describe('授权看板卡片', () => {
  it('个人授权只显示订阅会员，不再显示席位会员', () => {
    const w = mount(LicenseSummaryCards, { props: { summary: SUMMARY } })

    expect(w.text()).toContain('订阅会员')
    expect(w.text()).toContain('128')
    expect(w.text()).not.toContain('席位会员')
  })

  it('当年卡片两组都拆成 个人会员 / 企业席位 / 合计', () => {
    const w = mount(LicenseSummaryCards, { props: { summary: SUMMARY } })

    expect(w.text()).toContain('新获得授权')
    expect(w.text()).toContain('延续授权')
    expect(w.text()).toContain('个人会员')
    expect(w.text()).toContain('企业席位')
    expect(w.text()).toContain('30')
    expect(w.text()).toContain('40')
    expect(w.text()).toContain('70') // 新获得合计 = 30 + 40
    expect(w.text()).toContain('18')
    expect(w.text()).toContain('15')
    expect(w.text()).toContain('33') // 延续合计 = 18 + 15
  })

  it('企业有效数与席位占用都展示', () => {
    const w = mount(LicenseSummaryCards, { props: { summary: SUMMARY } })

    expect(w.text()).toContain('企业有效授权')
    expect(w.text()).toContain('12')
    expect(w.text()).toContain('86 / 120')
  })

  it('没有数据时显示 0 而不是空白', () => {
    const w = mount(LicenseSummaryCards, { props: { summary: undefined } })

    expect(w.text()).toContain('0')
  })
})
