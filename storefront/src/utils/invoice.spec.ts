import { describe, expect, it } from 'vitest'

import {
  INVOICE_TYPE_OPTIONS,
  invoiceFieldError,
  invoiceStatusName,
  isTaxNoValid,
} from './invoice'
import { InvoiceStatus, InvoiceType } from '@/types/invoice'

/**
 * 开票信息的前端校验（FR-155 / FR-156）。
 *
 * ⚠️ **规则必须与后端 `InvoiceValidator` 一致** —— 前端只是"提前拦住、少一次往返"，
 * **权威仍在后端**（绕过界面的请求后端照样拒）。两处不一致的后果是：
 * 界面放行、提交后报错，客户看到的是"我按你要求填了却提交不了"。
 */
describe('开票信息校验', () => {
  const normal = { titleName: '张三' }
  const special = {
    titleName: '南京路桥机械有限公司',
    taxNo: '91320104660690975E',
    titleAddress: '南京市玄武区某某路 1 号',
    titlePhone: '18913984596',
    bankName: '工商银行南京分行 6222 0000 0000 0000',
  }

  it('普票：只填名称就能过', () => {
    expect(invoiceFieldError(normal, InvoiceType.NORMAL)).toBeNull()
  })

  it('两种票：名称都是必填，且提示点名是哪一项', () => {
    expect(invoiceFieldError({ titleName: '' }, InvoiceType.NORMAL)).toContain('抬头名称')
    expect(invoiceFieldError({ titleName: '   ' }, InvoiceType.SPECIAL)).toContain('抬头名称')
    expect(invoiceFieldError({}, InvoiceType.NORMAL)).toContain('抬头名称')
  })

  it('专票：缺哪一项就提示哪一项', () => {
    expect(invoiceFieldError({ titleName: '公司' }, InvoiceType.SPECIAL)).toContain('纳税人识别号')
    expect(invoiceFieldError({ ...special, titleAddress: ' ' }, InvoiceType.SPECIAL)).toContain('注册地址')
    expect(invoiceFieldError({ ...special, titlePhone: '' }, InvoiceType.SPECIAL)).toContain('注册电话')
    expect(invoiceFieldError({ ...special, bankName: undefined }, InvoiceType.SPECIAL)).toContain('开户行')
  })

  it('专票：五项填全就过', () => {
    expect(invoiceFieldError(special, InvoiceType.SPECIAL)).toBeNull()
  })

  it('税号是选填，但填了就必须合法 —— 两种票都一样', () => {
    for (const type of [InvoiceType.NORMAL, InvoiceType.SPECIAL] as const) {
      const form = type === InvoiceType.SPECIAL ? { ...special, taxNo: 'abc' } : { titleName: '张三', taxNo: 'abc' }
      expect(invoiceFieldError(form, type)).toContain('纳税人识别号')
    }
  })

  it('税号格式：15 / 18 / 20 位数字与大写字母；小写与短位数都不行', () => {
    expect(isTaxNoValid('123456789012345')).toBe(true) // 15 位（老税号）
    expect(isTaxNoValid('91320104660690975E')).toBe(true) // 18 位（统一社会信用代码）
    expect(isTaxNoValid('12345678901234567890')).toBe(true) // 20 位
    expect(isTaxNoValid('12345678901234')).toBe(false) // 14 位
    expect(isTaxNoValid('91320104660690975e')).toBe(false) // 小写字母
    expect(isTaxNoValid('9132010466069097-E')).toBe(false) // 非法字符
  })

  it('票种选项与后端枚举逐字对齐（1 普票 / 2 专票），别漂移', () => {
    expect(INVOICE_TYPE_OPTIONS.map(o => o.value)).toEqual([InvoiceType.NORMAL, InvoiceType.SPECIAL])
    expect(INVOICE_TYPE_OPTIONS.map(o => o.label)).toEqual(['电子普通发票', '电子增值税专用发票'])
  })


  it('状态文案用词与后端枚举一致，不自行造同义词（SC-061 的可读性前提）', () => {
    expect(invoiceStatusName(InvoiceStatus.WAITING)).toBe('待开票')
    expect(invoiceStatusName(InvoiceStatus.ISSUED)).toBe('已开票')
    expect(invoiceStatusName(InvoiceStatus.CANCELED)).toBe('已作废')
  })
})
