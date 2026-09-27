/** 请求层常量。**成功码是 app 端的 0**，不是管理端的 200。 */
export const SUCCESS_CODE = 0

export const BASE_URL = `${import.meta.env.VITE_BASE_URL ?? ''}${import.meta.env.VITE_API_URL ?? ''}`
export const TIMEOUT = 30_000

/** 单租户固定值。**每个请求都必须带**——见 helpers.buildHeaders 的说明。 */
export const TENANT_ID = import.meta.env.VITE_TENANT_ID || '1'
/** H5 终端标识，影响会员注册来源与下单落库的 terminal 字段 */
export const TERMINAL = 20
