import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/config/axios', () => ({
  default: {
    get: (...a: unknown[]) => get(...a)
  }
}))

const { getDeliveryDefaultAddress, getExpressMapUrl, getExpressTrackList } = await import('./index')

beforeEach(() => {
  get.mockReset()
})

describe('后台物流轨迹接口（010）', () => {
  it('带 silent: true —— 由详情页内联提示，不弹全局错误通知', async () => {
    get.mockResolvedValue([])

    await getExpressTrackList(1)

    expect(get.mock.calls[0]?.[0]).toMatchObject({
      url: '/trade/order/get-express-track-list?id=1',
      silent: true
    })
  })

  it('物流地图请求同样带 silent: true（010 追加）', async () => {
    get.mockResolvedValue('https://api.kuaidi100.com/tools/map/x')

    await getExpressMapUrl(1)

    expect(get.mock.calls[0]?.[0]).toMatchObject({
      url: '/trade/order/get-express-map-url?id=1',
      silent: true
    })
  })

  it('发货默认地址（010 追加）', async () => {
    get.mockResolvedValue({ areaId: 110108, address: '西北旺亮甲店永丰中路100号' })

    const def = await getDeliveryDefaultAddress()

    expect(def.areaId).toBe(110108)
    expect(get.mock.calls[0]?.[0]).toMatchObject({
      url: '/trade/order/get-delivery-default-address'
    })
  })
})
