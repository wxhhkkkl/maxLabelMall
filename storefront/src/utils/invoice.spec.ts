import { describe, expect, it } from 'vitest'

import {
  INVOICE_TITLE_KIND_OPTIONS,
  INVOICE_TYPE_OPTIONS,
  invoiceFieldError,
  invoiceStatusName,
  isTaxNoValid,
} from './invoice'
import { InvoiceStatus, InvoiceTitleKind, InvoiceType, type InvoiceTitleKindValue } from '@/types/invoice'

/**
 * 开票信息的前端校验（FR-155 / FR-156 / Q37 / Q38）。
 *
 * ⚠️ **规则必须与后端 `InvoiceValidator` 一致** —— 前端只是"提前拦住、少一次往返"，
 * **权威仍在后端**（绕过界面的请求后端照样拒）。两处不一致的后果是：
 * 界面放行、提交后报错，客户看到的是"我按你要求填了却提交不了"。
 */
describe('开票信息校验', () => {
  /** 企业普票：名称 + 税号 */
  const company = {
    type: InvoiceType.NORMAL,
    titleKind: InvoiceTitleKind.COMPANY,
    titleName: '南京路桥机械有限公司',
    taxNo: '91320104660690975E',
  }
  const companySpecial = {
    ...company,
    type: InvoiceType.SPECIAL,
    titleAddress: '南京市玄武区某某路 1 号',
    titlePhone: '18913984596',
    bankName: '工商银行南京分行 6222 0000 0000 0000',
  }
  /** 个人普票：只要名称 */
  const personal = {
    type: InvoiceType.NORMAL,
    titleKind: InvoiceTitleKind.PERSONAL,
    titleName: '张三',
  }

  it('**企业**抬头：两种票都必填「名称 + 税号」，地址/电话/开户行一律选填', () => {
    expect(invoiceFieldError(company)).toBeNull()
    expect(invoiceFieldError(companySpecial)).toBeNull()
    // 专票只填名称+税号也过（Q37：那三栏是选填）
    expect(invoiceFieldError({ ...company, type: InvoiceType.SPECIAL })).toBeNull()
  })

  it('企业抬头缺税号 → 拦下来，并点名是哪一项', () => {
    expect(invoiceFieldError({ ...company, taxNo: '' })).toContain('纳税人识别号')
    expect(invoiceFieldError({ ...company, taxNo: '   ' })).toContain('纳税人识别号')
  })

  // ==================== 个人抬头（Q38） ====================

  it('**个人**抬头：只填名称就过（个人本来就没有纳税人识别号）', () => {
    expect(invoiceFieldError(personal)).toBeNull()
  })

  it('个人抬头的税号是**选填、不是禁止** —— 填了身份证号照样过', () => {
    expect(invoiceFieldError({ ...personal, taxNo: '320102199001011234' })).toBeNull()
  })

  it('个人抬头填了税号也要**合法**（选填不等于不校验）', () => {
    expect(invoiceFieldError({ ...personal, taxNo: 'abc' })).toContain('纳税人识别号')
  })

  it('**「个人 + 专票」不成立** —— 专票按税法只能开给企业', () => {
    expect(invoiceFieldError({ ...personal, type: InvoiceType.SPECIAL })).toContain('只能开给企业')
  })

  it('**个人抬头也必须填名称**', () => {
    expect(invoiceFieldError({ ...personal, titleName: '  ' })).toContain('抬头名称')
  })

  it('抬头类型没选 / 非法 → 先要它，别去报后来的字段', () => {
    expect(invoiceFieldError({ type: InvoiceType.NORMAL, titleName: '张三' })).toContain('抬头类型')
    // 非法取值只可能来自绕过界面的请求（后端 `InvoiceValidator` 同样要拒）——
    // 这里得强转，因为类型系统本来就该把 9 挡在编译期
    expect(invoiceFieldError({
      type: InvoiceType.NORMAL, titleKind: 9 as InvoiceTitleKindValue, titleName: '张三',
    })).toContain('抬头类型')
  })

  // ==================== 名称 / 税号 ====================

  it('名称是所有抬头都必填的，且提示点名是哪一项', () => {
    for (const kind of [InvoiceTitleKind.COMPANY, InvoiceTitleKind.PERSONAL] as const) {
      expect(invoiceFieldError({ type: InvoiceType.NORMAL, titleKind: kind, titleName: '' })).toContain('抬头名称')
      expect(invoiceFieldError({ type: InvoiceType.NORMAL, titleKind: kind, titleName: '   ' })).toContain('抬头名称')
    }
  })

  it('税号填了就必须合法 —— 企业与个人都一样', () => {
    expect(invoiceFieldError({ ...company, taxNo: 'abc' })).toContain('纳税人识别号')
    expect(invoiceFieldError({ ...personal, taxNo: 'abc' })).toContain('纳税人识别号')
  })

  it('税号格式：15 / 18 / 20 位数字与大写字母；小写与短位数都不行', () => {
    expect(isTaxNoValid('123456789012345')).toBe(true) // 15 位（老税号）
    expect(isTaxNoValid('91320104660690975E')).toBe(true) // 18 位（统一社会信用代码）
    expect(isTaxNoValid('12345678901234567890')).toBe(true) // 20 位
    expect(isTaxNoValid('12345678901234')).toBe(false) // 14 位
    expect(isTaxNoValid('91320104660690975e')).toBe(false) // 小写字母
    expect(isTaxNoValid('9132010466069097-E')).toBe(false) // 非法字符
  })

  // ==================== 选项与后端枚举对齐 ====================

  it('票种选项与后端枚举逐字对齐（1 普票 / 2 专票），别漂移', () => {
    expect(INVOICE_TYPE_OPTIONS.map(o => o.value)).toEqual([InvoiceType.NORMAL, InvoiceType.SPECIAL])
    expect(INVOICE_TYPE_OPTIONS.map(o => o.label)).toEqual(['电子普通发票', '电子增值税专用发票'])
  })

  it('抬头类型选项与后端枚举逐字对齐（1 企业 / 2 个人），别漂移', () => {
    expect(INVOICE_TITLE_KIND_OPTIONS.map(o => o.value)).toEqual([InvoiceTitleKind.COMPANY, InvoiceTitleKind.PERSONAL])
    expect(INVOICE_TITLE_KIND_OPTIONS.map(o => o.label)).toEqual(['企业', '个人'])
  })

  it('状态文案用词与后端枚举一致，不自行造同义词（SC-061 的可读性前提）', () => {
    expect(invoiceStatusName(InvoiceStatus.WAITING)).toBe('待开票')
    expect(invoiceStatusName(InvoiceStatus.ISSUED)).toBe('已开票')
    expect(invoiceStatusName(InvoiceStatus.CANCELED)).toBe('已作废')
  })
})
