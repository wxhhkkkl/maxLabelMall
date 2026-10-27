import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/org', () => ({
  getOrgMemberPage: vi.fn(),
  getOrgLicensePage: vi.fn(),
  addOrgMember: vi.fn(),
  removeOrgMember: vi.fn(),
  updateOrgMemberRole: vi.fn(),
  transferOrgOwner: vi.fn(),
  assignOrgSeat: vi.fn(),
  revokeOrgSeat: vi.fn()
}))

import {
  assignOrgSeat,
  getOrgLicensePage,
  getOrgMemberPage,
  revokeOrgSeat
} from '@/api/member/org'
import OrgDetail from './OrgDetail.vue'

const memberPage = vi.mocked(getOrgMemberPage)
const licensePage = vi.mocked(getOrgLicensePage)
const assignSeat = vi.mocked(assignOrgSeat)
const revokeSeat = vi.mocked(revokeOrgSeat)
const directive = { mounted: () => {} }

const mountDetail = () =>
  mount(OrgDetail, {
    props: { orgId: 88 },
    global: { plugins: [createPinia()], directives: { hasPermi: directive } }
  })

beforeEach(() => {
  memberPage.mockReset()
  licensePage.mockReset()
  assignSeat.mockReset()
  revokeSeat.mockReset()
})

describe('企业详情 · 成员与席位页签', () => {
  it('按企业查询成员并展示角色与席位状态', async () => {
    memberPage.mockResolvedValue({
      total: 2,
      list: [
        {
          memberId: 1001,
          nickname: '张三',
          mobileMasked: '138****8000',
          role: 1,
          seatActive: true,
          seatEndDate: '2027-10-26'
        },
        { memberId: 1002, nickname: '李四', mobileMasked: '139****9000', role: 3, seatActive: false }
      ]
    })
    const wrapper = mountDetail()
    await flushPromises()

    expect(memberPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10, orgId: 88 })
    )
    const text = wrapper.text()
    expect(text).toContain('张三')
    expect(text).toContain('OWNER')
    expect(text).toContain('有效至 2027-10-26')
    expect(text).toContain('李四')
    expect(text).toContain('未分配')
  })

  it('没有成员时不渲染任何成员行', async () => {
    memberPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountDetail()
    await flushPromises()

    expect(wrapper.findAll('.el-table__row')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('OWNER')
  })

  it('分配席位时带上企业与会员标识并刷新列表', async () => {
    memberPage.mockResolvedValue({
      total: 1,
      list: [{ memberId: 1002, nickname: '李四', role: 3, seatActive: false }]
    })
    assignSeat.mockResolvedValue(true)
    const wrapper = mountDetail()
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const assignButton = buttons.find((item) => item.text().includes('分配席位'))
    expect(assignButton).toBeTruthy()
    await assignButton!.trigger('click')
    await flushPromises()

    expect(assignSeat).toHaveBeenCalledWith({ orgId: 88, memberId: 1002 })
    expect(memberPage).toHaveBeenCalledTimes(2)
  })

  it('撤销席位时带上企业与会员标识', async () => {
    memberPage.mockResolvedValue({
      total: 1,
      list: [{ memberId: 1002, nickname: '李四', role: 3, seatActive: true, seatEndDate: '2027-10-26' }]
    })
    revokeSeat.mockResolvedValue(true)
    const wrapper = mountDetail()
    await flushPromises()

    const revokeButton = wrapper.findAll('button').find((item) => item.text().includes('撤销席位'))
    expect(revokeButton).toBeTruthy()
    await revokeButton!.trigger('click')
    await flushPromises()

    expect(revokeSeat).toHaveBeenCalledWith({ orgId: 88, memberId: 1002 })
  })

  it('OWNER 行不出现取消管理员、转交与移出动作', async () => {
    memberPage.mockResolvedValue({
      total: 1,
      list: [{ memberId: 1001, nickname: '张三', role: 1, seatActive: false }]
    })
    const wrapper = mountDetail()
    await flushPromises()

    const text = wrapper.text()
    expect(text).not.toContain('设为管理员')
    expect(text).not.toContain('转交 OWNER')
    expect(text).not.toContain('移出')
  })
})

describe('企业详情 · 隐私（FR-319）', () => {
  it('只展示成员标识/角色/席位，不出现模板与打印数据', async () => {
    memberPage.mockResolvedValue({
      total: 1,
      list: [
        {
          memberId: 1002,
          nickname: '李四',
          mobileMasked: '139****9000',
          role: 3,
          seatActive: true,
          seatEndDate: '2027-10-26'
        }
      ]
    })
    const wrapper = mountDetail()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('李四')
    expect(text).not.toContain('模板')
    expect(text).not.toContain('打印')
    // 也不该出现「查看」成员私有数据的入口
    expect(text).not.toContain('云模板')
  })
})

describe('企业详情 · 权限串（admin-api §5）', () => {
  it('成员/席位动作都带 v-hasPermi，与后端 @PreAuthorize 对齐', async () => {
    const seen: string[][] = []
    const recording = {
      mounted: (_el: unknown, binding: { value: string[] }) => {
        seen.push(binding.value)
      }
    }
    memberPage.mockResolvedValue({
      total: 1,
      list: [{ memberId: 1002, nickname: '李四', role: 3, seatActive: false }]
    })

    mount(OrgDetail, {
      props: { orgId: 88 },
      global: { plugins: [createPinia()], directives: { hasPermi: recording } }
    })
    await flushPromises()

    expect(seen).toContainEqual(['member:org:member:add'])
    expect(seen).toContainEqual(['member:org:seat:assign'])
    expect(seen).toContainEqual(['member:org:member:update-role'])
    expect(seen).toContainEqual(['member:org:owner:transfer'])
    expect(seen).toContainEqual(['member:org:member:remove'])
  })
})

describe('企业详情 · 授权批次页签', () => {
  it('切换页签后按企业查询批次并标记折算、退款与过期', async () => {
    memberPage.mockResolvedValue({ total: 0, list: [] })
    licensePage.mockResolvedValue({
      total: 3,
      list: [
        {
          id: 1,
          skuId: 9001,
          periodMonths: 12,
          seatsTotal: 2,
          startDate: '2026-10-26',
          endDate: '2027-10-26',
          remainingDays: 365,
          sourceOrderNo: 'M202610260001',
          refunded: false,
          folded: false,
          active: true
        },
        {
          id: 2,
          skuId: 9001,
          periodMonths: 12,
          seatsTotal: 1,
          startDate: '2025-10-26',
          endDate: '2026-10-26',
          remainingDays: 120,
          sourceOrderNo: 'M202510260002',
          refunded: false,
          folded: true,
          active: true
        },
        {
          id: 3,
          skuId: 9001,
          periodMonths: 12,
          seatsTotal: 1,
          startDate: '2024-10-26',
          endDate: '2025-10-26',
          remainingDays: 0,
          sourceOrderNo: 'M202410260003',
          refunded: true,
          folded: false,
          active: false
        }
      ]
    })
    const wrapper = mountDetail()
    await flushPromises()

    const tabs = wrapper.findAll('.el-tabs__item')
    expect(tabs.length).toBeGreaterThan(1)
    await tabs[1].trigger('click')
    await flushPromises()

    expect(licensePage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10, orgId: 88 })
    )
    const text = wrapper.text()
    expect(text).toContain('1 年')
    expect(text).toContain('M202610260001')
    expect(text).toContain('已折算')
    expect(text).toContain('来源已退款')
    expect(text).toContain('已过期')
  })

  it('没有批次时列表为空', async () => {
    memberPage.mockResolvedValue({ total: 0, list: [] })
    licensePage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountDetail()
    await flushPromises()

    const tabs = wrapper.findAll('.el-tabs__item')
    await tabs[1].trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.el-table__row')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('已折算')
  })
})
