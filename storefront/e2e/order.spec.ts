import { expect, test } from '@playwright/test'

import { createAddress, smsLogin, testMobile } from './helpers'

/**
 * US4 结算与下单 —— 端到端（按故事测试先行交付）。
 *
 * 覆盖 SC-003：从详情页「立即购买」→ 选地址 → 提交 → 订单出现在「我的订单」且为「待支付」。
 *
 * ⚠️ 这里**故意从 UI 一路点到底**，而不是直接调接口造订单。因为结算页的入参
 * 走的是 query → 路由 props 的映射，只测组件 props 是测不到那段接线的
 * —— 早先正是「组件测试全绿、真实链路里 source 恒为默认值、skuId 恒为 undefined」。
 */
test.describe('US4 结算与下单', () => {
  test('详情页「立即购买」→ 提交 → 订单出现在「我的订单」且为待支付（SC-003）', async ({
    page,
  }) => {
    const started = Date.now()
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    // 从商城进入第一个商品的详情页
    await page.goto('/mall')
    // ⚠️ 这里就点**第一张卡**，别自作聪明按价格筛。种子商品里既有 ¥0.01 的
    // 「1 分小商品」（**可正常下单**），也有库存 0 件或需要选规格的（如「测试商品」）——
    // 按「不是 ¥0.01」去筛，反而会挑到后者，表现为「立即购买」后停在商品页。
    // 若日后要换选品策略，必须先确认候选商品的**库存与规格**。
    await page.locator('.p-card .p-title').first().click()
    const productName = await page.locator('.pd-name').innerText()

    // 立即购买：直购不经过购物车（FR-024）
    await page.locator('.btn-buy').click()
    await expect(page).toHaveURL(/\/checkout/)

    // **结算页确实在结算这件商品** —— 这一步就是在守 query → props 的接线
    await expect(page.locator('.co-name').first()).toHaveText(productName)
    await expect(page.locator('.ml-amount-row.is-total')).toBeVisible()

    // 金额明细五行齐全，且没有积分行（FR-026g）
    for (const label of ['商品小计', '促销优惠', '运费', '优惠券抵扣', '应付总额']) {
      await expect(page.locator('.ml-amount-card')).toContainText(label)
    }
    await expect(page.locator('.ml-amount-card')).not.toContainText('积分')

    await page.locator('#submitOrder').click()

    // 进入订单详情，状态是「待支付」，并给出支付截止时间（FR-041）
    await expect(page).toHaveURL(/\/order\/\d+/)
    await expect(page.locator('.ml-pill')).toHaveText('待支付')
    await expect(page.locator('.od-deadline')).toContainText('支付截止')

    // 订单确实出现在「我的订单」里（SC-003 的断言目标）
    await page.goto('/order')
    await expect(page.locator('.order-card').first()).toBeVisible()
    await expect(page.locator('.order-card').first().locator('.ml-pill')).toHaveText('待支付')
    await expect(page.locator('.order-card').first()).toContainText(productName)

    // 记录墙钟时间供人工判定（T140）
    const seconds = ((Date.now() - started) / 1000).toFixed(1)
    console.log(`[SC-003] 从进站到下单成功并在「我的订单」可见共用时 ${seconds}s`)
  })

  test('「我的订单」按状态筛选，且待支付可取消（FR-034 / FR-036）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    await page.goto('/mall')
    await page.locator('.p-card .p-title').first().click()
    await page.locator('.btn-buy').click()
    await page.locator('#submitOrder').click()
    await expect(page).toHaveURL(/\/order\/\d+/)

    await page.goto('/order')
    // 新账号只有这一笔，「待支付」筛选下应当看得到
    await page.locator('.cat-pills .tab', { hasText: '待支付' }).click()
    await expect(page.locator('.order-card')).toHaveCount(1)

    // 取消是不可撤销的，必须先二次确认
    await page.locator('.cancel-order').first().click()
    await expect(page.locator('.ml-modal')).toBeVisible()
    await page.locator('#confirmCancel').click()

    // 取消后它不再属于「待支付」，当前筛选下应当消失
    await expect(page.locator('.order-card')).toHaveCount(0)

    // 切到「已取消」能看到它 —— 状态由后端改成，前端只是重新拉了一次列表
    await page.locator('.cat-pills .tab', { hasText: '已取消' }).click()
    await expect(page.locator('.order-card').first().locator('.ml-pill')).toHaveText('已取消')
  })

  test('购物车去结算只带被勾选的条目（FR-025）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    await page.goto('/mall')
    await page.locator('.p-card .p-btn').first().click()
    await expect(page.locator('.cart-chip')).toContainText('(1)')

    await page.goto('/cart')
    await page.locator('#toCheckout').click()
    await expect(page).toHaveURL(/\/checkout/)

    // 购物车来源：结算的是**车里那件**，不是默认商品
    await expect(page.locator('.co-item')).toHaveCount(1)
  })
})
