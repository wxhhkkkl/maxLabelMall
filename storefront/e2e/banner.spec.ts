import { expect, test } from '@playwright/test'

/**
 * 商城页顶部横幅的**护栏**（FR-082 / SC-029 / T239）。
 *
 * 只断言一件事：**没有横幅时页面不留空白**。
 *
 * 为什么只测这一条、而不测"有横幅时能看见"：e2e 跑在测试租户上，那里**没有**
 * 位置为「商城页」的 banner 数据（线上也没有 —— 见 quickstart §4.2，要靠运营去后台建）。
 * 断言"看得见"就得先在测试库里造数据，为一个展示型区块不值得。
 * 而"没数据时不留空白"恰恰是**不需要任何数据**就能验、且最容易悄悄坏掉的那一条 ——
 * 一旦有人把 `v-if="banners.length"` 改成无条件渲染，这个空容器就会把商品列表顶下去。
 *
 * ⚠️ 需要本地 48080 后端（`playwright.config.ts` 的 webServer 会拉起 `pnpm dev`，
 * globalSetup 先探后端端口）。**起不来就记为未执行，不要跳过不记。**
 */
test.describe('商城页横幅', () => {
  test('没有配置横幅时，顶部不出现空白区块，商品列表照常（FR-082）', async ({ page }) => {
    await page.goto('/mall')

    // 页面渲染完成 —— 用商品网格或空态两者之一作为"渲染完了"的信号
    await expect(page.locator('.mall-page-main')).toBeVisible()

    // 关键断言：banner 容器不存在（而不是"存在但看不见"）
    await expect(page.locator('.mall-banner-wrap')).toHaveCount(0)

    // 并且商品区没有被一段空白顶下去：面包屑之后紧接着就是主区
    const crumbBox = await page.locator('.crumbs').boundingBox()
    const mainBox = await page.locator('.mall-page-main').boundingBox()
    expect(crumbBox).not.toBeNull()
    expect(mainBox).not.toBeNull()
    if (crumbBox && mainBox) {
      // 中间不该夹着一大块空白（设计里面包屑到主区本来就是紧邻的）
      expect(mainBox.y - (crumbBox.y + crumbBox.height)).toBeLessThan(80)
    }
  })
})
