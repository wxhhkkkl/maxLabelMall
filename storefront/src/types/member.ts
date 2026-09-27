/** 登录/刷新令牌的响应（AppAuthLoginRespVO） */
export interface AuthLoginResp {
  userId: number
  accessToken: string
  refreshToken: string
  expiresTime: string
}

/** 当前会员（/member/user/get） */
export interface MemberUser {
  id: number
  nickname: string
  mobile: string
  avatar?: string
}

/** 登录方式 */
export type LoginType = 'sms' | 'password'
