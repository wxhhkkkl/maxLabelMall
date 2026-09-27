import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试配置。
 *
 * 四个 e2e 文件按故事测试先行交付（auth / cart / order / pay），
 * 每个都在其故事的实现任务之前创建、先失败、随该故事完成转绿。
 *
 * ⚠️ 运行前置（缺任一项会以难懂的方式失败）：
 *   1. 后端已启动：`java -jar yudao-server/target/yudao-server.jar`
 *      —— **不要加 `--spring.profiles.active=local`**（那会覆盖 application.yaml 里的
 *      `local,my` 并回落到本机 MySQL）
 *   2. 远程 Redis 与 CynosDB 可达（云安全组需放行开发机当前出口 IP）
 *   3. `pay_channel` 表存在 `code='mock'` 且 `app_id=1` 的启用行
 *   4. 短信验证码固定为 `9999`，故无需真实短信通道
 *
 * 失败时先按 quickstart.md §6 排查环境，再怀疑代码。
 */
export default defineConfig({
  testDir: 'e2e',
  // 先探一次后端端口：环境没起对时的原生报错（卡在登录弹层/验证码）极难懂，
  // 见 e2e/global-setup.ts
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false, // 共享后端与数据，串行运行避免相互污染
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'zh-CN',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
