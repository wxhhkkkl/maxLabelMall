import { del, get, post, put } from '@/config/http'
import type { Address } from '@/types'

/**
 * 收货地址（`/app-api/member/address/**`）。
 *
 * 五个字段全必填：`name` / `mobile` / `areaId` / `detailAddress` / `defaultStatus`。
 * 其中 `areaId` 由 `/system/area/tree` 三级联动选出。
 */

/** 提交用：不含 id 与 areaName（areaName 由后端按 areaId 回填） */
export type AddressForm = Omit<Address, 'id' | 'areaName'>

export function listAddress(): Promise<Address[]> {
  return get<Address[]>('/member/address/list')
}

export function getDefaultAddress(): Promise<Address | null> {
  return get<Address | null>('/member/address/get-default')
}

export function getAddress(id: number): Promise<Address> {
  return get<Address>('/member/address/get', { id })
}

export function createAddress(form: AddressForm): Promise<number> {
  return post<number>('/member/address/create', form)
}

export function updateAddress(id: number, form: AddressForm): Promise<boolean> {
  return put<boolean>('/member/address/update', { id, ...form })
}

export function deleteAddress(id: number): Promise<boolean> {
  return del<boolean>('/member/address/delete', { id })
}
