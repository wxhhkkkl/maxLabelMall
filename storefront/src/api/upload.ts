import { post } from '@/config/http'

/**
 * 文件上传（`POST /app-api/infra/file/upload`）。
 *
 * 本期只服务于**头像** —— 后端 `PUT /member/user/update` 的 `avatar` 带 `@URL` 校验，
 * 所以必须先上传拿到 URL 再提交。返回的字符串**就是文件的访问 URL**。
 *
 * ⚠️ **不要手写 `Content-Type`**：axios 传 `FormData` 时会自动补上带 boundary 的
 * `multipart/form-data`；手写成不带 boundary 的那串会把上传打坏（后端收不到文件）。
 *
 * ⚠️ 体积/格式上限**由后台的文件上传配置决定**，前端不自己编一个数字 ——
 * 超限时把后端的文案透出来即可。
 */
export function uploadFile(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return post<string>('/infra/file/upload', form)
}
