import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/labelshopImport', () => ({
  getLabelshopPrecheck: vi.fn(),
  getLatestLabelshopTask: vi.fn(),
  getLabelshopTask: vi.fn(),
  getLabelshopItems: vi.fn(),
  startLabelshopImport: vi.fn(),
  getMemberLabelshopPrecheck: vi.fn(),
  getMemberLatestLabelshopTask: vi.fn(),
  getMemberLabelshopTask: vi.fn(),
  getMemberLabelshopItems: vi.fn(),
  startMemberLabelshopImport: vi.fn()
}))

import {
  getLabelshopItems,
  getLabelshopPrecheck,
  getLabelshopTask,
  getLatestLabelshopTask,
  getMemberLabelshopItems,
  getMemberLabelshopPrecheck,
  getMemberLabelshopTask,
  getMemberLatestLabelshopTask,
  startLabelshopImport,
  startMemberLabelshopImport
} from '@/api/member/labelshopImport'
import LabelshopImportDialog from './LabelshopImportDialog.vue'

const precheck = vi.mocked(getLabelshopPrecheck)
const latest = vi.mocked(getLatestLabelshopTask)
const task = vi.mocked(getLabelshopTask)
const items = vi.mocked(getLabelshopItems)
const start = vi.mocked(startLabelshopImport)
const memberPrecheck = vi.mocked(getMemberLabelshopPrecheck)
const memberLatest = vi.mocked(getMemberLatestLabelshopTask)
const memberTask = vi.mocked(getMemberLabelshopTask)
const memberItems = vi.mocked(getMemberLabelshopItems)
const memberStart = vi.mocked(startMemberLabelshopImport)

const precheckValue = {
  orgId: 88,
  orgName: '云马科技',
  ownerMemberId: 1001,
  ownerNickname: '张三',
  ownerMobile: '138****8000',
  orgStatus: 0,
  licenseActive: true,
  licenseEndDate: '2027-10-26',
  seatCount: 3,
  folderName: '20261112173000导入',
  personalEdition: 'professional',
  personalUsedBytes: 0,
  personalQuotaBytes: 104857600,
  enterpriseUsedBytes: 0,
  enterpriseQuotaBytes: 314572800,
  runningTaskId: null
}

const mountDialog = () =>
  mount(LabelshopImportDialog, {
    props: { modelValue: true, orgId: 88 },
    global: { plugins: [createPinia()], directives: { hasPermi: { mounted: () => {} } } }
  })

beforeEach(() => {
  precheck.mockReset()
  latest.mockReset()
  task.mockReset()
  items.mockReset()
  start.mockReset()
  memberPrecheck.mockReset()
  memberLatest.mockReset()
  memberTask.mockReset()
  memberItems.mockReset()
  memberStart.mockReset()
  precheck.mockResolvedValue(precheckValue)
  latest.mockResolvedValue(null)
  memberLatest.mockResolvedValue(null)
  memberItems.mockResolvedValue({ total: 0, list: [] })
  items.mockResolvedValue({ total: 0, list: [] })
  Object.defineProperty(window, 'location', {
    value: { protocol: 'https:' },
    configurable: true
  })
})

describe('历史模板导入对话框', () => {
  it('打开时展示 OWNER、文件夹与容量', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(precheck).toHaveBeenCalledWith(88)
    expect(wrapper.text()).toContain('云马科技')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('20261112173000导入')
    expect(wrapper.text()).toContain('开始导入')
  })

  it('开始导入时提交账号、密码与授权确认', async () => {
    start.mockResolvedValue({ taskId: 9 })
    task.mockResolvedValue({
      id: 9,
      orgId: 88,
      ownerMemberId: 1001,
      folderName: '20261112173000导入',
      sourceAccountMasked: 'us***@example.com',
      status: 3,
      totalCount: 0,
      processedCount: 0,
      successCount: 0,
      skipCount: 0,
      failCount: 0,
      personalBytes: 0,
      enterpriseBytes: 0,
      personalUsedBytes: 0,
      personalQuotaBytes: 104857600,
      personalExceeded: false,
      enterpriseUsedBytes: 0,
      enterpriseQuotaBytes: 314572800,
      enterpriseExceeded: false,
      libraries: [],
      uncleaned: true,
      archiveJsonCount: 0
    })
    const wrapper = mountDialog()
    await flushPromises()

    const textInputs = wrapper.findAll('input').filter((item) => item.attributes('type') !== 'checkbox')
    await textInputs[0].setValue('user@example.com')
    await textInputs[1].setValue('secret')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    const startButton = wrapper.findAll('button').find((item) => item.text().includes('开始导入'))
    expect(startButton).toBeTruthy()
    await startButton!.trigger('click')
    await flushPromises()

    expect(start).toHaveBeenCalledWith({
      orgId: 88,
      account: 'user@example.com',
      password: 'secret',
      authorized: true
    })
  })

  it('HTTP 下不阻断，正常加载前置条件', async () => {
    Object.defineProperty(window, 'location', {
      value: { protocol: 'http:' },
      configurable: true
    })
    const wrapper = mountDialog()
    await flushPromises()

    expect(precheck).toHaveBeenCalledWith(88)
    expect(latest).toHaveBeenCalledWith(88)
    expect(wrapper.text()).not.toContain('该功能必须通过 HTTPS 使用')
  })

  it('企业停用时展示阻断并禁用开始', async () => {
    precheck.mockResolvedValue({ ...precheckValue, orgStatus: 1 })
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text()).toContain('企业已停用')
    const startButton = wrapper.findAll('button').find((item) => item.text().includes('开始导入'))
    expect(startButton!.attributes('disabled')).toBeDefined()
  })

  it('企业授权到期时阻断开始', async () => {
    precheck.mockResolvedValue({ ...precheckValue, licenseActive: false })
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text()).toContain('企业授权已到期')
    const startButton = wrapper.findAll('button').find((item) => item.text().includes('开始导入'))
    expect(startButton!.attributes('disabled')).toBeDefined()
  })

  it('刷新后恢复最近一次已完成任务', async () => {
    latest.mockResolvedValue({
      id: 9,
      orgId: 88,
      ownerMemberId: 1001,
      folderName: '20261112173000导入',
      sourceAccountMasked: 'us***@example.com',
      status: 2,
      totalCount: 44,
      processedCount: 44,
      successCount: 44,
      skipCount: 0,
      failCount: 0,
      personalBytes: 0,
      enterpriseBytes: 0,
      personalUsedBytes: 0,
      personalQuotaBytes: 104857600,
      personalExceeded: false,
      enterpriseUsedBytes: 0,
      enterpriseQuotaBytes: 314572800,
      enterpriseExceeded: false,
      libraries: [{ library: 12, total: 44, success: 44, skip: 0, fail: 0 }],
      uncleaned: true,
      archiveJsonCount: 0
    })
    const wrapper = mountDialog()
    await flushPromises()

    expect(latest).toHaveBeenCalledWith(88)
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('我的模板库')
    expect(wrapper.text()).toContain('成功')
    expect(wrapper.text()).toContain('跳过')
    expect(wrapper.text()).toContain('失败')
    expect(wrapper.text()).toContain('原样导入、未清洗')
    expect(wrapper.text()).toContain('归档 JSON')
    expect(wrapper.text()).toContain('企业空间用量')
    expect(wrapper.text()).toContain('剩余')
  })

  it('会员模式只展示会员与个人空间，并按 memberId 发起', async () => {
    memberPrecheck.mockResolvedValue({
      orgId: undefined,
      ownerMemberId: 1001,
      ownerNickname: '张三',
      ownerMobile: '138****8000',
      orgStatus: 0,
      licenseActive: true,
      seatCount: 0,
      folderName: null,
      personalEdition: 'standard',
      personalUsedBytes: 0,
      personalQuotaBytes: 10485760,
      enterpriseUsedBytes: 0,
      enterpriseQuotaBytes: 0,
      runningTaskId: null
    })
    memberStart.mockResolvedValue({ taskId: 11 })
    memberTask.mockResolvedValue({
      id: 11,
      subjectType: 2,
      orgId: null,
      ownerMemberId: 1001,
      folderName: null,
      sourceAccountMasked: 'us***@example.com',
      status: 0,
      totalCount: 0,
      processedCount: 0,
      successCount: 0,
      skipCount: 0,
      failCount: 0,
      personalBytes: 0,
      enterpriseBytes: 0,
      personalUsedBytes: 0,
      personalQuotaBytes: 10485760,
      personalExceeded: false,
      enterpriseUsedBytes: 0,
      enterpriseQuotaBytes: 0,
      enterpriseExceeded: false,
      libraries: [],
      uncleaned: true,
      archiveJsonCount: 0
    })
    const wrapper = mount(LabelshopImportDialog, {
      props: { modelValue: true, memberId: 1001 },
      global: { plugins: [createPinia()], directives: { hasPermi: { mounted: () => {} } } }
    })
    await flushPromises()

    expect(memberPrecheck).toHaveBeenCalledWith(1001)
    expect(memberLatest).toHaveBeenCalledWith(1001)
    expect(precheck).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('会员')
    expect(wrapper.text()).not.toContain('持席成员数')
    expect(wrapper.text()).not.toContain('企业空间')

    const textInputs = wrapper.findAll('input').filter((item) => item.attributes('type') !== 'checkbox')
    await textInputs[0].setValue('user@example.com')
    await textInputs[1].setValue('secret')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    const startButton = wrapper.findAll('button').find((item) => item.text().includes('开始导入'))
    await startButton!.trigger('click')
    await flushPromises()

    expect(memberStart).toHaveBeenCalledWith({
      memberId: 1001,
      account: 'user@example.com',
      password: 'secret',
      authorized: true
    })
    expect(memberTask).toHaveBeenCalledWith(11)
    expect(wrapper.text()).toContain('个人空间用量')
    expect(wrapper.text()).toContain('剩余')
  })

  it('轮询到终态后停止并渲染失败清单', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    try {
      latest.mockResolvedValue(runningTask())
      task.mockResolvedValue(doneTask())
      items.mockResolvedValue({ total: 1, list: [failureItem()] })
      const wrapper = mountDialog()
      await flushPromises()
      expect(task).not.toHaveBeenCalled()

      vi.advanceTimersByTime(2500)
      await flushPromises()

      expect(task).toHaveBeenCalledWith(9)
      expect(items).toHaveBeenCalledWith({ taskId: 9, status: 3, pageNo: 1, pageSize: 10 })
      expect(wrapper.text()).toContain('失败清单')
      expect(wrapper.text()).toContain('坏模板')
      expect(wrapper.text()).toContain('正文为空')

      const calls = task.mock.calls.length
      vi.advanceTimersByTime(5000)
      await flushPromises()
      expect(task.mock.calls.length).toBe(calls)
    } finally {
      vi.useRealTimers()
    }
  })

  it('会员模式轮询使用会员端点并在终态停止', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    try {
      memberPrecheck.mockResolvedValue({
        ...precheckValue,
        orgId: undefined,
        folderName: null,
        seatCount: 0,
        enterpriseUsedBytes: 0,
        enterpriseQuotaBytes: 0
      })
      memberLatest.mockResolvedValue(runningTask(11, 2))
      memberTask.mockResolvedValue(doneTask(11, 2))
      memberItems.mockResolvedValue({ total: 1, list: [failureItem()] })
      const wrapper = mount(LabelshopImportDialog, {
        props: { modelValue: true, memberId: 1001 },
        global: { plugins: [createPinia()], directives: { hasPermi: { mounted: () => {} } } }
      })
      await flushPromises()

      vi.advanceTimersByTime(2500)
      await flushPromises()

      expect(memberTask).toHaveBeenCalledWith(11)
      expect(memberItems).toHaveBeenCalledWith({ taskId: 11, status: 3, pageNo: 1, pageSize: 10 })
      expect(items).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('坏模板')
    } finally {
      vi.useRealTimers()
    }
  })
})

function runningTask(id = 9, subjectType = 1) {
  return {
    id,
    subjectType,
    orgId: subjectType === 1 ? 88 : null,
    ownerMemberId: 1001,
    folderName: subjectType === 1 ? '20261112173000导入' : null,
    sourceAccountMasked: 'us***@example.com',
    status: 1,
    totalCount: 2,
    processedCount: 0,
    successCount: 0,
    skipCount: 0,
    failCount: 0,
    personalBytes: 0,
    enterpriseBytes: 0,
    personalUsedBytes: 0,
    personalQuotaBytes: 104857600,
    personalExceeded: false,
    enterpriseUsedBytes: 0,
    enterpriseQuotaBytes: 314572800,
    enterpriseExceeded: false,
    libraries: [],
    uncleaned: true,
    archiveJsonCount: 0
  }
}

function doneTask(id = 9, subjectType = 1) {
  return {
    ...runningTask(id, subjectType),
    status: 2,
    processedCount: 2,
    successCount: 1,
    failCount: 1,
    personalBytes: 10
  }
}

function failureItem() {
  return {
    id: 5,
    sourceLibrary: 13,
    sourceCategory: '组模板库/上衣',
    sourceName: '坏模板',
    targetScope: 2,
    format: 'lsdx',
    editable: true,
    sizeBytes: 0,
    status: 3,
    errorMessage: '正文为空'
  }
}
