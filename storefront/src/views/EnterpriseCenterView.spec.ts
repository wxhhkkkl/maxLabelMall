import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/enterprisePlan', () => ({ listEnterprisePlans: vi.fn() }))
vi.mock('@/api/org', () => ({
  getMyOrg: vi.fn(),
  listOrgMembers: vi.fn(),
  listOrgLicenses: vi.fn(),
  addOrgMember: vi.fn(),
  removeOrgMember: vi.fn(),
  updateOrgMemberRole: vi.fn(),
  transferOrgOwner: vi.fn(),
  assignOrgSeat: vi.fn(),
  revokeOrgSeat: vi.fn(),
  leaveOrg: vi.fn(),
}))

import {
  addOrgMember,
  assignOrgSeat,
  getMyOrg,
  listOrgLicenses,
  listOrgMembers,
  revokeOrgSeat,
  transferOrgOwner,
  updateOrgMemberRole,
} from '@/api/org'
import { listEnterprisePlans } from '@/api/enterprisePlan'
import EnterpriseCenterView from './EnterpriseCenterView.vue'

const getOrg = vi.mocked(getMyOrg)
const listPlans = vi.mocked(listEnterprisePlans)
const listMembers = vi.mocked(listOrgMembers)
const listLicenses = vi.mocked(listOrgLicenses)
const assignSeat = vi.mocked(assignOrgSeat)
const revokeSeat = vi.mocked(revokeOrgSeat)
const addMember = vi.mocked(addOrgMember)
const updateRole = vi.mocked(updateOrgMemberRole)
const transferOwner = vi.mocked(transferOrgOwner)

beforeEach(() => {
  vi.restoreAllMocks()
  getOrg.mockReset()
  listMembers.mockReset()
  listLicenses.mockReset()
  listPlans.mockReset()
  listPlans.mockResolvedValue([])
  assignSeat.mockReset()
  revokeSeat.mockReset()
  addMember.mockReset()
  updateRole.mockReset()
  transferOwner.mockReset()
  push.mockReset()
})

function mountView() {
  return mount(EnterpriseCenterView, { global: { stubs: { RouterLink: true } } })
}

describe('企业中心', () => {
  it('未加入企业时给出明确提示', async () => {
    getOrg.mockResolvedValue({ orgId: null })
    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('你还没有加入企业')
  })

  it('展示成员与席位，并可分配席位', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 0,
      seatTotal: 2,
      seatAvailable: 2,
      licenseEndDate: '2027-10-26',
    })
    listMembers.mockResolvedValue({
      list: [
        { memberId: 1002, nickname: '张三', mobileMasked: '138****8000', role: 3, seatActive: false },
      ],
      total: 1,
    })
    assignSeat.mockResolvedValue(true)

    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('测试企业')
    expect(view.text()).toContain('张三')
    expect(view.text()).toContain('剩余 2')

    const assign = view.findAll('button').find((button) => button.text() === '分配席位')
    expect(assign).toBeTruthy()
    await assign!.trigger('click')
    await flushPromises()
    expect(assignSeat).toHaveBeenCalledWith(1002)
  })

  it('管理员可加入成员、撤销席位、调整角色与转交', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 1,
      seatTotal: 2,
      seatAvailable: 1,
      licenseEndDate: '2027-10-26',
    })
    listMembers.mockResolvedValue({
      list: [
        { memberId: 1002, nickname: '张三', mobileMasked: '138****8000', role: 3, seatActive: true, seatEndDate: '2027-10-26' },
      ],
      total: 1,
    })
    revokeSeat.mockResolvedValue(true)
    addMember.mockResolvedValue(true)
    updateRole.mockResolvedValue(true)
    transferOwner.mockResolvedValue(true)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const view = mountView()
    await flushPromises()

    const byText = (text: string) => view.findAll('button').find((button) => button.text() === text)
    await byText('撤销席位')!.trigger('click')
    await flushPromises()
    expect(revokeSeat).toHaveBeenCalledWith(1002)

    await byText('设为管理员')!.trigger('click')
    await flushPromises()
    expect(updateRole).toHaveBeenCalledWith(1002, 2)

    await byText('转交 OWNER')!.trigger('click')
    await flushPromises()
    expect(transferOwner).toHaveBeenCalledWith(1002)

    await view.find('form.ent-add input').setValue('13800138001')
    await view.find('form.ent-add').trigger('submit')
    await flushPromises()
    expect(addMember).toHaveBeenCalledWith('13800138001')
  })

  it('展示授权批次并可进入续费', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 0,
      seatTotal: 20,
      seatAvailable: 20,
      licenseEndDate: '2027-10-26',
    })
    listMembers.mockResolvedValue({ list: [], total: 0 })
    listLicenses.mockResolvedValue({
      list: [
        {
          id: 1,
          skuId: 601,
          periodMonths: 12,
          seatsTotal: 20,
          startDate: '2026-10-26',
          endDate: '2027-10-26',
          remainingDays: 365,
          sourceOrderNo: 'o202610260001',
          refunded: false,
          folded: false,
          active: true,
        },
      ],
      total: 1,
    })

    const view = mountView()
    await flushPromises()
    const tab = view.findAll('button').find((button) => button.text() === '授权批次')
    await tab!.trigger('click')
    await flushPromises()

    expect(view.text()).toContain('o202610260001')
    expect(view.text()).toContain('20')
    const renew = view.findAll('button').find((button) => button.text() === '续费')
    await renew!.trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/enterprise/plan/601',
      query: { renew: '1' },
    })
  })

  it('已折算的批次只留痕：显示「已折算」且不再提供续费入口', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 0,
      seatTotal: 2,
      seatAvailable: 2,
      licenseEndDate: '2028-03-26',
    })
    listMembers.mockResolvedValue({ list: [], total: 0 })
    listLicenses.mockResolvedValue({
      list: [
        {
          id: 1,
          skuId: 601,
          periodMonths: 12,
          seatsTotal: 1,
          startDate: '2026-10-26',
          endDate: '2027-08-26',
          remainingDays: 300,
          sourceOrderNo: 'o202610260001',
          refunded: false,
          folded: true,
          active: false,
        },
        {
          id: 2,
          skuId: 601,
          periodMonths: 12,
          seatsTotal: 2,
          startDate: '2026-10-26',
          endDate: '2028-03-26',
          remainingDays: 500,
          sourceOrderNo: 'o202611260001',
          refunded: false,
          folded: false,
          active: true,
        },
      ],
      total: 2,
    })

    const view = mountView()
    await flushPromises()
    const tab = view.findAll('button').find((button) => button.text() === '授权批次')
    await tab!.trigger('click')
    await flushPromises()

    expect(view.text()).toContain('已折算')
    // 折算行没有「续费」按钮，只有新批次那一个
    expect(view.findAll('button').filter((button) => button.text() === '续费')).toHaveLength(1)
  })

  it('加载失败有明确提示', async () => {
    getOrg.mockRejectedValue(new Error('网络错误'))
    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('网络错误')
  })

  it('不展示成员的模板与打印数据', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 0,
      seatTotal: 2,
      seatAvailable: 2,
    })
    listMembers.mockResolvedValue({
      list: [{ memberId: 1002, nickname: '张三', role: 3, seatActive: false }],
      total: 1,
    })

    const view = mountView()
    await flushPromises()
    expect(view.text()).not.toContain('模板内容')
    expect(view.text()).not.toContain('打印记录')
    expect(view.text()).not.toContain('dataSnapshot')
  })

  it('企业管理员可看到购买入口并进入企业商品', async () => {
    getOrg.mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 1,
      status: 0,
      seatUsed: 0,
      seatTotal: 0,
      seatAvailable: 0,
    })
    listMembers.mockResolvedValue({ list: [], total: 0 })
    listPlans.mockResolvedValue([
      { spuId: 501, skuId: 601, name: 'MaxLabel 企业授权', periodMonths: 12, price: 99000, stock: 99 },
    ])

    const view = mountView()
    await flushPromises()
    expect(view.text()).toContain('购买企业授权')
    expect(view.text()).toContain('¥990.00')

    const buy = view.findAll('button').find((button) => button.text() === '购买')
    expect(buy).toBeTruthy()
    await buy!.trigger('click')
    expect(push).toHaveBeenCalledWith('/enterprise/plan/601')
  })

  it('普通成员不显示购买入口', async () => {
    getOrg.mockResolvedValue({ orgId: 88, orgName: '测试企业', role: 3, status: 0 })
    listMembers.mockResolvedValue({ list: [], total: 0 })

    const view = mountView()
    await flushPromises()
    expect(view.text()).not.toContain('购买企业授权')
    expect(listPlans).not.toHaveBeenCalled()
  })
})
