import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/user', () => ({
  getUserPage: vi.fn()
}))
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: vi.fn() })
}))

import { getUserPage } from '@/api/member/user'
import MemberUserIndex from './index.vue'

const getPage = vi.mocked(getUserPage)
const directive = { mounted: () => {} }
const stubs = {
  'doc-alert': true,
  MemberTagSelect: true,
  MemberLevelSelect: true,
  MemberGroupSelect: true,
  UserLevelUpdateForm: true,
  UserPointUpdateForm: true,
  UserBalanceUpdateForm: true,
  UserForm: true,
  CouponSendForm: true
}

beforeEach(() => {
  getPage.mockReset()
})

function mountList(rows: any[]) {
  getPage.mockResolvedValue({ total: rows.length, list: rows } as never)
  return mount(MemberUserIndex, {
    global: {
      plugins: [createPinia()],
      directives: { hasPermi: directive },
      stubs
    }
  })
}

const headersOf = (wrapper: any) =>
  wrapper
    .findAll('.el-table__header th')
    .map((th: any) => th.text())
    .filter((text: string) => !!text)

describe('会员列表列口径（2026-11-18）', () => {
  it('只展示 用户编号 / 手机号 / 昵称 / 公司名称 / 订阅状态 / 登录时间 / 注册时间 / 状态（+ 操作）', async () => {
    const wrapper = mountList([
      { id: 1, mobile: '13800000001', nickname: '张三', company: '赋签', status: 0 }
    ])
    await flushPromises()

    expect(headersOf(wrapper)).toEqual([
      '用户编号',
      '手机号',
      '昵称',
      '公司名称',
      '订阅状态',
      '登录时间',
      '注册时间',
      '状态',
      '操作'
    ])
  })

  it('订阅状态仍区分 有效 / 已过期 / 未订阅', async () => {
    const wrapper = mountList([
      { id: 1, mobile: '13800000001', subscriptionActive: true, subscriptionEndDate: '2027-10-20' },
      { id: 2, mobile: '13800000002', subscriptionActive: false, subscriptionEndDate: '2026-09-01' },
      { id: 3, mobile: '13800000003', subscriptionActive: false }
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('有效')
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('未订阅')
  })

  it('注册时间只到天；登录时间保留到秒', async () => {
    const wrapper = mountList([
      {
        id: 1,
        mobile: '13800000001',
        createTime: '2026-11-01 10:20:30',
        loginDate: '2026-11-02 18:30:45'
      }
    ])
    await flushPromises()

    expect(wrapper.text()).toContain('2026-11-01') // 注册：只到天
    expect(wrapper.text()).not.toContain('2026-11-01 10:20:30')
    expect(wrapper.text()).toContain('2026-11-02 18:30:45') // 登录：到秒
  })
})
