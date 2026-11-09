import { beforeEach, describe, expect, it } from 'vitest'

import {
  getLastInvoiceChoice,
  getLastInvoiceTitleId,
  saveLastInvoiceChoice,
  saveLastInvoiceTitleId
} from './last-invoice'

describe('last-invoice —— 上次发票选择的本地记忆', () => {
  beforeEach(() => window.localStorage.clear())

  it('票种与抬头类型能原样读写', () => {
    saveLastInvoiceChoice({ type: 2, titleKind: 1 })
    expect(getLastInvoiceChoice()).toEqual({ type: 2, titleKind: 1 })
  })

  it('没有 / 损坏的值 → 返回 null，不抛错', () => {
    expect(getLastInvoiceChoice()).toBeNull()
    window.localStorage.setItem('MAXLABEL_LAST_INVOICE_CHOICE', '{bad json')
    expect(getLastInvoiceChoice()).toBeNull()
  })

  it('抬头 id 只接受正整数', () => {
    saveLastInvoiceTitleId(9)
    expect(getLastInvoiceTitleId()).toBe(9)
    saveLastInvoiceTitleId(0)
    expect(getLastInvoiceTitleId()).toBe(9)
  })
})
