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
    // ⚠️ **给 e2e 覆盖租户**：`.env` 里前台默认跑租户 **162（御旺宸发）**，
    // 但 162 目前只有一套「【示例】」占位商品，撑不起这里 22 条用例
    // （它们要商品、订单、可用的券）。所以 e2e 明确指回**有完整种子数据的租户 1**。
    //
    // 这是 `set VAR=value&&` 的 Windows 写法（cmd.exe；本项目开发环境是 Windows）。
    // Vite 会把 `process.env` 里 `VITE_` 前缀的变量并入 `import.meta.env`，
    // 因此这里设的值**优先于 `.env` 文件**。
    command: 'set VITE_TENANT_ID=1&& pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
