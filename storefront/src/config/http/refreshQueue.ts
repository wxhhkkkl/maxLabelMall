/**
 * 401 无感刷新的去重队列。
 *
 * 多个请求同时拿到 401 时，**只刷新一次**，其余请求排队等待，刷新成功后
 * 一并重放各自的原始请求。刷新失败时所有等待者都失败，且**不复用失败结果**
 * —— 下一个请求会重新触发一次刷新。
 */
export interface RefreshQueue {
  submit<T>(original: () => Promise<T>): Promise<T>
  /** 当前是否正在刷新（测试与调试用） */
  readonly isRefreshing: boolean
}

export function createRefreshQueue(refresh: () => Promise<void>): RefreshQueue {
  // 进行中的刷新；null 表示当前没有刷新在跑
  let current: Promise<void> | null = null

  return {
    get isRefreshing(): boolean {
      return current !== null
    },

    submit<T>(original: () => Promise<T>): Promise<T> {
      if (current === null) {
        current = refresh().finally(() => {
          // 无论成功失败都复位，否则后续请求会永远排队
          current = null
        })
        // 第一个提交者之外的等待者不消费该 promise 的 rejection，
        // 这里吸收一次以免产生 unhandled rejection 告警
        current.catch(() => undefined)
      }
      // 刷新成功后重放原始请求；刷新失败时 then 不会执行，rejection 直接透传
      return current.then(() => original())
    },
  }
}
