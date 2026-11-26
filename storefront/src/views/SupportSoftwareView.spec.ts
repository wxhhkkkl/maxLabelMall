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

  /**
   * 发布日期（2026-11-26）
   *
   * 后端给的是 **epoch 毫秒数**（`TimestampLocalDateTimeSerializer`），不是字符串。
   * 旧实现按字符串 `slice(0, 10)`，数字上直接抛 TypeError；而 1970 的历史数据存的是 0，
   * 恰好被 `v-if` 当成假值吞掉，才没炸出来。所以：数字要格式化，0 不渲染。
   */
  it('发布日期按 epoch 毫秒渲染，为 0 时不显示', async () => {
    listAll.mockResolvedValue([
      {
        id: 1,
        categoryId: 1,
        title: 'MaxLabel',
        introduction: '',
        type: 1,
        versions: [
          {
            version: '1.2.9',
            platform: 'windows',
            arch: 'x64',
            fileName: 'a.exe',
            fileSize: 1,
            releaseTime: new Date(2026, 10, 26, 13, 29, 31).getTime(),
            downloadUrl: 'https://img.yuwangchenfa.com/software/a.exe'
          },
          {
            version: '1.2.9',
            platform: 'windows',
            arch: 'x86',
            fileName: 'b.exe',
            fileSize: 1,
            releaseTime: 0,
            downloadUrl: 'https://img.yuwangchenfa.com/software/b.exe'
          }
        ]
      }
    ])
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    const details = wrapper.findAll('.driver-version-detail')
    expect(details).toHaveLength(2)
    expect(details[0]!.text()).toContain('2026-11-26')
    expect(details[1]!.text()).not.toMatch(/\d{4}-\d{2}-\d{2}/)
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


describe('下载加载恢复', () => {
  it('请求失败显示重试入口，重试后恢复列表状态', async () => {
    listAll.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce([])
    const w = mount(SupportSoftwareView)
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('加载失败')
    await w.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(listAll).toHaveBeenCalledTimes(2)
    expect(w.find('[role="alert"]').exists()).toBe(false)
    expect(w.text()).toContain('暂无可用下载')
    w.unmount()
  })
})
