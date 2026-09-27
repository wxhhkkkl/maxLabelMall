import { get } from '@/config/http'
import type { Area } from '@/types'

/**
 * 行政区划树（`/app-api/system/area/tree`）。
 *
 * ⚠️ **必须来自后台，不得在前端内置**（宪法原则 III）：内置的行政区划会过期
 * （撤县设区、更名），且与后台校验的地区编号对不上，导致地址保存失败。
 */
export function getAreaTree(): Promise<Area[]> {
  return get<Area[]>('/system/area/tree')
}
