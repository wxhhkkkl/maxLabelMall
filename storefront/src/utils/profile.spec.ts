import { describe, expect, it } from 'vitest'

import { SEX_LABELS, buildProfileUpdate, sexLabel, validateProfile } from './profile'

/**
 * 「编辑资料」的校验与请求体构造。
 *
 * 口径全部来自后端 `AppMemberUserUpdateReqVO` + 本轮澄清：
 *   · 后端**四个字段都不是必填**（无 `@NotNull`）→ 前端**只提交改动过的字段**；
 *   · **省略 = 保留原值**（`NOT_NULL` 更新策略），**空串 = 清空**（`@URL`/`@Email` 都放行空串）；
 *   · **昵称必填**（清空会让顶栏回落成脱敏手机号，FR-016）；**邮箱可清空**；头像不提供移除。
 */

const CURRENT = {
  nickname: '张三',
  avatar: 'https://img.example.com/a.png',
  email: 'old@example.com',
  sex: 1,
}

describe('validateProfile —— 表单校验', () => {
  it('昵称必填：空或纯空格都不通过', () => {
    expect(validateProfile({ nickname: '' }).ok).toBe(false)
    expect(validateProfile({ nickname: '   ' }).ok).toBe(false)
    expect(validateProfile({ nickname: '张三' }).ok).toBe(true)
  })

  it('邮箱格式不对要拦下', () => {
    const r = validateProfile({ nickname: '张三', email: 'abc' })
    expect(r.ok).toBe(false)
    expect(r.message).toContain('邮箱')
  })

  it('邮箱长度上限 50（与后端 @Size(max=50) 对齐）', () => {
    const long = `${'a'.repeat(45)}@ex.com` // 52 字符
    expect(validateProfile({ nickname: '张三', email: long }).ok).toBe(false)
    expect(validateProfile({ nickname: '张三', email: `${'a'.repeat(40)}@ex.com` }).ok).toBe(true)
  })

  it('**邮箱为空是合法的**（可清空）', () => {
    expect(validateProfile({ nickname: '张三', email: '' }).ok).toBe(true)
    expect(validateProfile({ nickname: '张三' }).ok).toBe(true)
  })
})

describe('buildProfileUpdate —— 只提交改动过的字段', () => {
  it('**什么都没改 → 空对象**（不发多余字段，避免把没改的值用陈旧值写回）', () => {
    expect(buildProfileUpdate(CURRENT, { ...CURRENT })).toEqual({})
  })

  it('**只改昵称 → body 只有 nickname**（这是 FR-011b 的核心约束）', () => {
    expect(buildProfileUpdate(CURRENT, { ...CURRENT, nickname: '李四' })).toEqual({ nickname: '李四' })
  })

  it('改了头像与性别 → 只有这两项', () => {
    const next = { ...CURRENT, avatar: 'https://img.example.com/b.png', sex: 2 }
    expect(buildProfileUpdate(CURRENT, next)).toEqual({
      avatar: 'https://img.example.com/b.png',
      sex: 2,
    })
  })

  it('**邮箱清空要提交空串**（不是省略 —— 省略等于保留原值，那就清不掉）', () => {
    expect(buildProfileUpdate(CURRENT, { ...CURRENT, email: '' })).toEqual({ email: '' })
  })

  it('邮箱首尾空格去掉后再比对（避免只有空格差异就发请求）', () => {
    expect(buildProfileUpdate(CURRENT, { ...CURRENT, email: ' old@example.com ' })).toEqual({})
  })

  it('性别从"未设置"改成具体值要提交', () => {
    const current = { ...CURRENT, sex: undefined }
    expect(buildProfileUpdate(current, { ...CURRENT, sex: 1 })).toEqual({ sex: 1 })
  })
})

describe('性别映射（后端 SexEnum：0 未知 / 1 男 / 2 女）', () => {
  it('三个取值都能映射', () => {
    expect(sexLabel(0)).toBe('未知')
    expect(sexLabel(1)).toBe('男')
    expect(sexLabel(2)).toBe('女')
  })

  it('未设置（undefined/null）与未知值回落为空串 —— 不猜', () => {
    expect(sexLabel(undefined)).toBe('')
    expect(sexLabel(null)).toBe('')
    expect(sexLabel(99)).toBe('')
  })

  it('可选项列表与后端枚举一致', () => {
    expect(SEX_LABELS).toEqual([
      { value: 0, label: '未知' },
      { value: 1, label: '男' },
      { value: 2, label: '女' },
    ])
  })
})
