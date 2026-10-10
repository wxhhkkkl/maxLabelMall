import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '@/App.vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

/**
 * 路由表验证 —— 真实挂载并逐条导航，确认 23 条路由都能打开且渲染出内容。
 *
 * 这是 Checkpoint「23 条路由都能打开」的可执行版本：`curl` 对 SPA 无效
 * （所有路径都返回同一份 index.html，渲染发生在客户端）。
 *
 * 路由表用真实定义，只把 history 换成 memory 以便在 jsdom 里导航。
 */

// 与 src/router/index.ts 保持同构：这里直接复用其 routes 定义
import realRouter, { authGuard } from './index'

// 顶栏（SiteHeader）用了 Pinia store（登录态），挂载 App 时必须装上
beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
})

function makeRouter() {
  const r = createRouter({
    history: createMemoryHistory(),
    routes: (realRouter as unknown as { options: { routes: never[] } }).options.routes,
  })
  // 复用真实守卫，而不是在测试里重写一份 —— 否则测的就不是生产逻辑了
  r.beforeEach(authGuard)
  return r
}

/** 设计稿 6 条 + 功能页 14 条 + 信息页 6 条 = 26（另加行业详情 1 条 = 27） */
const ALL_PATHS = [
  '/',
  '/mall',
  '/product/1',
  '/product/1/comments',
  '/software',
  '/solutions',
  '/solutions/warehouse',
  '/support',
  '/cart',
  '/checkout',
  '/order',
  '/order/1',
  '/account',
  '/account/address',
  '/account/after-sale',
  '/account/points',
  '/coupon',
  '/coupon/mine',
  '/agreement/user',
  '/agreement/privacy',
  '/enterprise',
  '/templates',
  '/changelog',
  '/about',
  '/news',
  '/contact',
  '/jobs',
]

describe('路由表', () => {
  it('声明了 27 条路径（设计稿 6 + 功能页 14 + 信息页 6 + 行业详情 1）', () => {
    const paths: string[] = []
    const walk = (routes: Array<{ path: string; children?: unknown[] }>, prefix = '') => {
      for (const r of routes) {
        const p = r.path.startsWith('/') ? r.path : `${prefix}/${r.path}`.replace(/\/+/g, '/')
        if (r.children) walk(r.children as Array<{ path: string; children?: unknown[] }>, p)
        else paths.push(p)
      }
    }
    walk((realRouter as unknown as { options: { routes: never[] } }).options.routes)
    // 叶子路由。注意首页那条 path 为 ''，解析后就是 '/'，不要额外过滤掉它。
    // 2026-10-09：个人中心整理新增 /account/after-sale 与 /account/points（23 → 26，
    // 其中 23 之后先由行业方案加了 1 条）。
    // 2026-10-10：商品详情改版追加 /product/:id/comments（26 → 27）。
    expect(paths).toHaveLength(27)
    expect(paths).toContain('/')
  })

  it.each(ALL_PATHS)('%s 能打开并渲染出内容', async (path) => {
    const r = makeRouter()
    await r.push(path)
    await r.isReady()

    const w = mount(App, { global: { plugins: [r, createPinia()] } })
    await r.isReady()

    // 外壳必须存在（FR-042 的结构保证：所有页面经 DefaultLayout 挂载）
    expect(w.findComponent(DefaultLayout).exists()).toBe(true)
    // 顶栏与页脚只在 DefaultLayout 里渲染一次，页面自己不得再渲染
    expect(w.findAll('.header')).toHaveLength(1)
    expect(w.findAll('.footer')).toHaveLength(1)
    // 页面主体渲染出了东西：要么有文本，要么是加载骨架。
    // （详情页等数据页挂载时会先进入加载态，骨架屏本身没有文字，
    //   所以不能简单断言「有文本」。）
    const main = w.find('main')
    expect(main.exists()).toBe(true)
    const hasText = main.text().length > 0
    const hasPlaceholder = main.find('.ml-skeleton, .ph, .empty-state').exists()
    expect(hasText || hasPlaceholder).toBe(true)
  })

  it('未匹配的路径不会渲染外壳内的页面内容（留待 404 处理）', async () => {
    const r = makeRouter()
    // 不存在的路径：vue-router 会警告但不抛错
    await r.push('/definitely-not-a-page')
    expect(r.currentRoute.value.matched).toHaveLength(0)
  })
})

describe('静态链接守卫 —— 站内链接不得指向不存在的路由（FR-043）', () => {
  it('所有视图/组件里的 to="/..." 都指向已声明的路由', async () => {
    const { readFileSync, readdirSync, statSync } = await import('node:fs')
    const { join } = await import('node:path')

    // vitest 的 cwd 即工程根；jsdom 下 import.meta.url 不是 file:// 协议，不能用它取路径
    const root = join(process.cwd(), 'src')
    const files: string[] = []
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name)
        if (statSync(full).isDirectory()) walk(full)
        else if (full.endsWith('.vue')) files.push(full)
      }
    }
    walk(join(root, 'views'))
    walk(join(root, 'components'))

    // 已声明的路由路径集合（把 :id 这类参数段替换成通配）
    const declared = new Set<string>()
    const c2 = (realRouter as unknown as { options: { routes: Array<{ path: string; children?: unknown[] }> } }).options.routes
    const collect = (routes: typeof c2, prefix = '') => {
      for (const r of routes) {
        const p = r.path.startsWith('/') ? r.path : `${prefix}/${r.path}`.replace(/\/+/g, '/')
        if (r.children) collect(r.children as typeof c2, p)
        else declared.add(p.replace(/:[^/]+/g, '[^/]+'))
      }
    }
    collect(c2)

    /** 归一化：去掉尾部斜杠，但根路径保持 '/' */
    const norm = (p: string) => {
      const t = p.replace(/\/+$/, '')
      return t === '' ? '/' : t
    }

    const bad: string[] = []
    for (const f of files) {
      const src = readFileSync(f, 'utf8')
      for (const m of src.matchAll(/to="(\/[^"?#]*)(?:[?#][^"]*)?"/g)) {
        const target = norm(m[1] ?? '')
        const ok = [...declared].some((d) => new RegExp('^' + norm(d) + '$').test(target))
        if (!ok) bad.push(`${f.split('/').slice(-2).join('/')} → ${target}`)
      }
    }
    expect(bad).toEqual([])
  })
})

describe('需登录守卫（T074 / FR-015）', () => {
  const PROTECTED = ['/cart', '/checkout', '/order', '/order/1', '/account', '/account/address', '/coupon/mine']

  it.each(PROTECTED)('%s 未登录时挂上 ?login=1 并记住 redirect', async (path) => {
    window.localStorage.clear()
    const r = makeRouter()
    await r.push(path)
    await r.isReady()
    expect(r.currentRoute.value.query.login).toBe('1')
    expect(r.currentRoute.value.query.redirect).toBe(path)
    // 仍是原路径（没有跳到某个 /login）
    expect(r.currentRoute.value.path).toBe(path)
  })

  it('**已带 ?login=1 的受保护页面不再重定向** —— 否则会无限循环（应用挂死）', async () => {
    window.localStorage.clear()
    const r = makeRouter()
    await r.push('/cart?login=1&redirect=/cart')
    await r.isReady()
    // 导航成功且停在原地，没有再次被重定向
    expect(r.currentRoute.value.path).toBe('/cart')
    expect(r.currentRoute.value.query.login).toBe('1')
  })

  it('已登录时直接放行', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const r = makeRouter()
    await r.push('/cart')
    await r.isReady()
    expect(r.currentRoute.value.query.login).toBeUndefined()
    window.localStorage.clear()
  })

  it('公开页面不需要登录', async () => {
    window.localStorage.clear()
    for (const path of ['/', '/mall', '/software', '/templates', '/agreement/user']) {
      const r = makeRouter()
      await r.push(path)
      await r.isReady()
      expect(r.currentRoute.value.query.login).toBeUndefined()
    }
  })

  it('**不存在 /login 路由** —— 登录只由弹层承载', () => {
    const paths: string[] = []
    const walk = (routes: Array<{ path: string; children?: unknown[] }>, prefix = '') => {
      for (const rt of routes) {
        const p = rt.path.startsWith('/') ? rt.path : `${prefix}/${rt.path}`.replace(/\/+/g, '/')
        if (rt.children) walk(rt.children as Array<{ path: string; children?: unknown[] }>, p)
        else paths.push(p)
      }
    }
    walk((realRouter as unknown as { options: { routes: never[] } }).options.routes)
    expect(paths).not.toContain('/login')
  })
})
