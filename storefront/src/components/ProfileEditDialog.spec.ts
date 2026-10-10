import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const updateProfile = vi.fn()
vi.mock('@/api/member', () => ({
  updateProfile: (...a: unknown[]) => updateProfile(...a),
}))
vi.mock('@/api/upload', () => ({ uploadFile: vi.fn() }))

import type { MemberUser } from '@/types'

const ProfileEditDialog = (await import('./ProfileEditDialog.vue')).default

const MEMBER = {
  id: 1,
  nickname: '张三',
  mobile: '13800008888',
  avatar: 'https://img.example.com/a.png',
  email: 'old@example.com',
  sex: 1,
}

function mountDialog(member: MemberUser = MEMBER) {
  return mount(ProfileEditDialog, { props: { open: true, member } })
}

beforeEach(() => {
  updateProfile.mockReset()
  updateProfile.mockResolvedValue(true)
})

/**
 * 编辑资料弹层。
 *
 * ⚠️ 本组断言的核心是 **请求体只含改动过的字段** —— 后端四个字段都不是必填，
 * 把没改的也发回去会用陈旧值覆盖别处的改动（见 utils/profile 的说明）。
 */
describe('ProfileEditDialog —— 回填', () => {
  it('打开时用当前会员回填昵称/邮箱/性别/头像', async () => {
    const w = mountDialog()
    await flushPromises()

    expect((w.get('#profileNickname').element as HTMLInputElement).value).toBe('张三')
    expect((w.get('#profileEmail').element as HTMLInputElement).value).toBe('old@example.com')
    expect(w.get('.av-img').attributes('src')).toBe('https://img.example.com/a.png')
    // 性别 1 = 男，select 的选中项
    expect((w.get('#profileSex').element as HTMLSelectElement).value).toBe('1')
  })

  it('没有等级/积分的会员也能正常打开（字段缺失不炸）', async () => {
    const w = mountDialog({ id: 2, nickname: '李四', mobile: '13900000000' })
    await flushPromises()
    expect((w.get('#profileNickname').element as HTMLInputElement).value).toBe('李四')
    expect((w.get('#profileEmail').element as HTMLInputElement).value).toBe('')
  })
})

describe('ProfileEditDialog —— 提交', () => {
  it('**只改昵称 → body 只有 nickname**', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileNickname').setValue('李四')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith({ nickname: '李四' })
    expect(Object.keys(updateProfile.mock.calls[0][0] as object)).toEqual(['nickname'])
  })

  it('**邮箱清空 → 提交空串**（不是省略，否则清不掉）', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileEmail').setValue('')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith({ email: '' })
  })

  it('改性别 → 提交 sex', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileSex').setValue('2')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).toHaveBeenCalledWith({ sex: 2 })
  })

  it('**没做任何修改 → 不发请求**（省一次无意义的写回）', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('**邮箱非法 → 不发请求**，给出格式提示', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileEmail').setValue('abc')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).not.toHaveBeenCalled()
    expect(w.text()).toContain('邮箱格式不正确')
  })

  it('**昵称清空 → 不发请求**（昵称是展示名，空会让顶栏回落成手机号）', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileNickname').setValue('  ')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(updateProfile).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写昵称')
  })

  it('成功 → emit saved 与 close', async () => {
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileNickname').setValue('李四')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(w.emitted('saved')).toBeTruthy()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('失败 → **原样透出后端文案、弹层不关**（用户可改后重试）', async () => {
    updateProfile.mockRejectedValue({ message: '邮箱格式不正确' })
    const w = mountDialog()
    await flushPromises()
    await w.get('#profileEmail').setValue('ok@example.com')
    await w.get('#profileSave').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('邮箱格式不正确')
    expect(w.emitted('close')).toBeFalsy()
    expect(w.emitted('saved')).toBeFalsy()
  })
})
