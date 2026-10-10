import { describe, expect, it } from 'vitest'
import { findSupportArticle, searchSupportArticles, supportArticles, supportFaqs, supportIssueTabs } from './supportContent'

describe('帮助内容检索与导航', () => {
  it('提供六篇基础教程、八篇排查与四篇选型内容，所有入口有对应文章', () => {
    expect(supportArticles.filter((item) => item.category === 'guides')).toHaveLength(6)
    expect(supportArticles.filter((item) => item.category === 'troubleshooting')).toHaveLength(8)
    expect(supportArticles.filter((item) => item.category === 'materials')).toHaveLength(4)
    expect(new Set(supportArticles.map((item) => item.slug)).size).toBe(18)
    for (const slug of [...supportIssueTabs.flatMap((tab) => tab.slugs), ...supportFaqs.flatMap((faq) => faq.slug ? [faq.slug] : [])]) expect(findSupportArticle(slug)).toBeDefined()
  })
  it('支持分类、多词、正文与大小写检索，并正确处理无结果', () => {
    expect(searchSupportArticles('  usb  ', 'troubleshooting').some((item) => item.slug === 'device-not-found')).toBe(true)
    expect(searchSupportArticles('纸张 尺寸', 'guides').some((item) => item.slug === 'paper-size')).toBe(true)
    expect(searchSupportArticles('树脂', 'materials').map((item) => item.slug)).toContain('ribbon-selection')
    expect(searchSupportArticles('完全不存在的内容词')).toEqual([])
  })
})
