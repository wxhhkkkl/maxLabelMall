import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { industrySolutions } from '@/data/industrySolutions'
import SolutionsView from './SolutionsView.vue'
import IndustrySolutionView from './IndustrySolutionView.vue'

async function render(path = '/solutions') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/solutions', alias: '/solution', component: SolutionsView },
      { path: '/solutions/:industry', alias: '/solution/:industry', component: IndustrySolutionView },
      { path: '/contact', component: { template: '<div>联系方式</div>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('行业方案总览', () => {
  it('十大行业都有可打开的独立详情入口', async () => {
    const { wrapper, router } = await render()
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(10)
    const links = wrapper.findAll('.solution-industry-card').map((card) => card.attributes('href'))
    for (const industry of industrySolutions) {
      const path = `/solutions/${industry.id}`
      expect(links).toContain(path)
      await router.push(path)
      expect(wrapper.find('h1').text()).toBe(industry.headline)
      expect(wrapper.findAll('.solution-label-example')).toHaveLength(3)
    }
    wrapper.unmount()
  })

  it('分类与标签关键词联合筛选，并可从空状态恢复', async () => {
    const { wrapper } = await render()
    const group = wrapper.findAll('.solution-filter-groups button').find((button) => button.text() === '专业标识')!
    await group.trigger('click')
    expect(group.attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(2)
    await wrapper.find('input').setValue('料盘')
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(1)
    expect(wrapper.find('.solution-industry-card').text()).toContain('电子半导体')
    await wrapper.find('input').setValue('不存在的关键词')
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(0)
    await wrapper.find('.es-reset').trigger('click')
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(10)
    expect(wrapper.find('input').element.value).toBe('')
    wrapper.unmount()
  })

  it('支持单数地址，并为未知行业提供返回入口', async () => {
    const { wrapper, router } = await render('/solution')
    expect(wrapper.findAll('.solution-industry-card')).toHaveLength(10)
    await router.push('/solution/bakery')
    expect(wrapper.find('h1').text()).toContain('今日出炉')
    await router.push('/solutions/unknown')
    expect(wrapper.text()).toContain('未找到这个行业方案')
    expect(wrapper.find('a[href="/solutions"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('详情间导航会更新流程与标签，不残留上一行业', async () => {
    const { wrapper, router } = await render('/solutions/warehouse')
    expect(wrapper.text()).toContain('库位上架')
    await router.push('/solutions/semiconductor')
    expect(wrapper.text()).toContain('料盘赋码')
    expect(wrapper.text()).not.toContain('库位上架')
    wrapper.unmount()
  })
})
