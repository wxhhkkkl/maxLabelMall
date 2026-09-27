import { expect, test, type Page } from '@playwright/test'

import { checkMlCheck, createAddress, DEV_SMS_CODE, testMobile } from './helpers'

/**
 * 移动端适配 —— 375px 宽度下**不得横向滚动**（SC-008）。
 *
 * 判定口径：`document.documentElement.scrollWidth` 不超过视口宽度。
 * 这比"肉眼看一眼"可靠 —— 少 1px 的溢出人眼看不出来，却会让页面左右轻微滑动。
 */
const WIDTH = 375
/** 允许 1px 的亚像素舍入误差 */
const SLACK = 1

async function expectNoHScroll(page: Page, label: string) {
  const over = await page.evaluate(() => {
    const el = document.documentElement
    return el.scrollWidth - el.clientWidth
  })
  expect(over, `${label} 在 ${WIDTH}px 下横向溢出 ${over}px`).toBeLessThanOrEqual(SLACK)
}

/**
 * 移动端的登录方式**与桌面不同**，这里必须走真实可用的那条路。
 *
 * ⚠️ 375px 下顶栏那个 `.login` 按钮是 `display:none` —— 这不是我们的 bug：
 * 设计稿自己就这么写的（`design.css` 的 `@media (max-width: 768px) { .login { display: none } }`），
 * 且设计稿的 `.mobile-nav` 里也只有 5 个导航项、没有登录入口。
 *
 * 手机上真实可用的路径是：进受保护页 → 路由守卫挂上 `?login=1` → 弹层自动打开。
 * 所以这里 `goto('/account')` 而不是点顶栏按钮。
 */
async function mobileLogin(page: Page, mobile: string) {
  await page.goto('/account')
  await expect(page.locator('.ml-modal')).toBeVisible()
  await checkMlCheck(page, '.ml-modal')
  await page.locator('#loginMobile').fill(mobile)
  await page.locator('#getCodeBtn').click()
  // 按钮进入倒计时 = 发码请求已回来（与 helpers.smsLogin 同一判定口径）
  await expect(page.locator('#getCodeBtn')).toBeDisabled()
  await page.locator('#loginCode').fill(DEV_SMS_CODE)
  await page.locator('#loginSubmit').click()
  await page.locator('.ml-modal').waitFor({ state: 'detached' })
}

test.use({ viewport: { width: WIDTH, height: 812 }, isMobile: true, hasTouch: true })

test.describe('移动端 375px —— 无横向滚动（SC-008）', () => {
  test('首页与商品详情', async ({ page }) => {
    await page.goto('/')
    await expectNoHScroll(page, '首页')
    // 首页的商品区要等真实商品回来再量
    await expect(page.locator('.p-card').first()).toBeVisible()
    await expectNoHScroll(page, '首页（商品区已渲染）')

    await page.locator('.p-card .p-title').first().click()
    await expect(page.locator('.pd-name')).toBeVisible()
    await expectNoHScroll(page, '商品详情')
  })

  test('下单全路径：登录 → 加购 → 购物车 → 结算 → 订单', async ({ page }) => {
    await page.goto('/')
    await mobileLogin(page, testMobile())
    // 下单前要有一个收货地址（375px 下增删改查也要能用）
    await createAddress(page)

    await page.goto('/mall')
    // 加购要先拉一次详情拿 skuId，等角标确认真的加上了再走
    await page.locator('.p-card .p-btn').first().click()
    await expect(page.locator('.cart-chip')).toContainText('(1)')

    await page.goto('/cart')
    await expect(page.locator('.cart-row').first()).toBeVisible()
    await expectNoHScroll(page, '购物车')

    await page.locator('#toCheckout').click()
    await expect(page).toHaveURL(/\/checkout/)
    await expect(page.locator('.ml-amount-row.is-total')).toBeVisible()
    await expectNoHScroll(page, '结算页')

    await page.locator('#submitOrder').click()
    await expect(page).toHaveURL(/\/order\/\d+/)
    await expectNoHScroll(page, '订单详情')

    await page.goto('/order')
    await expect(page.locator('.order-card').first()).toBeVisible()
    await expectNoHScroll(page, '我的订单')
  })

  test('领券中心与我的券', async ({ page }) => {
    await page.goto('/')
    await mobileLogin(page, testMobile())

    await page.goto('/coupon')
    await expect(page.locator('.cc-card').first()).toBeVisible()
    await expectNoHScroll(page, '领券中心')

    await page.goto('/coupon/mine')
    await expect(page.locator('.cat-pills')).toBeVisible()
    await expectNoHScroll(page, '我的券')
  })

  test('信息页：企业采购 / 模板中心 / 更新日志 / 关于 / 新闻 / 联系 / 招聘', async ({ page }) => {
    for (const [path, label] of [
      ['/enterprise', '企业采购'],
      ['/templates', '模板中心'],
      ['/changelog', '更新日志'],
      ['/about', '公司介绍'],
      ['/news', '新闻动态'],
      ['/contact', '联系我们'],
      ['/jobs', '加入我们'],
    ] as const) {
      await page.goto(path)
      await expectNoHScroll(page, label)
    }
  })

  test('协议页', async ({ page }) => {
    await page.goto('/agreement/user')
    await expectNoHScroll(page, '用户协议')
    await page.goto('/agreement/privacy')
    await expectNoHScroll(page, '隐私政策')
  })
})

/**
 * `/mall` 曾经在 375px 下横向溢出 **44px**（`div.mall-right` 实测 403px，比视口还宽）：
 * `design.css` 的 `.mall-page-main { align-items: flex-start }` 在 ≤1100px 改成
 * `flex-direction: column` 时**没有重置 `align-items`**，于是子项在交叉轴上不拉伸、
 * 按内容最大宽度撑开。
 *
 * 修复后这条用例**转为正常断言**（此前用 `test.fail()` 标为已知缺陷）——
 * 修法就是在那条媒体查询里补 `align-items: stretch`。
 */
test('商城页在 375px 下不横向滚动（曾是已知缺陷）', async ({ page }) => {
  await page.goto('/mall')
  await expect(page.locator('.p-card').first()).toBeVisible()
  await expectNoHScroll(page, '商城')
})
