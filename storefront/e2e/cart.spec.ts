import { expect, test } from '@playwright/test'

import { checkMlCheck, smsLogin, testMobile } from './helpers'

/**
 * US3 购物车 —— 端到端（按故事测试先行交付）。
 *
 * 覆盖：FR-019（角标来自后端真实件数）、FR-015（未登录加购在登录后自动补上）。
 */
test.describe('US3 购物车', () => {
  test('登录后加购，顶栏角标 +1（FR-019）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)

    await page.goto('/mall')
    const firstAdd = page.locator('.p-card .p-btn').first()
    await firstAdd.click()

    // 角标来自后端 get-count，不是本地累加
    await expect(page.locator('.cart-chip')).toContainText('(1)')
  })

  test('未登录加购 → 引导登录 → **登录后自动完成这次加购**（FR-015）', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/mall')

    await page.locator('.p-card .p-btn').first().click()
    // 未登录：弹层被打开，且提示登录后会加入购物车
    await expect(page.locator('.ml-modal')).toBeVisible()

    await checkMlCheck(page, '.ml-modal')
    await page.locator('#loginMobile').fill(mobile)
    await page.locator('#getCodeBtn').click()
    await page.locator('#loginCode').fill('9999')
    await page.locator('#loginSubmit').click()
    await page.locator('.ml-modal').waitFor({ state: 'detached' })

    // **关键**：不需要用户重新点一次加购
    await expect(page.locator('.cart-chip')).toContainText('(1)')
  })

  test('购物车条目可改量与删除，合计随之更新', async ({ page }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)

    await page.goto('/mall')
    await page.locator('.p-card .p-btn').first().click()
    // ⚠️ **必须等加购真的完成再离开这一页**。`onAdd` 不是立刻发加购请求：
    // 商城列表接口不返回 `skus`，所以它要先 `getProductDetail` 拿到 skuId，
    // 才发 `cart/add`（见 ProductCard.vue 的注释）。`click()` 只保证事件已派发，
    // 紧接着 `page.goto('/cart')` 会把在途请求连同这次加购一起掐掉 ——
    // 页面自然显示「购物车还是空的」，而这不是产物缺陷，是测试没等。
    // 可等的信号：顶栏角标（它来自后端 get-count）。
    await expect(page.locator('.cart-chip')).toContainText('(1)')

    await page.goto('/cart')
    await expect(page.locator('.cart-row')).toHaveCount(1)

    await page.locator('.cart-qty-plus').first().click()
    await expect(page.locator('.cart-qty-num').first()).toHaveText('2')

    await page.locator('.cart-remove').first().click()
    await expect(page.locator('.empty-state')).toBeVisible()
  })

  test('退出再登录，购物车内容不变（FR-022）', async ({ page }) => {
    // 本用例要用**同一手机号**登录两次，第二次会撞上后端 1 分钟的发码限流
    // （按手机号、不筛场景 —— 见 helpers.ts 顶部第 4 条），故放宽超时。
    test.setTimeout(180_000)

    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    await page.goto('/mall')
    await page.locator('.p-card .p-btn').first().click()
    await expect(page.locator('.cart-chip')).toContainText('(1)')

    await page.goto('/account')
    await page.getByRole('button', { name: '退出登录' }).click()

    await smsLogin(page, mobile)
    // 购物车绑在账号上，重新登录后应看到同一份
    await expect(page.locator('.cart-chip')).toContainText('(1)')
  })
})
