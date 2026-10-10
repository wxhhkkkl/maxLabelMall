import { get } from '@/config/http'
import type { Banner } from '@/types'

/**
 * 横幅读取（`/app-api/promotion/banner/list`）。
 *
 * ⚠️ **`position` 必传** —— 后端没有默认值，缺了直接 400，所以这里做成必填参数。
 *
 * ⚠️ 该接口**不按 `status` 过滤**：后端只按 position 等值查询，**停用的 banner 也会返回**。
 * 响应里没有 status 字段，前端也就无从判断 —— 这是既有行为，本期**不掩盖也不绕过**。
 * 若要"停用即不展示"，那是后端的改动（见 contracts/app-api.md §5.1）。
 */

/**
 * 横幅位置 —— 与后端 `BannerPositionEnum` **逐值对应**，别凭感觉改。
 *
 * ⚠️ `MALL`(6) 是**本项目新增**的（2026-10-10）。它必须在**三处同时存在**：
 * 后端枚举、数据库字典 `promotion_banner_position`、以及这里。少任何一处都会坏：
 * 缺字典 → 运营在后台选不到；缺枚举 → 管理端提交被 `@InEnum` 400 拒绝。
 */
export const BANNER_POSITION = {
  HOME: 1,
  MALL: 6,
} as const

export async function listBanners(position: number): Promise<Banner[]> {
  const res = await get<Banner[]>('/promotion/banner/list', { position })
  return Array.isArray(res) ? res : []
}
