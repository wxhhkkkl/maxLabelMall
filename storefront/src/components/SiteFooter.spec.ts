import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import siteRouter from '@/router'
import { findIndustry } from '@/data/industrySolutions'
import { findSupportArticle, supportCategories } from '@/data/supportContent'
import { footerNavigation } from '@/data/footerNavigation'
import { supportContact } from '@/data/placeholders'
import SiteFooter from './SiteFooter.vue'

const router = createRouter({ history: createMemoryHistory(), routes: siteRouter.options.routes })

describe('SiteFooter destination maintenance', () => {
  it('every navigation target resolves, including real industry and support content', () => {
    for (const group of footerNavigation) {
      for (const link of group.links) {
        const target = router.resolve(link.to)
        expect(target.matched.length, link.label).toBeGreaterThan(0)
        if (target.name === 'solution-detail') {
          expect(findIndustry(String(target.params.industry)), link.label).toBeDefined()
        }
        if (target.name === 'support-article') {
          expect(findSupportArticle(String(target.params.slug)), link.label).toBeDefined()
        }
        if (target.query.category) {
          expect(supportCategories.some(c => c.id === target.query.category), link.label).toBe(true)
          expect(target.hash).toBe('#knowledge')
        }
      }
    }
  })

  it('industry links reach individual solutions; contact and driver links reach their specific pages', () => {
    const w = mount(SiteFooter, { global: { plugins: [router] } })
    const links = new Map(w.findAll('a').map(a => [a.text(), a.attributes('href')]))
    expect(links.get('仓储物流')).toBe('/solutions/warehouse')
    expect(links.get('生产制造')).toBe('/solutions/manufacturing')
    expect(links.get('医药与医疗器械')).toBe('/solutions/medical')
    expect(links.get('跨境电商')).toBe('/solutions/crossborder')
    expect(links.get('联系我们')).toBe('/support/contact')
    expect(links.get('驱动安装指南')).toBe('/support/driver-install')
    expect(links.get('用户协议')).toBe('/agreement/user')
    expect(links.get('隐私政策')).toBe('/agreement/privacy')
    expect(w.text()).toContain(supportContact.phone)
    expect(w.text()).toContain(supportContact.wechat)
    expect(w.text()).not.toMatch(/软件订阅|客服热线待填写|XXXXXXXX|增值电信业务经营许可证/)
    expect(w.findAll('a').every(a => a.attributes('href') !== '#')).toBe(true)
  })
})
