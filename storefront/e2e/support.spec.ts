import { test, expect, type Page } from '@playwright/test'
import { supportArticles } from '../src/data/supportContent'

async function capture(page: Page, filename: string) {
  const viewport = page.viewportSize()!
  await page.evaluate(async () => {
    await Promise.all([...document.querySelectorAll<HTMLImageElement>('.support-page img')].map(async (img) => { img.loading = 'eager'; await img.decode() }))
    await document.fonts.ready
    window.scrollTo({ top: 0, behavior: 'instant' })
  })
  const height = await page.evaluate(() => document.documentElement.scrollHeight)
  await page.setViewportSize({ width: viewport.width, height })
  await page.screenshot({ path: `../design/support-center/${filename}.png`, fullPage: true })
  await page.setViewportSize(viewport)
}

test('search, categories, issue tabs and guide navigation work', async ({ page }) => {
  await page.goto('/support')
  await expect(page.locator('.header')).toHaveCount(1)
  await expect(page.locator('.footer')).toHaveCount(1)
  await page.getByRole('tab', { name: '打印效果' }).click()
  await expect(page.locator('#issue-panel a')).toHaveCount(4)
  await page.getByRole('tab', { name: '打印效果' }).press('ArrowRight')
  await expect(page.getByRole('tab', { name: '走纸与耗材' })).toBeFocused()
  await expect(page.getByRole('tab', { name: '走纸与耗材' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: '走纸与耗材' }).press('Home')
  await expect(page.getByRole('tab', { name: '设备连接' })).toBeFocused()
  await page.getByRole('searchbox').fill('打印偏移')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/q=/)
  await expect(page.locator('.support-article-list')).toContainText('标签打印偏移')
  await page.getByRole('searchbox').fill('完全不存在的内容词')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('暂未找到匹配的内容')).toBeVisible()
  await page.getByRole('button', { name: '查看全部内容', exact: true }).click()
  await page.locator('.support-filter').getByRole('button', { name: '耗材选型' }).click()
  await expect(page.locator('.support-article-list a')).toHaveCount(4)
  await page.locator('.support-article-list a').first().click()
  await expect(page.locator('.support-reading-header h1')).toContainText('热敏纸与热转印')
  await expect(page.locator('.header .nav a.active')).toHaveText('服务支持')
  await page.goBack()
  await expect(page.locator('.support-article-list a')).toHaveCount(4)
})

test('all eighteen articles and unknown content render correctly', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  for (const article of supportArticles) {
    await page.goto('/support/' + article.slug)
    await expect(page.locator('h1')).toHaveText(article.title)
    await expect(page.locator('.support-article-section')).toHaveCount(3)
    await expect(page.locator('.support-article-verification li')).toHaveCount(2)
    await expect(page.locator('.header')).toHaveCount(1)
    await expect(page.getByText('官方参考资料')).toHaveCount(0)
    await expect(page.locator('.support-article-body a[href^="http"]')).toHaveCount(0)
  }
  await page.goto('/support/not-an-article')
  await expect(page.getByText('未找到这篇帮助内容')).toBeVisible()
  expect(errors).toEqual([])
})

test('desktop and mobile layouts load clear images and show direct contact information', async ({ page, context }) => {
  for (const width of [1440, 1100, 768, 480, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/support')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `home fits ${width}`).toBe(true)
    if (width === 1440 || width === 390) await capture(page, `support-${width}`)
    await page.goto('/support/load-labels')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `article fits ${width}`).toBe(true)
    if (width === 1440 || width === 390) await capture(page, `article-${width}`)
    await page.goto('/support/contact')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `contact fits ${width}`).toBe(true)
    if (width === 1440) await capture(page, 'contact-1440')
  }
  await expect(page.locator('.support-contact-cards')).toContainText('张经理')
  await expect(page.locator('.support-contact-cards')).toContainText('q345845052')
  await expect(page.locator('.support-contact-phone')).toHaveText('010-88613986')
  await expect(page.locator('.support-contact-cards a[href^="tel:"]')).toHaveCount(0)
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.getByRole('button', { name: '复制微信号' }).click()
  await expect(page.getByRole('status')).toContainText('微信号已复制')
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('q345845052')
  await expect(page.locator('.support-page input, .support-page textarea')).toHaveCount(0)
  await capture(page, 'contact-320')
  await page.getByRole('link', { name: '返回帮助中心' }).click()
  await expect(page.locator('#contact-support .support-contact-compact')).toBeVisible()
  const positions = await page.evaluate(() => ({
    card: document.querySelector('#contact-support .support-contact-cards')!.getBoundingClientRect().bottom,
    footer: document.querySelector('.footer')!.getBoundingClientRect().top,
  }))
  expect(positions.footer).toBeGreaterThan(positions.card)
  await page.setViewportSize({ width: 620, height: 900 })
  await page.locator('#contact-support').scrollIntoViewIfNeeded()
  await page.screenshot({ path: '../design/support-center/contact-inline-fixed.png' })
})
