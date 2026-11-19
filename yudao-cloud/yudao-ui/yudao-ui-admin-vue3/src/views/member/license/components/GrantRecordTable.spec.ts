import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 赠送记录 + 撤回（020 / Q164）
 *
 * 钉三件事：
 * · 已撤回的行不再提供撤回入口，并把撤回人 / 理由显示出来（记录不删，靠留痕）
 * · **撤回理由必填**，空着时「确认撤回」不可点
 * · 撤回成功后通知父组件刷新（看板与客户明细都要变）
 */
const getLicenseGrantPage = vi.fn()
const revokeLicenseGrant = vi.fn()
vi.mock('@/api/member/license', () => ({
  getLicenseGrantPage: (...a: unknown[]) => getLicenseGrantPage(...a),
  revokeLicenseGrant: (...a: unknown[]) => revokeLicenseGrant(...a),
  getLicenseSummary: vi.fn(),
  getLicenseCustomerPage: vi.fn(),
  getLicenseGiftTargetPage: vi.fn(),
  grantLicense: vi.fn()
}))

const GrantRecordTable = (await import('./GrantRecordTable.vue')).default

const ACTIVE_ROW = {
  id: 1,
  targetType: 1,
  targetId: 639,
  targetName: '张先生',
  contentType: 1,
  months: 12,
  reason: '维护客户',
  operatorId: 249,
  operatorName: '运营小王',
  createTime: new Date(2026, 10, 19, 20, 12, 36).getTime()
}

const REVOKED_ROW = {
  id: 2,
  targetType: 2,
  targetId: 1,
  targetName: '示例企业',
  contentType: 2,
  seats: 5,
  reason: '补发席位',
  operatorId: 249,
  operatorName: '运营小王',
  createTime: new Date(2026, 10, 19, 20, 20, 0).getTime(),
  revokeTime: new Date(2026, 10, 19, 20, 30, 0).getTime(),
  revokeReason: '误送给该企业',
  revokeOperatorName: '张经理'
}

async function mountTable(rows = [ACTIVE_ROW, REVOKED_ROW]) {
  getLicenseGrantPage.mockResolvedValue({ list: rows, total: rows.length })
  const w = mount(GrantRecordTable, {
    global: { plugins: [createPinia()], directives: { hasPermi: () => {} } }
  })
  await flushPromises()
  return w
}

beforeEach(() => {
  getLicenseGrantPage.mockReset()
  revokeLicenseGrant.mockReset()
  revokeLicenseGrant.mockResolvedValue(true)
})

describe('赠送记录', () => {
  it('已撤回的行不再显示撤回按钮，并展示撤回人与理由', async () => {
    const w = await mountTable()

    const text = w.text()
    expect(text).toContain('已生效')
    expect(text).toContain('已撤回')
    expect(text).toContain('误送给该企业')
    expect(text).toContain('张经理')

    const revokeButtons = w.findAll('button').filter((b) => b.text() === '撤回')
    expect(revokeButtons).toHaveLength(1)
  })

  it('撤回理由必填：空着时确认按钮不可点', async () => {
    const w = await mountTable([ACTIVE_ROW])

    await w.findAll('button').find((b) => b.text() === '撤回')!.trigger('click')
    await flushPromises()

    const confirm = w.findAll('button').find((b) => b.text() === '确认撤回')!
    expect(confirm.attributes('disabled')).toBeDefined()

    await w.find('textarea').setValue('   ')
    expect(confirm.attributes('disabled')).toBeDefined()

    await w.find('textarea').setValue('  误送  ')
    expect(confirm.attributes('disabled')).toBeUndefined()
  })

  it('提交撤回：带上记录编号与去空格的理由，成功后通知父组件', async () => {
    const w = await mountTable([ACTIVE_ROW])

    await w.findAll('button').find((b) => b.text() === '撤回')!.trigger('click')
    await flushPromises()
    await w.find('textarea').setValue('  误送给该客户  ')
    await w.findAll('button').find((b) => b.text() === '确认撤回')!.trigger('click')
    await flushPromises()

    expect(revokeLicenseGrant).toHaveBeenCalledWith({ id: 1, reason: '误送给该客户' })
    expect(w.emitted('success')).toHaveLength(1)
  })
})
