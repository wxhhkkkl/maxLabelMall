/**
 * 「上次发票选择」的本地记忆。
 *
 * 只记**选择**（票种 + 抬头类型 + 抬头 id），不记税号、银行账户等敏感字段 ——
 * 那些继续从抬头簿（服务端）取，避免在 localStorage 里多存一份。
 *
 * 下次打开结算页/补开弹层时：
 *   · 票种与抬头类型按这里恢复；
 *   · 抬头簿选择器优先选中这里记的抬头 id（不存在或票种不匹配时退回默认抬头）。
 */
import type { InvoiceTitleKindValue, InvoiceTypeValue } from '@/types'

export interface LastInvoiceChoice {
  type: InvoiceTypeValue
  titleKind: InvoiceTitleKindValue
}

const CHOICE_KEY = 'MAXLABEL_LAST_INVOICE_CHOICE'
const TITLE_KEY = 'MAXLABEL_LAST_INVOICE_TITLE'

function store(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function getLastInvoiceChoice(): LastInvoiceChoice | null {
  try {
    const raw = store()?.getItem(CHOICE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<LastInvoiceChoice>
    if (typeof parsed?.type !== 'number' || typeof parsed?.titleKind !== 'number') return null
    return { type: parsed.type as InvoiceTypeValue, titleKind: parsed.titleKind as InvoiceTitleKindValue }
  } catch {
    return null
  }
}

export function saveLastInvoiceChoice(choice: LastInvoiceChoice): void {
  try {
    store()?.setItem(CHOICE_KEY, JSON.stringify(choice))
  } catch {
    // 隐私模式 / 配额满：记不住不影响开票
  }
}

export function getLastInvoiceTitleId(): number | null {
  try {
    const value = Number(store()?.getItem(TITLE_KEY))
    return Number.isInteger(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

export function saveLastInvoiceTitleId(id: number): void {
  if (!Number.isInteger(id) || id <= 0) return
  try {
    store()?.setItem(TITLE_KEY, String(id))
  } catch {
    // 同上
  }
}
