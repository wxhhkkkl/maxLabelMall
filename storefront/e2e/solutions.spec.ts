import { test, expect, type Page } from '@playwright/test'

async function capture(page: Page, path: string) {
  const viewport = page.viewportSize()!
  await page.evaluate(async () => {
    const images = [...document.querySelectorAll<HTMLImageElement>('.solutions-page img')]
    await Promise.all(images.map(async (image) => {
      image.loading = 'eager'
      await image.decode()
    }))
    for (const image of images) {
      image.scrollIntoView({ behavior: 'instant', block: 'center' })
      await new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
    }
    await document.fonts.ready
    window.scrollTo({ top: 0, behavior: 'instant' })
    await new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
  })
  // A full-height viewport avoids Chrome's offscreen image rasterization during capture.
  const height = await page.evaluate(() => document.documentElement.scrollHeight)
  await page.setViewportSize({ width: viewport.width, height })
  await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
  await page.screenshot({ path, fullPage: true })
  await page.setViewportSize(viewport)
}
const industries = ['warehouse', 'manufacturing', 'apparel', 'medical', 'food', 'retail', 'crossborder', 'bakery', 'catering', 'semiconductor']

test('search, category, reset and card navigation work with the existing site shell', async ({ page }) => {
  await page.goto('/solutions')
  await expect(page.locator('.solution-industry-card')).toHaveCount(10)
  await expect(page.locator('.header')).toHaveCount(1)
  await expect(page.locator('.footer')).toHaveCount(1)
  await page.getByRole('button', { name: '专业标识', exact: true }).click()
  await expect(page.locator('.solution-industry-card')).toHaveCount(2)
  await page.getByRole('searchbox').fill('料盘')
  await expect(page.locator('.solution-industry-card')).toHaveCount(1)
  await page.getByRole('searchbox').fill('不存在的关键词')
  await expect(page.getByText('暂未找到匹配的行业方案')).toBeVisible()
  await page.getByRole('button', { name: '查看全部行业', exact: true }).click()
  await expect(page.locator('.solution-industry-card')).toHaveCount(10)
  await page.locator('.solution-industry-card').first().click()
  await expect(page).toHaveURL(/\/solutions\/warehouse$/)
  await expect(page.locator('.header .nav a.active')).toHaveText('行业方案')
  await page.goBack()
  await expect(page.locator('.solution-industry-card')).toHaveCount(10)
  await page.getByRole('button', { name: '找到我的行业' }).click()
  await expect(page.locator('#industry-heading')).toBeInViewport()
})

test('all industry details and the singular alias load without API data', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  for (const industry of industries) {
    await page.goto('/solutions/' + industry)
    await expect(page.locator('h1')).toBeVisible()
    await expect(page.locator('.solution-label-example')).toHaveCount(3)
    await expect(page.locator('.solution-business-flow')).toBeVisible()
    await expect(page.locator('.header')).toHaveCount(1)
    await expect(page.locator('.footer')).toHaveCount(1)
    await expect(page.locator('.solution-banner-photo')).toBeVisible()
    await expect(page.locator('.solution-label-photo img')).toHaveCount(3)
    for (const illustration of await page.locator('.solution-label-photo img').all()) {
      await illustration.scrollIntoViewIfNeeded()
      await expect.poll(() => illustration.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true)
    }
    if (industry === 'bakery' || industry === 'apparel') {
      await expect(page.locator('.flow-source')).toContainText('共享产品档案')
      await expect(page.locator('.flow-branches article')).toHaveCount(3)
    } else {
      await expect(page.locator('.solution-business-flow li')).toHaveCount(4)
    }
  }
  await page.goto('/solution')
  await expect(page.locator('.solution-industry-card')).toHaveCount(10)
  await page.goto('/solutions/does-not-exist')
  await expect(page.getByText('未找到这个行业方案')).toBeVisible()
  await page.getByRole('link', { name: '返回行业方案' }).click()
  await expect(page).toHaveURL(/\/solutions$/)
  expect(errors).toEqual([])
})

test('responsive layouts have no overflow and keep readable spacing', async ({ page }) => {
  for (const width of [1440, 1101, 1100, 768, 600, 480, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    for (const path of ['/solutions', '/solutions/medical', '/solutions/bakery', '/solutions/catering', '/solutions/food', '/solutions/retail']) {
      await page.goto(path)
      await expect(page.locator('h1')).toBeVisible()
      const layout = await page.locator('.solutions-page').evaluate((root) => {
        const rect = root.getBoundingClientRect()
        const overflow = [...root.querySelectorAll('*')].filter((element) => {
          if (element.classList.contains('solution-sr-only')) return false
          const box = element.getBoundingClientRect()
          return box.width > 0 && (box.right > window.innerWidth + 1 || box.left < -1)
        }).map((element) => element.className)
        return { width: rect.width, viewport: window.innerWidth, overflow }
      })
      expect(layout.overflow, path + ' at ' + width).toEqual([])
      expect(layout.width).toBeLessThanOrEqual(layout.viewport)
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/solutions')
  expect(await page.locator('.solution-industry-grid').evaluate((grid) => getComputedStyle(grid).gridTemplateColumns.split(' ').length)).toBe(3)
  await capture(page, '../design/industry-solutions/implemented-overview-desktop.png')
  await page.goto('/solutions/warehouse')
  await capture(page, '../design/industry-solutions/implemented-warehouse-desktop.png')
  await page.goto('/solutions/manufacturing')
  await capture(page, '../design/industry-solutions/implemented-manufacturing-desktop.png')
  await page.goto('/solutions/bakery')
  await page.locator('.solution-detail-samples').scrollIntoViewIfNeeded()
  await expect.poll(() => page.locator('.solution-label-photo img').evaluateAll((images) => images.every((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth >= 1200))).toBe(true)
  await capture(page, '../design/industry-solutions/implemented-bakery-desktop.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/solutions')
  await capture(page, '../design/industry-solutions/implemented-overview-mobile.png')
  await page.goto('/solutions/medical')
  await capture(page, '../design/industry-solutions/implemented-medical-mobile.png')
  await page.goto('/solutions/bakery')
  await capture(page, '../design/industry-solutions/implemented-bakery-mobile.png')
})
