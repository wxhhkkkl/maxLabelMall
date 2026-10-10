import { get } from '@/config/http'
import type { MemberPointRecord, PageResult } from '@/types'

/**
 * 积分明细（`/app-api/member/point/record/page`）。
 *
 * ⚠️ 这里返回的是**积分变动记录**（每一笔的增减），积分**余额**在
 * `/member/user/get` 的 `point` 字段上 —— 两者别混。
 *
 * 后端还支持 `addStatus`（增/减筛选）与 `createTime` 区间，本期**不用**：
 * 记录条数少时多一排筛选器只是噪音（见 plan 对应的 R13 决策）。
 */
export function pagePointRecords(
  params: { pageNo?: number; pageSize?: number } = {},
): Promise<PageResult<MemberPointRecord>> {
  return get<PageResult<MemberPointRecord>>('/member/point/record/page', params)
}
