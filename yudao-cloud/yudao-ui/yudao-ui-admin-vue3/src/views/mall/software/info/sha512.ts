/**
 * 计算文件的 sha512（Base64），与 electron-updater 的 `sha512` 字段同算法同编码。
 *
 * ⚠️ 整包读进内存：本项目安装包在 200 MB 量级，浏览器端可承受；更大的包需要改成分片摘要。
 */
export async function sha512OfFile(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-512', buffer)
  const bytes = new Uint8Array(digest)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}
