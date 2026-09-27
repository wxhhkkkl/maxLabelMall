import { describe, expect, it } from 'vitest'

import { telHref } from './tel'

/**
 * `tel:` 链接的生成。
 *
 * ⚠️ 关键约束：**占位文案不能变成可拨号链接**。业务方提供号码之前，热线是
 * `[[企业采购热线待填写]]` —— 照直拼会得到 `tel:[[...]]`，既打不通、又像是
 * 已经在提供该服务（FR-054 禁止发布虚构联系方式）。
 */
describe('telHref', () => {
  it('把常见写法清理成可拨号的号码', () => {
    expect(telHref('010-8888 6666')).toBe('tel:01088886666')
    expect(telHref('400-800-1234')).toBe('tel:4008001234')
    expect(telHref('+86 138 0000 8888')).toBe('tel:+8613800008888')
  })

  it('**占位文案不给 `tel:` 链接**（FR-054）', () => {
    expect(telHref('[[企业采购热线待填写]]')).toBe('')
    expect(telHref('电话待填写')).toBe('')
    expect(telHref('邮箱待填写')).toBe('')
  })

  it('位数不足的不当作电话号码（避免把「待填写」里的数字当真）', () => {
    expect(telHref('12345')).toBe('')
  })

  it('空值给空串', () => {
    expect(telHref('')).toBe('')
    expect(telHref(undefined)).toBe('')
    expect(telHref(null)).toBe('')
  })

  it('邮箱不会被误当成号码', () => {
    expect(telHref('support@example.com')).toBe('')
  })
})
