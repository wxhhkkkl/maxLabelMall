import type { OrderDetail } from '@/types'
import { InvoiceStatus, OrderStatus } from '@/types'
import { formatYuan } from '@/utils/money'
import { OFFLINE_WAITING_TEXT, orderStatusText } from '@/utils/orderStatus'
import { formatDateTime } from '@/utils/time'

/**
 * 对公转账单 PDF（022）
 *
 * 数据全部来自订单详情响应（不新增接口）；本模块只做「内容模型 → A4 HTML → PDF」。
 * 字段与规则见 specs/022-offline-pay-pdf/data-model.md 与 contracts/pdf-content.md。
 *
 * ⚠️ `jspdf` / `html2canvas` **必须在导出时才动态 import**：它们只服务这一个低频按钮，
 *    静态引入会让首屏白增数百 KB。
 */

const DASH = '—'

/** 对公转账单 PDF 的内容模型 */
export interface OfflinePayPdfItem {
  name: string
  spec: string
  unitPrice: string
  count: number
  subtotal: string
}

export interface OfflinePayPdfAmount {
  label: string
  value: string
  total?: boolean
}

export interface OfflinePayPdfInvoice {
  typeName: string
  titleKindName: string
  titleName: string
  taxNo: string
  amount: string
}

export interface OfflinePayPdfData {
  fileName: string
  account: {
    accountName: string
    accountNo: string
    bankName: string
    tip: string | null
  }
  order: {
    no: string
    createTime: string
    status: string
    payPrice: string
  }
  receiver: {
    name: string
    mobile: string
    address: string
  }
  items: OfflinePayPdfItem[]
  amounts: OfflinePayPdfAmount[]
  invoice: OfflinePayPdfInvoice | null
  userRemark: string | null
}

/** 缺失值统一「—」，不编造（008 口径） */
function text(value?: string | null): string {
  const trimmed = (value ?? '').trim()
  return trimmed || DASH
}

/** 是否显示导出入口：**未取消**的对公转账订单（Q168） */
export function canExportOfflinePayPdf(order: OrderDetail): boolean {
  return !!order.offlinePay && order.status !== OrderStatus.CANCELED
}

/** `对公转账单_<订单号>.pdf`；去掉文件系统非法字符 */
export function buildFileName(orderNo: string): string {
  const safe = (orderNo ?? '').replace(/[\\/:*?"<>|]/g, '').trim()
  return `对公转账单_${safe || '未知订单'}.pdf`
}

/** 把用户可控文本转成 HTML 文本（不执行） */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 组装 PDF 内容模型（纯函数，可单测） */
export function buildOfflinePayPdfData(order: OrderDetail): OfflinePayPdfData {
  const pay = order.offlinePay
  const invoice = order.invoice
  const waiting = pay?.waiting === true

  return {
    fileName: buildFileName(order.no),
    account: {
      accountName: text(pay?.accountName),
      accountNo: text(pay?.accountNo),
      bankName: text(pay?.bankName),
      tip: pay?.tip?.trim() || null,
    },
    order: {
      no: text(order.no),
      createTime: order.createTime ? formatDateTime(order.createTime) : DASH,
      status: waiting ? OFFLINE_WAITING_TEXT : orderStatusText(order.status),
      payPrice: formatYuan(order.payPrice),
    },
    receiver: {
      name: text(order.receiverName),
      mobile: text(order.receiverMobile),
      address:
        [order.receiverAreaName, order.receiverDetailAddress]
          .map((part) => (part ?? '').trim())
          .filter(Boolean)
          .join(' ') || DASH,
    },
    items: order.items.map((item) => ({
      name: text(item.spuName),
      spec:
        (item.properties ?? [])
          .map((property) => `${property.propertyName}：${property.valueName}`)
          .join('；') || DASH,
      unitPrice: formatYuan(item.price),
      count: item.count,
      // 商品小计 = 单价 × 数量（运费与优惠在「金额明细」里单列）
      subtotal: formatYuan(item.price * item.count),
    })),
    amounts: [
      { label: '商品小计', value: formatYuan(order.totalPrice) },
      { label: '促销优惠', value: formatYuan(-order.discountPrice) },
      { label: '运费', value: formatYuan(order.deliveryPrice) },
      { label: '优惠券抵扣', value: formatYuan(-order.couponPrice) },
      { label: '积分抵扣', value: formatYuan(-order.pointPrice) },
      { label: '会员优惠', value: formatYuan(-order.vipPrice) },
      { label: '应付总额', value: formatYuan(order.payPrice), total: true },
    ],
    // 已作废的发票不进入凭证（Q169，与订单详情页一致）
    invoice:
      invoice && invoice.status !== InvoiceStatus.CANCELED
        ? {
            typeName: text(invoice.typeName),
            titleKindName: text(invoice.titleKindName),
            titleName: text(invoice.titleName),
            taxNo: text(invoice.taxNo),
            amount: formatYuan(invoice.amount),
          }
        : null,
    userRemark: order.userRemark?.trim() || null,
  }
}

function labelRows(rows: Array<[string, string]>): string {
  return rows
    .map(
      ([label, value]) =>
        `<div class="row"><span class="label">${escapeHtml(label)}</span><span class="value">${escapeHtml(value)}</span></div>`,
    )
    .join('')
}

/** 渲染 A4 版式的 HTML（宽 794px ≈ A4 @96dpi） */
export function renderOfflinePayPdfHtml(data: OfflinePayPdfData): string {
  const itemRows = data.items
    .map(
      (item, index) => `
        <tr>
          <td class="idx">${index + 1}</td>
          <td><div class="item-name">${escapeHtml(item.name)}</div><div class="item-spec">${escapeHtml(item.spec)}</div></td>
          <td class="num">${escapeHtml(item.unitPrice)}</td>
          <td class="num">×${item.count}</td>
          <td class="num">${escapeHtml(item.subtotal)}</td>
        </tr>`,
    )
    .join('')
  const amountRows = data.amounts
    .map(
      (amount) => `
        <tr class="${amount.total ? 'total' : ''}">
          <td>${escapeHtml(amount.label)}</td>
          <td class="num">${escapeHtml(amount.value)}</td>
        </tr>`,
    )
    .join('')

  return `
    <style>
      .offline-pay-pdf-root { width: 794px; box-sizing: border-box; padding: 40px; background: #fff; color: #1f2d3d; font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif; font-size: 14px; line-height: 1.6; }
      .offline-pay-pdf-root h1 { margin: 0 0 6px; font-size: 24px; letter-spacing: .5px; }
      .offline-pay-pdf-root .brand-line { margin: 0 0 24px; color: #6483a7; font-size: 12px; }
      .offline-pay-pdf-root h2 { margin: 22px 0 10px; font-size: 16px; border-left: 4px solid #2e7cd6; padding-left: 8px; }
      .offline-pay-pdf-root .row { display: flex; gap: 12px; padding: 3px 0; }
      .offline-pay-pdf-root .label { flex: none; width: 84px; color: #6483a7; }
      .offline-pay-pdf-root .value { flex: 1; word-break: break-all; }
      .offline-pay-pdf-root table { width: 100%; border-collapse: collapse; }
      .offline-pay-pdf-root th, .offline-pay-pdf-root td { padding: 8px 10px; border-bottom: 1px solid #e8eef5; text-align: left; vertical-align: top; }
      .offline-pay-pdf-root th { background: #f5f9ff; color: #3d4a66; font-weight: 600; }
      .offline-pay-pdf-root .num { text-align: right; white-space: nowrap; }
      .offline-pay-pdf-root .idx { width: 36px; color: #8c9ab0; }
      .offline-pay-pdf-root .item-name { font-weight: 600; }
      .offline-pay-pdf-root .item-spec { color: #8c9ab0; font-size: 12px; }
      .offline-pay-pdf-root tr.total td { font-weight: 700; color: #1f2d3d; background: #fbfdff; }
      .offline-pay-pdf-root .tip { margin: 8px 0 0; padding: 10px 12px; background: #fff8e6; color: #8a5a00; border-radius: 6px; font-size: 12px; }
      .offline-pay-pdf-root .remark { white-space: pre-wrap; word-break: break-all; }
    </style>
    <h1>宸发科技 · 对公转账单</h1>
    <p class="brand-line">本单为对公转账付款凭证，请按下方账户信息与订单号完成付款。</p>

    <h2>收款账户</h2>
    ${labelRows([
      ['收款户名', data.account.accountName],
      ['收款账号', data.account.accountNo],
      ['开户行', data.account.bankName],
    ])}
    ${data.account.tip ? `<p class="tip">${escapeHtml(data.account.tip)}</p>` : ''}

    <h2>订单信息</h2>
    ${labelRows([
      ['订单号', data.order.no],
      ['下单时间', data.order.createTime],
      ['订单状态', data.order.status],
      ['实付金额', data.order.payPrice],
    ])}

    <h2>采购商品清单</h2>
    <table>
      <thead><tr><th class="idx">#</th><th>商品</th><th class="num">单价</th><th class="num">数量</th><th class="num">小计</th></tr></thead>
      <tbody>${itemRows}</tbody>
    </table>

    <h2>金额明细</h2>
    <table>
      <tbody>${amountRows}</tbody>
    </table>

    <h2>收货信息</h2>
    ${labelRows([
      ['收货人', data.receiver.name],
      ['手机号', data.receiver.mobile],
      ['收货地址', data.receiver.address],
    ])}

    ${
      data.invoice
        ? `<h2>开票信息</h2>
    ${labelRows([
      ['票种', data.invoice.typeName],
      ['抬头类型', data.invoice.titleKindName],
      ['抬头', data.invoice.titleName],
      ['税号', data.invoice.taxNo],
      ['开票金额', data.invoice.amount],
    ])}`
        : ''
    }

    ${
      data.userRemark
        ? `<h2>买家留言</h2><p class="remark">${escapeHtml(data.userRemark)}</p>`
        : ''
    }
  `
}

/**
 * 生成并下载 PDF。
 *
 * 先排 A4 HTML → html2canvas 画布 → jsPDF 写入；内容超过一页时按固定切图法分页。
 */
export async function exportOfflinePayPdf(order: OrderDetail): Promise<void> {
  const data = buildOfflinePayPdfData(order)
  const host = document.createElement('div')
  host.className = 'offline-pay-pdf-root'
  host.style.position = 'fixed'
  host.style.left = '-10000px'
  host.style.top = '0'
  host.style.width = '794px'
  host.style.background = '#ffffff'
  host.innerHTML = renderOfflinePayPdfHtml(data)
  document.body.appendChild(host)

  try {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
      import('html2canvas'),
      import('jspdf'),
    ])
    const canvas = await html2canvas(host, {
      scale: 2,
      backgroundColor: '#ffffff',
      logging: false,
    })
    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' })
    const margin = 10
    const pageWidth = 210
    const pageHeight = 297
    const imageWidth = pageWidth - margin * 2
    const contentHeight = pageHeight - margin * 2
    const imageHeight = (canvas.height * imageWidth) / canvas.width
    const imageData = canvas.toDataURL('image/jpeg', 0.95)

    let heightLeft = imageHeight
    let position = margin
    pdf.addImage(imageData, 'JPEG', margin, position, imageWidth, imageHeight)
    heightLeft -= contentHeight
    while (heightLeft > 0) {
      position = heightLeft - imageHeight + margin
      pdf.addPage()
      pdf.addImage(imageData, 'JPEG', margin, position, imageWidth, imageHeight)
      heightLeft -= contentHeight
    }
    pdf.save(data.fileName)
  } finally {
    host.remove()
  }
}
