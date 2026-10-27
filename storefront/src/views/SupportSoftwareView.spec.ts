import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/software', () => ({ listAllSoftware: vi.fn() }))

import { listAllSoftware } from '@/api/software'
import SupportSoftwareView from './SupportSoftwareView.vue'

const listAll = vi.mocked(listAllSoftware)

beforeEach(() => listAll.mockReset())

describe('服务支持 · 软件与驱动（013 / US49）', () => {
  it('一屏平铺所有软件及其版本，不再需要二次点击', async () => {
    listAll.mockResolvedValue([
      {
        id: 1,
        categoryId: 1,
        title: 'MaxLabel',
        introduction: '标签打印软件',
        type: 1,
        versions: [
          {
            version: '1.0.22',
            platform: 'windows',
            arch: 'x64',
            fileName: 'a.exe',
            fileSize: 144699554,
            downloadUrl: 'https://img.yuwangchenfa.com/software/windows-x64/1.0.22/a.exe'
          }
        ]
      },
      {
        id: 2,
        categoryId: 2,
        title: 'zenpert4T530',
        introduction: '打印机驱动',
        type: 2,
        versions: [
          {
            version: '7.4.2',
            platform: 'windows',
            arch: 'x64',
            fileName: 'b.zip',
            fileSize: 1048576,
            downloadUrl: 'https://img.yuwangchenfa.com/software/windows-x64/7.4.2/b.zip'
          }
        ]
      }
    ])

    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(listAll).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('[data-testid="software-block"]')).toHaveLength(2)

    const links = wrapper.findAll('a.download')
    expect(links).toHaveLength(2)
    expect(links[0]!.attributes('href')).toContain('1.0.22')
    expect(links[1]!.attributes('href')).toContain('7.4.2')
    expect(wrapper.text()).toContain('138.0 MB')
  })

  it('没有可下载项时给出空态', async () => {
    listAll.mockResolvedValue([])
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可用下载')
    expect(wrapper.findAll('[data-testid="software-block"]')).toHaveLength(0)
  })

  it('加载失败时给出提示而不是白屏', async () => {
    listAll.mockResolvedValue(null as never)
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可用下载')
  })

  it('页面不提供搜索框（Q88）', async () => {
    listAll.mockResolvedValue([])
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(wrapper.find('input[type="search"]').exists()).toBe(false)
  })
})
