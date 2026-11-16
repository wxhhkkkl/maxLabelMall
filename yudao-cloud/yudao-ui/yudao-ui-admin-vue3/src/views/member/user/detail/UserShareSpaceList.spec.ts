import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/orgShareContent', () => ({
  getMemberShareOverview: vi.fn(),
  getMemberShareContentPage: vi.fn(),
  getMemberShareContent: vi.fn(),
  getMemberShareFolders: vi.fn()
}))

import {
  getMemberShareContent,
  getMemberShareContentPage,
  getMemberShareFolders,
  getMemberShareOverview
} from '@/api/member/orgShareContent'
import UserShareSpaceList from './UserShareSpaceList.vue'

const overview = vi.mocked(getMemberShareOverview)
const page = vi.mocked(getMemberShareContentPage)
const detail = vi.mocked(getMemberShareContent)
const folders = vi.mocked(getMemberShareFolders)

const mountList = () =>
  mount(UserShareSpaceList, {
    props: { userId: 1001 },
    global: { plugins: [createPinia()] }
  })

beforeEach(() => {
  overview.mockReset()
  page.mockReset()
  detail.mockReset()
  folders.mockReset()
  overview.mockResolvedValue({ usedBytes: 2048, quotaBytes: 10485760, remainingBytes: 10483712 })
  folders.mockResolvedValue([
    { id: 600, name: '我的模板', parentId: null, memberCount: 0, contentCount: 87 },
    { id: 601, name: '上衣', parentId: 600, memberCount: 0, contentCount: 12 },
    { id: 700, name: 'LabelShop5', parentId: null, memberCount: 0, contentCount: 54 }
  ])
  page.mockResolvedValue({
    total: 2,
    list: [
      {
        id: 9,
        name: '顺丰面单',
        contentType: 1,
        sizeBytes: 48211,
        widthMm: 100,
        heightMm: 150,
        category: '物流',
        createTime: '2026-10-20T10:00:00'
      },
      {
        id: 10,
        name: '说明.txt',
        contentType: 2,
        sizeBytes: 100,
        createTime: '2026-10-21T10:00:00'
      }
    ]
  })
  detail.mockResolvedValue({
    id: 9,
    name: '顺丰面单',
    contentType: 1,
    sizeBytes: 48211,
    storageType: 1,
    content: '<?xml version="1.0"?><labelshopdocument/>',
    mimeType: 'text/xml'
  })
})

describe('会员个人空间页签', () => {
  it('展示容量概览与内容列表', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(overview).toHaveBeenCalledWith(1001)
    expect(page).toHaveBeenCalledWith({
      memberId: 1001,
      folderId: undefined,
      contentType: undefined,
      keyword: undefined,
      pageNo: 1,
      pageSize: 10
    })
    expect(wrapper.text()).toContain('容量占用')
    expect(wrapper.text()).toContain('顺丰面单')
    expect(wrapper.text()).toContain('模板')
    expect(wrapper.text()).toContain('纯文本')
    expect(wrapper.text()).toContain('100 × 150 mm')
  })

  it('按关键词与类型搜索', async () => {
    const wrapper = mountList()
    await flushPromises()

    const keyword = wrapper
      .findAll('input')
      .find((item) => item.attributes('placeholder') === '名称/分类/关键词')
    await keyword!.setValue('面单')
    await wrapper.findAll('button').find((item) => item.text() === '搜索')!.trigger('click')
    await flushPromises()

    expect(page).toHaveBeenLastCalledWith({
      memberId: 1001,
      folderId: undefined,
      contentType: undefined,
      keyword: '面单',
      pageNo: 1,
      pageSize: 10
    })
  })

  it('左侧树展示文件夹并点击切换筛选', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(folders).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('我的模板')
    expect(wrapper.text()).toContain('上衣')
    expect(wrapper.text()).toContain('LabelShop5')

    const target = wrapper.findAll('.folder-row').find((item) => item.text().includes('上衣'))
    expect(target).toBeTruthy()
    await target!.trigger('click')
    await flushPromises()

    expect(page).toHaveBeenLastCalledWith({
      memberId: 1001,
      folderId: 601,
      contentType: undefined,
      keyword: undefined,
      pageNo: 1,
      pageSize: 10
    })
  })

  it('预览时按会员与内容编号读取详情', async () => {
    const wrapper = mountList()
    await flushPromises()

    await wrapper.findAll('button').find((item) => item.text() === '预览')!.trigger('click')
    await flushPromises()

    expect(detail).toHaveBeenCalledWith(1001, 9)
    expect(wrapper.text()).toContain('内容预览')
  })
})
