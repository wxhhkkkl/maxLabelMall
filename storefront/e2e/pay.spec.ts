import { expect, test } from '@playwright/test'

import { createAddress, smsLogin, testMobile } from './helpers'

/**
 * US5 支付闭环 —— 端到端（按故事测试先行交付）。
 *
 * 覆盖 SC-010：待支付订单走**模拟支付通道**变成「待发货」，**全程不需要任何第三方
 * 商户号**。
 *
 * 链路（见 contracts/app-api.md §6）：
 *   下单 → 响应 { id（交易订单）, payOrderId（支付单） }
 *   → 页面上选中 mock 渠道（`[data-channel="mock"]`）
 *   → `POST /pay/order/submit { id: payOrderId, channelCode: 'mock' }`
 *   → MockPayClient 立即返回成功
 *   → 支付模块在事务提交后**异步**回调 `/app-api/trade/order/update-paid`
 *   → 交易订单 status → `10 待发货`
 *
 * ⚠️ 因此**状态是后端推进的，不是前端标记的**（FR-039）：提交支付后必须重新拉取
 * 订单详情来确认状态。回调是异步的，所以这里用自动重试的断言等它变，而不是
 * 提交完就假定已经改好了。
 */
test.describe('US5 支付闭环', () => {
  test('待支付订单走模拟支付 → 变「待发货」（SC-010）', async ({ page }) => {
    const started = Date.now()
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    // 先造一笔待支付订单（复用 US4 已验证的直购路径）
    await page.goto('/mall')
    await page.locator('.p-card .p-title').first().click()
    await page.locator('.btn-buy').click()
    await page.locator('#submitOrder').click()
    await expect(page).toHaveURL(/\/order\/\d+/)
    await expect(page.locator('.ml-pill')).toHaveText('待支付')

    // 待支付就该给出支付入口
    const payBtn = page.locator('#payOrder')
    await expect(payBtn).toBeVisible()

    // ⚠️ 必须**先显式选中 mock 渠道**，不能再靠"默认就是 mock"：
    //    前端已改成从后端拉启用的渠道列表再渲染选择器（渠道码不再写死），
    //    而 e2e 跑的是租户 1，它名下还开着 alipay_pc 等渠道 —— 不点的话
    //    默认会选中排在前面的支付宝，那要真实商户号且应用已上线，CI 里必然失败。
    await page.locator('[data-channel="mock"]').click()
    await payBtn.click()

    // 状态以后端为准：等回调把订单推进到「待发货」。
    // 给足时间是因为要等后端异步回调，不是等超时。
    await expect(page.locator('.ml-pill')).toHaveText('待发货', { timeout: 30_000 })

    // 已付款订单**不再展示**支付入口（FR-040），也不再显示支付截止（FR-041）
    await expect(page.locator('#payOrder')).toHaveCount(0)
    await expect(page.locator('.od-deadline')).toHaveCount(0)

    // 记录墙钟时间，供 SC-002 / SC-003 人工判定（T114 要求）
    const seconds = ((Date.now() - started) / 1000).toFixed(1)
    console.log(`[SC-010] 从进站到支付完成共用时 ${seconds}s`)
  })

  test('「我的订单」里该单也变为「待发货」（状态全站同源）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    await page.goto('/mall')
    await page.locator('.p-card .p-title').first().click()
    await page.locator('.btn-buy').click()
    await page.locator('#submitOrder').click()
    await expect(page).toHaveURL(/\/order\/\d+/)

    await page.locator('#payOrder').click()
    await expect(page.locator('.ml-pill')).toHaveText('待发货', { timeout: 30_000 })

    // 列表页展示的是后端状态，不是详情页的本地副本
    await page.goto('/order')
    await page.locator('.cat-pills .tab', { hasText: '待发货' }).click()
    await expect(page.locator('.order-card')).toHaveCount(1)
    await expect(page.locator('.order-card').first().locator('.ml-pill')).toHaveText('待发货')
  })

  test('桌面浏览器不给「微信支付（公众号）」入口（wx_pub 只能在微信内跑）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    await page.goto('/mall')
    await page.locator('.p-card .p-title').first().click()
    await page.locator('.btn-buy').click()
    await page.locator('#submitOrder').click()
    await expect(page).toHaveURL(/\/order\/\d+/)

    // 桌面 chromium 的 UA 不含 MicroMessenger —— 即便后台启用了 wx_pub，
    // 也不该给出这个入口：它要会员的 openid，且只能在微信内置浏览器里唤起收银台。
    // 这条护栏同时钉住了「渠道选择器不再是无条件照搬后端列表」。
    await expect(page.locator('[data-channel="wx_pub"]')).toHaveCount(0)
  })
})
