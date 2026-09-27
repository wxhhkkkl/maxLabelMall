import { request } from '@playwright/test'

/**
 * e2e 的运行前置检查（T139）。
 *
 * 不加这一层时，环境没起对的失败方式**非常难懂**：第一个用例会卡在
 * 「登录弹层没关」或「验证码不存在」上，看起来像前端 bug，其实是后端没在跑。
 * 这里显式探一次端口，把真正的病因连同排查步骤一起抛出来。
 *
 * ⚠️ 探的是**有没有响应**，不是响应码：401/403 也说明 48080 上有服务在跑。
 * 只有连不上才判失败。默认地址可用 `E2E_BACKEND_URL` 覆盖（便于在别的端口跑）。
 */
const BACKEND = process.env.E2E_BACKEND_URL ?? 'http://localhost:48080'

export default async function globalSetup(): Promise<void> {
  const ctx = await request.newContext({
    baseURL: BACKEND,
    extraHTTPHeaders: { 'tenant-id': '1' },
  })
  try {
    // 任意一个 /app-api 端点即可 —— 只为确认端口上有服务
    await ctx.get('/app-api/system/area/tree', { timeout: 5_000 })
  } catch (e) {
    throw new Error(
      [
        '',
        `✗ e2e 前置检查失败：连不上后端 ${BACKEND}（${(e as Error).message}）`,
        '',
        'e2e 需要后端在 48080 上就绪，请先按以下步骤排查：',
        '  1. 启动后端：在 yudao-cloud/ 下执行',
        '       java -jar yudao-server/target/yudao-server.jar',
        '     ⚠️ **不要**加 --spring.profiles.active=local（那会覆盖面里的 local,my）',
        '  2. 远程 Redis 与 CynosDB 可达（云安全组需放行开发机当前出口 IP）',
        '  3. pay_channel 表存在 code=\'mock\' 且 app_id=1 的启用行',
        '  4. 冷启动约 63 秒，等 Tomcat 报 "Started YudaoServerApplication" 再跑',
        '',
        '详细排查见 specs/001-mall-storefront-integration/quickstart.md §6。',
        '若后端在别的地址，用 E2E_BACKEND_URL 覆盖。',
        '',
      ].join('\n'),
      // 保留原始错误：`ECONNREFUSED` 之类的底层原因对排查有用
      { cause: e },
    )
  } finally {
    await ctx.dispose()
  }
}
