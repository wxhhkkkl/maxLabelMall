import { expect, type Page } from '@playwright/test'

/**
 * e2e 公共工具。
 *
 * ⚠️ 运行前置（见 playwright.config.ts 与 quickstart.md §6）：
 *   1. 后端已由 `java -jar yudao-server/target/yudao-server.jar` 启动在 48080
 *      —— **不要加 `--spring.profiles.active=local`**
 *   2. 远程 Redis 与 CynosDB 可达（云安全组放行开发机当前出口 IP）
 *   3. `pay_channel` 有 `code='mock'` 且 `app_id=1` 的启用行
 */

/** 本地短信验证码恒为 9999（yudao.sms-code.begin-code/end-code 都是 9999） */
export const DEV_SMS_CODE = '9999'

/**
 * 生成一个可复现的测试手机号。
 *
 * 用固定前缀 + 秒级时间戳，既能保证多次运行不撞号（避免"已注册"影响断言），
 * 又能在日志里一眼看出这是测试数据。**不要用随机数** —— 失败时无法复现。
 */
export function testMobile(): string {
  const stamp = String(Date.now()).slice(-8)
  return `139${stamp}`
}

/**
 * 勾选 C3 复选框。
 *
 * ⚠️ 必须点**可见的 `.ml-check-box`**，不能对 `.ml-check input` 调 `check()`：
 * 那个原生 input 是 `opacity:0; width:0; height:0` 的视觉隐藏控件（无障碍写法
 * 是对的），Playwright 判定它不可见，`check()` 会一直重试到超时 —— 表现为
 * **所有涉及勾选的 e2e 都卡死在这三行上**。
 */
export async function checkMlCheck(page: Page, scope: string): Promise<void> {
  const input = page.locator(`${scope} .ml-check input`)
  if (!(await input.isChecked())) {
    await page.locator(`${scope} .ml-check-box`).click()
  }
  await expect(input).toBeChecked()
}

/** 打开登录弹层并完成协议勾选（不勾选则发码与登录都不可用） */
export async function openLoginDialog(page: Page): Promise<void> {
  await page.getByRole('button', { name: '登录 / 注册' }).click()
  await checkMlCheck(page, '.ml-modal')
}

/**
 * 用验证码登录（手机号不存在时后端自动建号 —— 这就是注册）。
 *
 * ⚠️ 点完「获取验证码」**必须等发码请求真的回来**再提交登录。`click()` 只保证
 * 事件已派发，不等 HTTP 响应；紧接着提交登录会让 `sms-login` 与 `send-sms-code`
 * 并发，登录抢在验证码写入 Redis 之前到达，后端回「验证码不存在」。
 *
 * 可等的信号是按钮进入倒计时：`LoginDialog.onGetCode` 是在 `await sendSmsCode(...)`
 * 之后才调 `startCountdown()`，所以按钮一变「N 秒后重发」就说明码已落库。
 *
 * ⚠️ **发码可能被限流挡下**：后端 `yudao.sms-code.send-frequency: 1m`，而
 * `SmsCodeServiceImpl.createSmsCode` 查上一次发码记录时**不筛场景**
 * （`selectLastByMobile(mobile, null, null)`）。所以**同一个手机号在 1 分钟内
 * 第二次要码会被拒**（`短信发送过于频繁`），表现是按钮一直不进入倒计时。
 * 这里重试到发码成功为止：对**新手机号**第一次就成功、不会多花时间，只有真的
 * 撞上限流时才等。调用方因此不需要自己 sleep（撞限流的用例记得放宽超时）。
 */
export async function smsLogin(page: Page, mobile: string): Promise<void> {
  await openLoginDialog(page)
  await page.locator('#loginMobile').fill(mobile)
  await expect(async () => {
    await page.locator('#getCodeBtn').click()
    await expect(page.locator('#getCodeBtn')).toBeDisabled()
  }).toPass({ timeout: 120_000, intervals: [5_000] })
  await page.locator('#loginCode').fill(DEV_SMS_CODE)
  await page.locator('#loginSubmit').click()
  await page.locator('.ml-modal').waitFor({ state: 'detached' })
}

/**
 * 新建一个收货地址（下单流程的前置）。
 *
 * 行政区划**来自后台** `/system/area/tree`，测试无法预知具体省份名，因此每级
 * 都取第一个真实选项（`index: 0` 是「省份 / 城市 / 区县」占位）。选项是异步
 * 加载的，必须等每级**真的有可选值**再选 —— 直接 selectOption 会在只有占位项时
 * 选到一个空值，表现为保存时提示「请选择地区」。
 */
export async function createAddress(page: Page, name = '测试收件人'): Promise<void> {
  await page.goto('/account/address')
  // 新账号一条地址都没有，走的是空状态里的「新增地址」；已有地址时才是列表下方的
  // `#addAddress` —— 按可见文案定位才能两条路都走通。
  await page.getByRole('button', { name: '新增地址' }).click()

  await page.locator('#addrName').fill(name)
  await page.locator('#addrMobile').fill('13800001111')

  // 省份**不能盲选 index 1**：后端树的前两项是香港 / 澳门，它们是叶子
  // （`children` 为空），选下去市级只会剩「城市」占位，后面等下级选项会一路等到超时。
  await page.waitForFunction(() => document.querySelectorAll('#areaLv1 option').length > 1)
  await page.locator('#areaLv1').selectOption({ label: '北京市' })

  // 市 / 区两级由后端树带出，等它真的有可选值再选（直辖市只有两级，故不强求三级）
  for (const id of ['#areaLv2', '#areaLv3']) {
    await page.waitForFunction(
      (sel) => document.querySelectorAll(`${sel} option`).length > 1,
      id,
    )
    await page.locator(id).selectOption({ index: 1 })
  }

  await page.locator('#addrDetail').fill('测试路 1 号')
  await checkMlCheck(page, '.ml-modal')
  await page.locator('#addrSave').click()
  await page.locator('.ml-modal').waitFor({ state: 'detached' })
}
