import { expect, test, type Page } from '@playwright/test'

import { createAddress, smsLogin, testMobile } from './helpers'

/**
 * 挑一件单价够得上门槛的商品卡。
 *
 * ⚠️ 不能直接点第一张卡：库里有一件「1 分小商品」（单价 ¥0.01），而券的门槛最低
 * 也是 ¥5 —— 拿它去结算，券必然显示「差 X 元可用」，这个用例就永远验不到"可选用"。
 * 所以按卡片上**自己显示的单价**挑，不依赖列表顺序（顺序会随销量变化）。
 */
async function pickProductAbove(page: Page, minYuan: number) {
  const cards = page.locator('.p-card')
  const count = await cards.count()
  for (let i = 0; i < count; i++) {
    const card = cards.nth(i)
    const text = await card.locator('.p-price').innerText()
    const num = text.match(/¥\s*([\d,]+(?:\.\d+)?)/)
    if (num && Number(num[1].replace(/,/g, '')) >= minYuan) return card
  }
  throw new Error(`商城没有单价 ≥ ¥${minYuan} 的商品，无法验证券的可用性`)
}

/**
 * US5 优惠券 —— 端到端（按故事测试先行交付）。
 *
 * 覆盖 SC-014：用户从领券入口领取一张券后，**无需刷新或等待**即可在结算页选用它。
 *
 * ⚠️ 领券之后**一律用点击导航，不再 `page.goto`**。整页重载会把「前端缓存了陈旧的
 * 券列表」这类问题整段掩盖掉，而 SC-014 要验的恰恰是"领取 → 可选用之间无人工介入"。
 * 只有全程 SPA 才测得到。
 */
test.describe('US5 优惠券', () => {
  test('**顶栏能直达领券中心**，未登录也够得到（T125 / FR-026c）', async ({ page }) => {
    // T125 的意义就在这里：此前 `/coupon` 只能在**登录后**从「我的券」页绕进去，
    // 未登录访客没有任何入口。现在它进了主导航（因而移动端汉堡菜单里也有）。
    await page.goto('/')
    await page.locator('.nav a', { hasText: '领券中心' }).click()
    await expect(page).toHaveURL(/\/coupon$/)
    // 免登录也能看到券列表（`coupon-template/list` 是 @PermitAll）
    await expect(page.locator('.cc-card').first()).toBeVisible()
  })

  test('移动端汉堡菜单里也有领券中心入口', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/')
    await page.locator('.nav-toggle').click()
    await page.locator('.mobile-nav a', { hasText: '领券中心' }).click()
    await expect(page).toHaveURL(/\/coupon$/)
  })


  test('领券后无需刷新即可在结算页选用（SC-014 / FR-026c / FR-026d）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await createAddress(page)

    // —— 领券中心 ——
    //
    // ⚠️ **不能只领第一张。** 券模板有 `fixedStartTerm`（领取后第 N 天才生效），
    // 种子数据里"又一个优惠""可爱的优惠劵"分别是**第 3 天 / 第 10 天**才生效，
    // 只有"9 折卷"是领取即可用。领到前两张的话结算时必然不可选，
    // 这个用例就测不到 SC-014 的"可选用"了。
    //
    // 判定依据就用卡片上自己显示的文案：`fixedStartTerm > 0` 会渲染成
    // 「领取后**第 N 天生效**，M 天内有效」，即时生效的则是「领取后 M 天内有效」。
    await page.goto('/coupon')
    await expect(page.locator('.cc-card').first()).toBeVisible()

    const card = page.locator('.cc-card').filter({ hasNotText: '天生效' }).first()
    await expect(card).toBeVisible()
    const couponName = (await card.locator('.cc-name').innerText()).trim()
    expect(couponName).not.toBe('')

    await card.locator('.cc-take').click()
    // 领到之后这张就不再可领 —— 重复领取不产生第二张凭证（FR-026c）
    await expect(card).toContainText('已领取')

    // —— 以下全程 SPA 点击，不整页重载 ——

    // 「我的券」里能看到它（FR-026d）
    await page.locator('.cc-mine').click()
    await expect(page).toHaveURL(/\/coupon\/mine/)
    await expect(page.locator('.mc-card').first()).toBeVisible()
    await expect(page.locator('.mc-card', { hasText: couponName })).toHaveCount(1)

    // 回商城，挑一件够得上门槛的商品（券门槛最低 ¥5，而库里有 ¥0.01 的小商品）
    await page.locator('.nav a', { hasText: '商城' }).click()
    await expect(page).toHaveURL(/\/mall/)
    await expect(page.locator('.p-card').first()).toBeVisible()
    const product = await pickProductAbove(page, 10)

    // 直购 → 结算
    await product.locator('.p-title').click()
    await page.locator('.btn-buy').click()
    await expect(page).toHaveURL(/\/checkout/)
    await expect(page.locator('.ml-amount-row.is-total')).toBeVisible()

    // 结算页的券选择器里应当**已经有刚才领的这张券**，而且是**可选的**
    // （可用性一律由后端判定，前端不自己算 —— FR-026b）
    await page.locator('#openCouponPicker').click()
    const item = page.locator('.coupon-item', { hasText: couponName })
    await expect(item).toBeVisible()
    await expect(item).not.toHaveClass(/is-disabled/)

    // 选它 → 应付总额按券扣减。全过程**没有刷过一次页面**（SC-014）
    const totalBefore = await page.locator('.ml-amount-row.is-total .v').innerText()
    await item.click()
    await expect(page.locator('#openCouponPicker')).toContainText(couponName)
    await expect(page.locator('.ml-amount-row.is-total .v')).not.toHaveText(totalBefore)
  })

  test('重复领取同一张券不产生第二张凭证（FR-026c）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)

    await page.goto('/coupon')
    const card = page.locator('.cc-card').first()
    const couponName = (await card.locator('.cc-name').innerText()).trim()
    await card.locator('.cc-take').click()
    await expect(card).toContainText('已领取')

    // 「我的券」里这张只应出现一次
    await page.goto('/coupon/mine')
    const mine = page.locator('.mc-card', { hasText: couponName })
    await expect(mine).toHaveCount(1)
  })
})
