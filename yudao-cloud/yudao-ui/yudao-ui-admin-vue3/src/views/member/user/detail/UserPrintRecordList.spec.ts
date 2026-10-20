import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/printRecord', () => ({ getPrintRecordPage: vi.fn() }))

import { getPrintRecordPage } from '@/api/member/printRecord'
import UserPrintRecordList from './UserPrintRecordList.vue'

const getPage = vi.mocked(getPrintRecordPage)

beforeEach(() => {
  getPage.mockReset()
})

describe('会员打印记录页签', () => {
  it('展示打印时间、模板、张数、状态与打印机', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 5, memberId: 1001, deviceId: 'device', recordUid: 'record',
        printTime: '2026-10-20T10:00:00', templateTitle: '顺丰面单',
        mode: 'driver', count: 10, copies: 1, physicalCount: 10, sentCount: 10,
        status: 'completed', printer: 'Zebra ZT410', createTime: '2026-10-20T10:00:01'
      }]
    })

    const wrapper = mount(UserPrintRecordList, {
      props: { userId: 1001 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('顺丰面单')
    expect(wrapper.text()).toContain('completed')
    expect(wrapper.text()).toContain('Zebra ZT410')
    expect(wrapper.text()).toContain('10 / 10')
    expect(getPage).toHaveBeenCalledWith(expect.objectContaining({ userId: 1001, pageNo: 1, pageSize: 10 }))
  })
})
