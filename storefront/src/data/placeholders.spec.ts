import { describe, expect, it } from 'vitest'

import { PRIVACY_POLICY_CHECKLIST, PENDING_ATTR, RENDERED, isPending } from './placeholders'

/**
 * 占位符机制的**判定契约**。
 *
 * ⚠️ 本文件**不**断言 `hasPendingContent() === false`。
 * 早期版本把它当作「门禁①」写在这里，同时又要求 `pnpm test` 全绿 ——
 * 两者极性相反（种子未替换时门禁必须失败、pnpm test 必须通过），
 * **不存在同时满足的状态**。
 *
 * 现在分工是：
 *   · 本文件          —— 只断言 `isPending()` 的判定契约（全程可绿）
 *   · `pnpm check:placeholders` —— 门禁①，可独立运行、可独立失败
 *   · DOM 扫描 / 视图层 grep     —— 门禁② ③，见 quickstart §4.6
 */

describe('isPending —— 占位文案判定', () => {
  it('被 [[ ]] 包裹的字符串是占位', () => {
    expect(isPending('[[公司简介待补充]]')).toBe(true)
    expect(isPending('[[50,000+]]')).toBe(true)
    // 空占位也算占位（免得写成 [[]] 就被漏掉）
    expect(isPending('[[]]')).toBe(true)
  })

  it('真实文案不是占位', () => {
    expect(isPending('赋签科技（北京）有限公司')).toBe(false)
    expect(isPending('')).toBe(false)
  })

  it('只在**两端**同时出现才算占位 —— 文案中间出现方括号不代表未替换', () => {
    // 真实文案里可能出现方括号（如「标签 [A4] 规格」），不得误判
    expect(isPending('标签 [A4] 规格')).toBe(false)
    expect(isPending('[[开头有但结尾没有')).toBe(false)
    expect(isPending('结尾有但开头没有]]')).toBe(false)
  })

  it('非字符串一律不是占位（数字/布尔/对象字段不参与判定）', () => {
    expect(isPending(undefined)).toBe(false)
    expect(isPending(null)).toBe(false)
    expect(isPending(0)).toBe(false)
    expect(isPending(false)).toBe(false)
    expect(isPending({})).toBe(false)
  })
})

describe('RENDERED —— 只含会渲染的内容', () => {
  it('不渲染的核对清单不在 RENDERED 内', () => {
    // 若把 PRIVACY_POLICY_CHECKLIST 放进 RENDERED，门禁① 会被永久污染
    expect(Object.keys(RENDERED)).not.toContain('privacyChecklist')
    // 它是独立导出，且是纯字符串数组（未包裹 [[ ]]，本就不该参与占位判定）
    expect(Array.isArray(PRIVACY_POLICY_CHECKLIST)).toBe(true)
    expect(PRIVACY_POLICY_CHECKLIST.every((x) => typeof x === 'string' && !isPending(x))).toBe(true)
  })

  it('六类页面内容与站点元信息都在 RENDERED 内', () => {
    for (const key of [
      'siteMeta',
      'about',
      'news',
      'contact',
      'jobs',
      'changelog',
      'templates',
      'enterprise',
      'legal',
      'inheritedClaims',
    ]) {
      expect(Object.keys(RENDERED)).toContain(key)
    }
  })

  it('页面从 RENDERED 取名占位标记属性，而不是手写', () => {
    // 属性名必须与组件契约一致
    expect(PENDING_ATTR).toBe('data-content-pending')
  })
})
