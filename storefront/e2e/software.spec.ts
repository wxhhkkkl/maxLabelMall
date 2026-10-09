import { test, expect, type Page } from '@playwright/test'

async function capture(page: Page, path: string) {
  const viewport = page.viewportSize()!
  await page.evaluate(async () => {
    await Promise.all([...document.querySelectorAll<HTMLImageElement>('.software-page img')].map(async (img) => {
      img.loading = 'eager'
      await img.decode()
    }))
    await document.fonts.ready
    window.scrollTo(0, 0)
  })
  const height = await page.evaluate(() => document.documentElement.scrollHeight)
  await page.setViewportSize({ width: viewport.width, height })
  await page.screenshot({ path, fullPage: true })
  await page.setViewportSize(viewport)
}

test('software teaser preserves the shell, clear artwork and matching data on desktop and mobile', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/software')
  await expect(page.locator('h1')).toHaveText('让标签设计极致简单')
  await expect(page.locator('.header')).toHaveCount(1)
  await expect(page.locator('.footer')).toHaveCount(1)
  await expect(page.locator('.header .nav a.active')).toHaveText('标签软件')
  await expect(page.locator('.preview-announcement strong')).toHaveText('敬请期待')
  const blur = await page.locator('.hero-preview .preview-workspace').evaluate((el) => getComputedStyle(el).filter)
  expect(blur).toBe('blur(4px)')
  await expect(page.locator('.data-labels .coating-label')).toHaveCount(3)
  for (const width of [1440, 1100, 768, 480, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
    expect(overflow, `overflow at ${width}`).toBe(false)
    if (width === 1440 || width === 390) {
      await capture(page, `../design/software-introduction/implemented-${width === 1440 ? 'desktop' : 'mobile'}.png`)
    }
  }
  const photos = await page.locator('.application-example img').evaluateAll((elements) => elements.map((el) => ({ width: (el as HTMLImageElement).naturalWidth, loaded: (el as HTMLImageElement).complete })))
  expect(photos).toHaveLength(3)
  expect(photos.every((photo) => photo.loaded && photo.width >= 1400)).toBe(true)
  expect(errors).toEqual([])
})
