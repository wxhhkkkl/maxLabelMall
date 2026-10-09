import { test, expect } from '@playwright/test'

test('new home banner and industry links work on desktop and mobile', async ({ page }) => {
  await page.route('**/product/spu/page**', (route) => route.fulfill({ json: { code: 0, data: { list: [], total: 0 } } }))
  await page.route('**/product/category/list**', (route) => route.fulfill({ json: { code: 0, data: [] } }))
  await page.goto('/')
  await expect(page.locator('h1')).toHaveCount(1)
  await expect(page.locator('h1')).toHaveText('设计、打印与耗材一站解决')
  await expect(page.locator('.header')).toHaveCount(1)
  await expect(page.locator('.footer')).toHaveCount(1)
  await expect(page.locator('.home-banner-software-note')).toContainText('软件开发中')
  await expect(page.locator('.home-banner-image')).toHaveAttribute('alt', /MaxLabel 标签设计软件/)
  for (const width of [1440, 1100, 768, 480, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    for (const selector of ['.home-banner', '.home-industry']) {
      const section = page.locator(selector)
      await section.scrollIntoViewIfNeeded()
      await section.locator('img').evaluateAll(async (images) => {
        await Promise.all(images.map((img) => (img as HTMLImageElement).decode()))
      })
      const fits = await section.evaluate((el) => [...el.querySelectorAll('h1, h2, a, img')].every((child) => {
        const rect = child.getBoundingClientRect()
        return rect.left >= -1 && rect.right <= window.innerWidth + 1
      }))
      expect(fits, `${selector} fits ${width}`).toBe(true)
      if (width === 1440 || width === 390) {
        await section.screenshot({ path: `../design/home-support-redesign/implemented-${selector.slice(1)}-${width}.png`, style: '.header { visibility: hidden !important; }' })
      }
    }
  }
  await page.locator('.home-banner-primary').click()
  await expect(page).toHaveURL(/\/mall$/)
  await page.goto('/')
  await page.locator('.home-industry-card').first().click()
  await expect(page).toHaveURL(/\/solutions\/manufacturing$/)
  await expect(page.locator('.solution-banner-photo')).toBeVisible()
})
