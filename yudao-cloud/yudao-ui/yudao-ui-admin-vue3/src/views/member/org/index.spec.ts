import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/user', () => ({
  getUserPage: vi.fn(async () => ({ list: [], total: 0 }))
}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    success: vi.fn(),
    error: vi.fn(),
    confirm: vi.fn(async () => true),
    delConfirm: vi.fn(async () => true)
  })
}))
vi.mock('@/api/member/org', () => ({
  getOrgPage: vi.fn(),
  getOrg: vi.fn(),
  createOrg: vi.fn(),
  updateOrg: vi.fn(),
  updateOrgStatus: vi.fn(),
  getOrgMemberPage: vi.fn(async () => ({ list: [], total: 0 })),
  getOrgLicensePage: vi.fn(async () => ({ list: [], total: 0 })),
  addOrgMember: vi.fn(),
  removeOrgMember: vi.fn(),
  updateOrgMemberRole: vi.fn(),
  transferOrgOwner: vi.fn(),
  assignOrgSeat: vi.fn(),
  revokeOrgSeat: vi.fn()
}))

import { getOrg, getOrgPage, updateOrgStatus } from '@/api/member/org'
import OrgIndex from './index.vue'

const getPage = vi.mocked(getOrgPage)
const getOrgMock = vi.mocked(getOrg)
const updateStatus = vi.mocked(updateOrgStatus)
const directive = { mounted: () => {} }

const org = {
  id: 88,
  name: '测试企业',
  ownerMemberId: 1001,
  ownerNickname: '张三',
  ownerMobile: '138****8000',
  status: 0,
  memberCount: 5,
  seatTotal: 20,
  seatUsed: 3,
  licenseEndDate: '2027-10-26'
}

const mountIndex = () =>
  mount(OrgIndex, {
    global: { plugins: [createPinia()], directives: { hasPermi: directive } }
  })

/**
 * 查询区与弹窗各有一个「请输入企业名称」输入框，弹窗那个在 DOM 里排后面；
 * 且 `Dialog` 不传 `appendToBody`，所以弹窗内容仍在 wrapper 内。
 */
type Wrapper = ReturnType<typeof mountIndex>

const dialogText = (wrapper: Wrapper) => wrapper.find('.el-dialog').text()
const dialogNameInput = (wrapper: Wrapper) => {
  const inputs = wrapper.findAll('input[placeholder="请输入企业名称"]')
  return inputs[inputs.length - 1]?.element as HTMLInputElement | undefined
}

beforeEach(() => {
  getPage.mockReset()
  getOrgMock.mockReset()
  updateStatus.mockReset()
})

describe('企业管理页', () => {
  it('展示企业、席位与状态', async () => {
    getPage.mockResolvedValue({ total: 1, list: [org] })
    const wrapper = mountIndex()
    await flushPromises()
    expect(wrapper.text()).toContain('测试企业')
    expect(wrapper.text()).toContain('3 / 20')
    expect(wrapper.text()).toContain('2027-10-26')
  })

  it('没有企业时不渲染任何行', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountIndex()
    await flushPromises()
    expect(wrapper.findAll('.el-table__row')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('测试企业')
  })

  it('按名称与状态查询时回到第一页并带上筛选条件', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountIndex()
    await flushPromises()
    getPage.mockClear()

    await wrapper.find('input[placeholder="请输入企业名称"]').setValue('杏林')
    const buttons = wrapper.findAll('button')
    await buttons.find((item) => item.text().includes('搜索'))!.trigger('click')
    await flushPromises()

    expect(getPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, name: '杏林' })
    )
  })

  it('新增企业时打开空白表单', async () => {
    getPage.mockResolvedValue({ total: 1, list: [org] })
    const wrapper = mountIndex()
    await flushPromises()

    await wrapper.findAll('button').find((item) => item.text().includes('新增企业'))!.trigger('click')
    await flushPromises()

    expect(dialogText(wrapper)).toContain('新增企业')
    expect(dialogNameInput(wrapper)?.value).toBe('')
    expect(getOrgMock).not.toHaveBeenCalled()
  })

  it('编辑企业时带出企业信息', async () => {
    getPage.mockResolvedValue({ total: 1, list: [org] })
    getOrgMock.mockResolvedValue(org)
    const wrapper = mountIndex()
    await flushPromises()

    await wrapper.findAll('button').find((item) => item.text() === '编辑')!.trigger('click')
    await flushPromises()

    expect(getOrgMock).toHaveBeenCalledWith(88)
    expect(dialogText(wrapper)).toContain('编辑企业')
    expect(dialogNameInput(wrapper)?.value).toBe('测试企业')
  })

  it('停用企业时调用状态接口', async () => {
    getPage.mockResolvedValue({ total: 1, list: [org] })
    updateStatus.mockResolvedValue(true)
    const wrapper = mountIndex()
    await flushPromises()

    await wrapper.findAll('button').find((item) => item.text() === '停用')!.trigger('click')
    await flushPromises()

    expect(updateStatus).toHaveBeenCalledWith(88, 1)
  })
})
