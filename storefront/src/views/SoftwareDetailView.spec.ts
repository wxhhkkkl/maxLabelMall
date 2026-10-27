import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { id: '10' } }) }))
vi.mock('@/api/software', () => ({ getSoftwareDetail: vi.fn() }))

import { getSoftwareDetail } from '@/api/software'
import SoftwareDetailView from './SoftwareDetailView.vue'

const getDetail = vi.mocked(getSoftwareDetail)

beforeEach(() => getDetail.mockReset())

describe('服务支持 · 软件详情（013 / US49）', () => {
  it('只展示后端返回的当前发布，并给出 OSS 直链', async () => {
    getDetail.mockResolvedValue({
      id: 10,
      categoryId: 1,
      title: 'MaxLabel',
      introduction: '标签打印软件',
      type: 1,
      versions: [
        {
          version: '1.0.23',
          platform: 'windows',
          arch: 'x64',
          fileName: 'MaxLabel-Setup-1.0.23.exe',
          fileSize: 264241152,
          downloadUrl: 'https://img.yuwangchenfa.com/software/windows-x64/1.0.23/a.exe'
        }
      ]
    })
    const wrapper = mount(SoftwareDetailView)
    await flushPromises()

    expect(getDetail).toHaveBeenCalledWith(10)
    const link = wrapper.find('a.download')
    expect(link.attributes('href')).toBe('https://img.yuwangchenfa.com/software/windows-x64/1.0.23/a.exe')
    expect(wrapper.text()).toContain('1.0.23')
    expect(wrapper.text()).toContain('252.0 MB')
  })

  it('软件不可见时给出明确提示而不是白屏', async () => {
    // 后端对"不存在/停用/不展示"统一按不存在返回；这里以 null 模拟该响应
    getDetail.mockResolvedValue(null as never)
    const wrapper = mount(SoftwareDetailView)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="software-error"]').text()).toContain('不存在')
  })
})
