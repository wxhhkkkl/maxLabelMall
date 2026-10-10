/** 登录/刷新令牌的响应（AppAuthLoginRespVO） */
export interface AuthLoginResp {
  userId: number
  accessToken: string
  refreshToken: string
  expiresTime: string
}

/** 会员等级（后端 `AppMemberUserInfoRespVO.Level`） */
export interface MemberLevel {
  id: number
  name: string
  level: number
  icon?: string
}

/**
 * 当前会员（`/member/user/get`）—— 后端 `AppMemberUserInfoRespVO`。
 *
 * ⚠️ 前端类型**一度只声明了 id/nickname/mobile/avatar**，而接口其实还返回
 * `email`/`sex`/`point`/`experience`/`level` —— 个人资料编辑与积分等级展示都要用它们。
 */
export interface MemberUser {
  id: number
  nickname: string
  mobile: string
  avatar?: string
  email?: string
  /** 性别：`0 未知 / 1 男 / 2 女`（后端共享枚举 `SexEnum`；该字段**没有**枚举校验） */
  sex?: number
  /** 积分余额 */
  point?: number
  /** 经验值 */
  experience?: number
  /**
   * 当前等级。**可能为 null** —— 该会员没有等级，或后台未配置等级
   * （租户 162 目前就是零配置）。展示端必须容忍，回落为「暂无等级」。
   */
  level?: MemberLevel | null
}

/**
 * 修改个人信息的请求体（`PUT /member/user/update`）。
 *
 * ⚠️ **所有字段都是可选的** —— 该 VO 上没有任何 `@NotNull`，前端**只提交改动过的字段**。
 * ⚠️ 于是**省略 = 保留原值**（更新走 MyBatis-Plus 默认的 `NOT_NULL` 策略），
 * 而**传空串 = 清空**（`@URL`/`@Email` 都对空串放行）。两者语义不同，别混。
 */
export interface ProfileUpdateReq {
  nickname?: string
  /** 头像 URL（后端带 `@URL` 校验，必须先经上传拿到 URL） */
  avatar?: string
  /** 邮箱（后端带 `@Email` + `≤50` 校验） */
  email?: string
  sex?: number
}

/** 登录方式 */
export type LoginType = 'sms' | 'password'

/** 积分记录（`GET /member/point/record/page`，后端 `AppMemberPointRecordRespVO`） */
export interface MemberPointRecord {
  id: number
  /** 事由标题 */
  title: string
  description?: string
  /** **变动值**（正 = 增加，负 = 减少），不是余额 */
  point: number
  /** 发生时间（后端 epoch 毫秒） */
  createTime: number
}
