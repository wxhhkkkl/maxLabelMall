import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: (...a: unknown[]) => put(...a),
  del: (...a: unknown[]) => del(...a),
}))

const { listInvoiceTitles, createInvoiceTitle, updateInvoiceTitle, deleteInvoiceTitle, createOrderInvoice } =
  await import('./invoice')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
  del.mockReset()
})

/**
 * 发票抬头簿与补开（006 contracts/app-api.md §1/§2）。
 *
 * ⚠️ 路径与参数形态要与契约**逐字一致**：这五个接口后端都是新写的，
 * 拼错路径的后果是 404 而不是"参数被忽略"，但**同样不会在类型层面报错**。
 */
describe('发票抬头簿', () => {
  it('列表走 GET /trade/invoice-title/list', async () => {
    get.mockResolvedValue([])
    await listInvoiceTitles()
    expect(get).toHaveBeenCalledWith('/trade/invoice-title/list')
  })

  it('新增走 POST /trade/invoice-title/create，body 就是表单', async () => {
    post.mockResolvedValue(7)
    const form = { titleName: '南京路桥机械有限公司', taxNo: '91320104660690975E', defaultStatus: true }
    await createInvoiceTitle(form)
    expect(post).toHaveBeenCalledWith('/trade/invoice-title/create', form)
  })

  it('修改带上 id', async () => {
    put.mockResolvedValue(true)
    await updateInvoiceTitle(3, { titleName: '张三', defaultStatus: false })
    expect(put).toHaveBeenCalledWith('/trade/invoice-title/update', {
      id: 3,
      titleName: '张三',
      defaultStatus: false,
    })
  })

  it('删除按 id', async () => {
    del.mockResolvedValue(true)
    await deleteInvoiceTitle(3)
    expect(del).toHaveBeenCalledWith('/trade/invoice-title/delete', { id: 3 })
  })
})

describe('订单详情补开', () => {
  it('走 POST /trade/order-invoice/create，带上 orderId', async () => {
    post.mockResolvedValue(77)
    await createOrderInvoice({
      orderId: 1001,
      type: 2,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
    })
    expect(post).toHaveBeenCalledWith('/trade/order-invoice/create', {
      orderId: 1001,
      type: 2,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
    })
  })
})
