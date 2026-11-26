import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { OrderDetail } from '@/types'
import { InvoiceStatus, InvoiceTitleKind, InvoiceType, OrderStatus } from '@/types'

/**
 * 对公转账单 PDF（022）
 *
 * 钉住三件事：
 * · 内容模型：字段映射、订单状态、空值「—」、金额/时间格式、小计 = 单价 × 数量
 * · 文本安全：用户可控文本一律转义，不进入 HTML
 * · 导出编排：文件名、A4 分页、save 调用、离屏容器清理
 */

const pdfMocks = vi.hoisted(() => ({ addImage: vi.fn(), addPage: vi.fn(), save: vi.fn() }))
vi.mock('jspdf', () => ({
  jsPDF: class {
    addImage = pdfMocks.addImage
    addPage = pdfMocks.addPage
    save = pdfMocks.save
  },
}))
const html2canvasMock = vi.hoisted(() => vi.fn())
vi.mock('html2canvas', () => ({ default: html2canvasMock }))

import {
  buildFileName,
  buildOfflinePayPdfData,
  canExportOfflinePayPdf,
  escapeHtml,
  exportOfflinePayPdf,
  renderOfflinePayPdfHtml,
} from './offlinePayPdf'

const CREATE_AT = new Date(2026, 10, 24, 13, 57, 33).getTime()

function order(over: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: 1,
    no: 'O202611241357331',
    status: OrderStatus.UNPAID,
    createTime: CREATE_AT,
    payPrice: 30800,
    items: [
      {
        id: 11,
        spuName: '三防热敏标签纸',
        picUrl: '',
        properties: [{ propertyName: '版本', valueName: '标配' }],
        count: 2,
        price: 15000,
        payPrice: 30000,
      },
      {
        id: 12,
        spuName: '树脂碳带',
        picUrl: '',
        properties: [],
        count: 1,
        price: 800,
        payPrice: 800,
      },
    ],
    totalPrice: 30800,
    discountPrice: 0,
    deliveryPrice: 0,
    couponPrice: 0,
    pointPrice: 0,
    vipPrice: 0,
    payExpireTime: 0,
    receiverName: '陈先生',
    receiverMobile: '15088488833',
    receiverAreaName: '广东省 深圳市',
    receiverDetailAddress: 'xx 路 8 号',
    offlinePay: {
      waiting: true,
      accountName: '宸发科技',
      accountNo: '6222000011112222',
      bankName: '中国银行深圳分行',
      tip: '请在转账附言里写订单号',
    },
    ...over,
  }
}

const invoice = {
  status: InvoiceStatus.WAITING,
  type: InvoiceType.NORMAL,
  typeName: '电子普通发票',
  titleKind: InvoiceTitleKind.COMPANY,
  titleKindName: '企业',
  titleName: '某某科技有限公司',
  taxNo: '91440300MA5XXXXXXX',
  amount: 30800,
  fileUrl: null,
  issueTime: null,
  createTime: CREATE_AT,
}

function canvasOf(height: number) {
  return { width: 794, height, toDataURL: vi.fn(() => 'data:image/jpeg;base64,AAA') }
}

beforeEach(() => {
  pdfMocks.addImage.mockReset()
  pdfMocks.addPage.mockReset()
  pdfMocks.save.mockReset()
  html2canvasMock.mockReset()
})

describe('offlinePayPdf —— 入口判定', () => {
  it('对公订单（waiting 任意）且未取消 → 可导出', () => {
    expect(canExportOfflinePayPdf(order())).toBe(true)
    expect(
      canExportOfflinePayPdf(
        order({ status: OrderStatus.UNDELIVERED, offlinePay: { ...order().offlinePay!, waiting: false } }),
      ),
    ).toBe(true)
  })

  it('非对公订单与已取消订单（取消后 waiting 仍可能为 true）→ 不可导出', () => {
    expect(canExportOfflinePayPdf(order({ offlinePay: null }))).toBe(false)
    expect(canExportOfflinePayPdf(order({ status: OrderStatus.CANCELED }))).toBe(false)
  })
})

describe('offlinePayPdf —— 文件名与转义', () => {
  it('文件名包含订单号（Q165 示例）', () => {
    expect(buildFileName('O202611241357331')).toBe('对公转账单_O202611241357331.pdf')
  })

  it('文件名移除非法字符；订单号为空时用兜底名', () => {
    expect(buildFileName('O/2026:1*2?3"4<5>6|7')).toBe('对公转账单_O20261234567.pdf')
    expect(buildFileName('   ')).toBe('对公转账单_未知订单.pdf')
  })

  it('转义 HTML 五个字符', () => {
    expect(escapeHtml(`A<B&C>"D'E`)).toBe('A&lt;B&amp;C&gt;&quot;D&#39;E')
  })
})

describe('offlinePayPdf —— 内容模型', () => {
  it('待收款的订单状态是「待收款」，不是「待支付」', () => {
    expect(buildOfflinePayPdfData(order()).order.status).toBe('待收款')
  })

  it('已确认收款后按真实状态显示（待发货）', () => {
    const data = buildOfflinePayPdfData(
      order({
        status: OrderStatus.UNDELIVERED,
        offlinePay: { ...order().offlinePay!, waiting: false },
      }),
    )
    expect(data.order.status).toBe('待发货')
  })

  it('账户、订单、收货与商品逐项映射（金额与时间格式化）', () => {
    const data = buildOfflinePayPdfData(order())

    expect(data.fileName).toBe('对公转账单_O202611241357331.pdf')
    expect(data.account).toEqual({
      accountName: '宸发科技',
      accountNo: '6222000011112222',
      bankName: '中国银行深圳分行',
      tip: '请在转账附言里写订单号',
    })
    expect(data.order).toEqual({
      no: 'O202611241357331',
      createTime: '2026-11-24 13:57:33',
      status: '待收款',
      payPrice: '¥308.00',
    })
    expect(data.receiver).toEqual({
      name: '陈先生',
      mobile: '15088488833',
      address: '广东省 深圳市 xx 路 8 号',
    })
    expect(data.items).toEqual([
      { name: '三防热敏标签纸', spec: '版本：标配', unitPrice: '¥150.00', count: 2, subtotal: '¥300.00' },
      { name: '树脂碳带', spec: '—', unitPrice: '¥8.00', count: 1, subtotal: '¥8.00' },
    ])
  })

  it('金额明细固定 7 行，优惠为负、0 如实显示，末行加粗', () => {
    const data = buildOfflinePayPdfData(
      order({ discountPrice: 1000, couponPrice: 3000, pointPrice: 500, vipPrice: 200 }),
    )

    expect(data.amounts).toEqual([
      { label: '商品小计', value: '¥308.00' },
      { label: '促销优惠', value: '-¥10.00' },
      { label: '运费', value: '¥0.00' },
      { label: '优惠券抵扣', value: '-¥30.00' },
      { label: '积分抵扣', value: '-¥5.00' },
      { label: '会员优惠', value: '-¥2.00' },
      { label: '应付总额', value: '¥308.00', total: true },
    ])
  })

  it('账户字段缺失时显示「—」，不编造（008 口径）', () => {
    const data = buildOfflinePayPdfData(
      order({
        offlinePay: { waiting: true, accountName: '', accountNo: undefined, bankName: ' ', tip: '' },
        receiverName: '',
        receiverAreaName: '',
        receiverDetailAddress: '',
      }),
    )

    expect(data.account.accountName).toBe('—')
    expect(data.account.accountNo).toBe('—')
    expect(data.account.bankName).toBe('—')
    expect(data.account.tip).toBeNull()
    expect(data.receiver.name).toBe('—')
    expect(data.receiver.address).toBe('—')
  })

  it('已申请且未作废的发票进入模型；未申请与已作废为 null（Q169）', () => {
    expect(buildOfflinePayPdfData(order({ invoice: invoice as any })).invoice).toEqual({
      typeName: '电子普通发票',
      titleKindName: '企业',
      titleName: '某某科技有限公司',
      taxNo: '91440300MA5XXXXXXX',
      amount: '¥308.00',
    })
    expect(buildOfflinePayPdfData(order({ invoice: null })).invoice).toBeNull()
    expect(
      buildOfflinePayPdfData(order({ invoice: { ...invoice, status: InvoiceStatus.CANCELED } as any }))
        .invoice,
    ).toBeNull()
  })

  it('买家留言去空格后为空则不渲染', () => {
    expect(buildOfflinePayPdfData(order({ userRemark: '   ' })).userRemark).toBeNull()
    expect(buildOfflinePayPdfData(order({ userRemark: ' 请尽快发货 ' })).userRemark).toBe('请尽快发货')
  })
})

describe('offlinePayPdf —— HTML 渲染', () => {
  it('九个章节中应渲染的都在，且不带 <img>', () => {
    const html = renderOfflinePayPdfHtml(
      buildOfflinePayPdfData(
        order({ userRemark: '请尽快发货', invoice: invoice as any }),
      ),
    )

    for (const text of [
      '对公转账单',
      '收款账户',
      '订单信息',
      '订单状态',
      '待收款',
      '采购商品清单',
      '金额明细',
      '收货信息',
      '开票信息',
      '买家留言',
      '请尽快发货',
    ]) {
      expect(html).toContain(text)
    }
    expect(html).not.toContain('<img')
  })

  it('用户可控文本被转义（商品名 / 留言 / 抬头）', () => {
    const html = renderOfflinePayPdfHtml(
      buildOfflinePayPdfData(
        order({
          items: [
            {
              id: 11,
              spuName: 'A<B&C>"D\'E',
              picUrl: '',
              properties: [{ propertyName: '规格', valueName: '<b>X</b>' }],
              count: 1,
              price: 100,
              payPrice: 100,
            },
          ],
          userRemark: '<script>alert(1)</script>',
          invoice: { ...invoice, titleName: 'A<B>' } as any,
        }),
      ),
    )

    expect(html).toContain('A&lt;B&amp;C&gt;&quot;D&#39;E')
    expect(html).toContain('&lt;b&gt;X&lt;/b&gt;')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('A&lt;B&gt;')
    expect(html).not.toContain('<script>alert(1)</script>')
  })
})

describe('offlinePayPdf —— 导出编排', () => {
  it('单页：写一次图、不加页，保存文件名正确，并清理离屏容器', async () => {
    html2canvasMock.mockResolvedValue(canvasOf(1123))

    await exportOfflinePayPdf(order())

    expect(pdfMocks.addImage).toHaveBeenCalledTimes(1)
    expect(pdfMocks.addPage).not.toHaveBeenCalled()
    expect(pdfMocks.save).toHaveBeenCalledWith('对公转账单_O202611241357331.pdf')
    expect(document.body.querySelector('.offline-pay-pdf-root')).toBeNull()
  })

  it('超高内容自动分页（不截断）', async () => {
    // 图片高 ≈ 806mm，一页内容区 277mm → 需要额外 2 页
    html2canvasMock.mockResolvedValue(canvasOf(3369))

    await exportOfflinePayPdf(order())

    expect(pdfMocks.addImage).toHaveBeenCalledTimes(3)
    expect(pdfMocks.addPage).toHaveBeenCalledTimes(2)
  })

  it('生成失败时向上抛错，且仍清理离屏容器', async () => {
    html2canvasMock.mockRejectedValue(new Error('canvas failed'))

    await expect(exportOfflinePayPdf(order())).rejects.toThrow('canvas failed')
    expect(pdfMocks.save).not.toHaveBeenCalled()
    expect(document.body.querySelector('.offline-pay-pdf-root')).toBeNull()
  })
})
