import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 赠送弹窗（020 / US69）
 *
 * 钉三件事：
 * · **超出剩余额度的档位置灰**（后端下发的 maxGiftMonths，不让运营选了才知道不行）
 * · **理由为空不许提交**
 * · 提交成功通知父组件刷新
 */
const grantLicense = vi.fn()
vi.mock('@/api/member/license', () => ({
  grantLicense: (...a: unknown[]) => grantLicense(...a),
  getLicenseSummary: vi.fn(),
  getLicenseCustomerPage: vi.fn(),
  getLicenseGrantPage: vi.fn()
}))

const GrantForm = (await import('./GrantForm.vue')).default

const ROW = {
  targetType: 1,
  targetId: 88,
  targetName: '小王',
  remainingDays: 210,
  expiredDays: 0,
  active: true,
  maxGiftMonths: 2
}

/** 企业从未有过授权（Q162 新入口下会出现的对象） */
const ORG_NO_LICENSE = {
  targetType: 2,
  targetId: 9,
  targetName: '某企业',
  status: 0,
  remainingDays: 0,
  active: false,
  maxGiftMonths: 12
}

async function mountForm(row: Record<string, unknown> = ROW) {
  const w = mount(GrantForm)
  ;(w.vm as unknown as { open: (r: Record<string, unknown>) => void }).open(row)
  await flushPromises()
  return w
}

const tiers = (w: Awaited<ReturnType<typeof mountForm>>) =>
  w.findAll('.el-radio-group').at(-1)!.findAll('.el-radio')

beforeEach(() => {
  grantLicense.mockReset()
  grantLicense.mockResolvedValue(true)
})

describe('赠送授权弹窗', () => {
  it('剩 10 个月（maxGiftMonths=2）：3 / 6 / 12 档置灰，只剩 1 个月可选', async () => {
    const w = await mountForm()

    const radios = tiers(w)
    expect(radios).toHaveLength(4)
    expect(radios[0].classes()).not.toContain('is-disabled')
    expect(radios[1].classes()).toContain('is-disabled')
    expect(radios[2].classes()).toContain('is-disabled')
    expect(radios[3].classes()).toContain('is-disabled')
    expect(w.text()).toContain('最多还能赠送 2 个月')
  })

  it('理由为空 → 不提交', async () => {
    const w = await mountForm()

    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(grantLicense).not.toHaveBeenCalled()
  })

  it('无有效授权的企业送席位 → 强制同时选时长，提交带上 months（Q157）', async () => {
    const w = await mountForm(ORG_NO_LICENSE)

    const seatRadio = w.findAll('.el-radio').find((r) => r.text() === '席位')!
    await seatRadio.find('input').setValue(true)
    await flushPromises()

    expect(w.text()).toContain('该企业暂无有效授权，赠送席位需同时指定时长')
    // 席位 + 时长两个 radio group 同时出现
    expect(w.findAll('.el-radio-group')).toHaveLength(2)

    await w.get('textarea').setValue('新客户补偿')
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(grantLicense).toHaveBeenCalledWith(
      expect.objectContaining({
        targetType: 2,
        targetId: 9,
        contentType: 2,
        seats: 1,
        months: 1,
        reason: '新客户补偿'
      })
    )
  })

  it('填了理由 → 带上对象、内容、月数与理由提交', async () => {
    const w = await mountForm()
    await w.get('textarea').setValue('客户投诉打印机故障，补偿 1 个月')

    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(grantLicense).toHaveBeenCalledWith(
      expect.objectContaining({
        targetType: 1,
        targetId: 88,
        contentType: 1,
        months: 1,
        reason: '客户投诉打印机故障，补偿 1 个月'
      })
    )
    expect(w.emitted('success')).toHaveLength(1)
  })
})
