import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SUCCESS_CODE, TERMINAL, TENANT_ID } from './config'
import { buildHeaders, errorMessageOf, isSuccess, isUnauthorized } from './helpers'
import { createRefreshQueue } from './refreshQueue'

describe('buildHeaders —— 请求头注入', () => {
  it('tenant-id 被**无条件**注入，不依赖任何开关', () => {
    // 后端在缺 tenant-id 时返回 HTTP 200 + body.code=400，
    // 表现为「页面空数据却不报错」，是本站最难排查的故障
    expect(buildHeaders()['tenant-id']).toBe(TENANT_ID)
    expect(buildHeaders('tk')['tenant-id']).toBe(TENANT_ID)
  })

  it('terminal 固定为 20（H5）', () => {
    expect(buildHeaders().terminal).toBe(String(TERMINAL))
  })

  it('有令牌时带 Bearer 前缀（后端容忍缺失，但统一带）', () => {
    expect(buildHeaders('tk-1').Authorization).toBe('Bearer tk-1')
  })

  it('无令牌时不带 Authorization，而不是带一个空值', () => {
    expect(buildHeaders('').Authorization).toBeUndefined()
    expect(buildHeaders(undefined).Authorization).toBeUndefined()
  })
})

describe('isSuccess —— 按 body.code 判定，不看 HTTP 状态', () => {
  it('code 为 0 即成功', () => {
    expect(isSuccess({ code: SUCCESS_CODE, data: null, msg: '' })).toBe(true)
  })

  // 这条是本站最危险的假成功场景
  it('HTTP 200 但 body.code=400（租户缺失）必须判为失败', () => {
    expect(isSuccess({ code: 400, data: null, msg: '请求的租户标识未传递，请进行排查' })).toBe(false)
  })

  it('其他业务错误码判为失败', () => {
    expect(isSuccess({ code: 401 })).toBe(false)
    expect(isSuccess({ code: 500 })).toBe(false)
  })

  it('响应体不是对象或缺少 code 时判为失败（不静默当成功）', () => {
    expect(isSuccess(undefined)).toBe(false)
    expect(isSuccess(null)).toBe(false)
    expect(isSuccess('<html>502</html>')).toBe(false)
    expect(isSuccess({ data: {} })).toBe(false)
  })

  it('管理端的 200 不算成功 —— app 端的成功码只有 0', () => {
    expect(isSuccess({ code: 200 })).toBe(false)
  })
})

describe('errorMessageOf —— 取后端文案，缺失时回落', () => {
  it('优先用后端 msg', () => {
    expect(errorMessageOf({ code: 400, msg: '请求的租户标识未传递，请进行排查' })).toBe(
      '请求的租户标识未传递，请进行排查',
    )
  })
  it('无 msg 时用调用方给的回落文案', () => {
    expect(errorMessageOf({ code: 500 }, '下单失败，请重试')).toBe('下单失败，请重试')
  })
  it('两者都没有时给出通用文案，不返回空串', () => {
    expect(errorMessageOf(undefined).length).toBeGreaterThan(0)
  })
})

describe('isUnauthorized', () => {
  it('401 需要刷新令牌', () => {
    expect(isUnauthorized({ code: 401 })).toBe(true)
  })
  it('其他错误码不需要', () => {
    expect(isUnauthorized({ code: 400 })).toBe(false)
    expect(isUnauthorized({ code: 500 })).toBe(false)
    expect(isUnauthorized(undefined)).toBe(false)
  })
})

describe('createRefreshQueue —— 并发 401 只刷新一次并重放', () => {
  let refresh: ReturnType<typeof vi.fn>
  let resolveRefresh: () => void

  beforeEach(() => {
    refresh = vi.fn(
      () =>
        new Promise<void>((res) => {
          resolveRefresh = res
        }),
    )
  })

  it('三个并发请求只触发一次刷新', async () => {
    const q = createRefreshQueue(refresh as unknown as () => Promise<void>)
    const replay = vi.fn().mockResolvedValue('ok')

    const all = Promise.all([q.submit(replay), q.submit(replay), q.submit(replay)])
    resolveRefresh()
    await all

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(replay).toHaveBeenCalledTimes(3)
  })

  it('刷新成功后重放原请求', async () => {
    const q = createRefreshQueue(refresh as unknown as () => Promise<void>)
    const replay = vi.fn().mockResolvedValue(42)

    const p = q.submit(replay)
    resolveRefresh()

    await expect(p).resolves.toBe(42)
    expect(replay).toHaveBeenCalledTimes(1)
  })

  it('刷新期间 isRefreshing 为 true，结束后复位', async () => {
    const q = createRefreshQueue(refresh as unknown as () => Promise<void>)
    expect(q.isRefreshing).toBe(false)

    const p = q.submit(vi.fn().mockResolvedValue(1))
    expect(q.isRefreshing).toBe(true)

    resolveRefresh()
    await p
    expect(q.isRefreshing).toBe(false)
  })

  it('刷新失败：所有等待者都失败，且不重放', async () => {
    const failing = vi.fn(() => Promise.reject(new Error('refresh failed')))
    const q = createRefreshQueue(failing as unknown as () => Promise<void>)
    const replay = vi.fn().mockResolvedValue('ok')

    const results = await Promise.allSettled([q.submit(replay), q.submit(replay)])

    expect(results.every((r) => r.status === 'rejected')).toBe(true)
    expect(replay).not.toHaveBeenCalled()
    expect(failing).toHaveBeenCalledTimes(1)
    // 失败后必须复位，否则后续请求会永远排队
    expect(q.isRefreshing).toBe(false)
  })

  it('刷新失败之后，新的请求会重新触发一次刷新（不复用失败结果）', async () => {
    const failing = vi.fn(() => Promise.reject(new Error('boom')))
    const q = createRefreshQueue(failing as unknown as () => Promise<void>)

    await Promise.allSettled([q.submit(vi.fn().mockResolvedValue(1))])
    await Promise.allSettled([q.submit(vi.fn().mockResolvedValue(1))])

    expect(failing).toHaveBeenCalledTimes(2)
  })
})
