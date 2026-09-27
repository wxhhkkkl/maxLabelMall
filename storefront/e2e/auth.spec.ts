import { expect, test } from '@playwright/test'

import { checkMlCheck, DEV_SMS_CODE, openLoginDialog, smsLogin, testMobile } from './helpers'

/**
 * US2 认证 —— 端到端。
 *
 * ⚠️ 本文件按**测试先行**交付：在 US2 的实现任务之前创建，先失败、随 US2 完成转绿。
 * （早期版本只有一条排在 Phase 10 的事后 e2e，被宪法原则 I 判为违规，已拆成
 * 四个按故事各一的文件。）
 *
 * 覆盖的成功标准：
 *   · SC-002 —— 新用户从进站到注册成功并处于登录态 ≤ 2 分钟
 *   · SC-011 / FR-010a —— 两种登录方式进入**同一个账号**
 */

test.describe('US2 认证', () => {
  test('新手机号走验证码登录即完成注册，顶栏出现该用户（SC-002）', async ({ page }) => {
    const mobile = testMobile()
    const started = Date.now()

    await page.goto('/')
    await smsLogin(page, mobile)

    // 顶栏从「登录 / 注册」变为该用户。
    // ⚠️ 断言的是「不再是登录入口、且**确实有名字**」，不能只认脱敏手机号：
    // 新号登录后后端会自动生成昵称（形如「用户097691」），而 FR-016 规定顶栏
    // **优先显示昵称**，脱敏手机号只是昵称缺失时的回落分支。
    const loginText = page.locator('.login')
    await expect(loginText).not.toHaveText(/登录 \/ 注册/)
    const name = ((await loginText.textContent()) ?? '').trim()
    expect(name).not.toBe('') // 冷启动只恢复令牌不恢复会员信息时，这里会渲染成空文本
    expect(name).not.toBe('登录 / 注册')

    // SC-002：≤ 2 分钟
    const seconds = ((Date.now() - started) / 1000).toFixed(1)
    expect(Date.now() - started).toBeLessThan(120_000)
    // 记录墙钟时间供人工判定（T140）
    console.log(`[SC-002] 从进站到注册成功并处于登录态共用时 ${seconds}s（要求 ≤ 120s）`)
  })

  test('未勾选协议时既不能发码也不能登录（SC-015）', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: '登录 / 注册' }).click()
    await page.locator('#loginMobile').fill(testMobile())

    // 未勾选 → 两个动作都不可执行
    await expect(page.locator('#getCodeBtn')).toBeDisabled()
    await expect(page.locator('#loginSubmit')).toBeDisabled()

    await checkMlCheck(page, '.ml-modal')
    await expect(page.locator('#getCodeBtn')).toBeEnabled()
  })

  test('**两种登录方式进入同一账号**：设密码 → 退出 → 密码登录（SC-011 / FR-010a）', async ({
    page,
  }) => {
    // 本用例要等过一段后端限流窗口，故放宽超时（见下方「获取验证码」处的说明）
    test.setTimeout(180_000)

    const mobile = testMobile()
    const password = 'Maxlabel123'

    // 第一步：验证码登录（即注册）
    await page.goto('/')
    await smsLogin(page, mobile)
    const loggedInName = await page.locator('.login').textContent()

    // 第二步：在个人中心设置密码 —— 注册时没有密码，这一步才能用密码登录。
    // ⚠️ 改密要一张 **scene 3** 的码（后端 `code` 必填且会 `useSmsCode` 实际核销），
    // 所以必须真的走一遍「获取验证码」。`page.goto` 是整页重载，也顺带覆盖了
    // 冷启动恢复会员信息 —— 会员信息没回来之前「获取验证码」是禁用的。
    await page.goto('/account')
    await page.locator('#newPassword').fill(password)
    await page.locator('#newPassword2').fill(password)

    // ⚠️ **这里必须等**：后端 `yudao.sms-code.send-frequency: 1m`，而且
    // `SmsCodeServiceImpl.createSmsCode` 查上一次发码记录时**不筛场景**
    // （`selectLastByMobile(mobile, null, null)`）—— 也就是说刚用验证码登录过
    // （scene 1）的手机号，**一分钟内**再要一张改密码（scene 3）会被拒，
    // 报「短信发送过于频繁」。这是后端的既有规则，不是前端缺陷。
    //
    // 用重试而不是固定 sleep 61s：窗口时长由后端配置决定，重试能自适应它的变化。
    await expect(async () => {
      await page.locator('#getPasswordCodeBtn').click()
      // 按钮进入倒计时 = 发码请求已回来（与 helpers.smsLogin 同一判定口径）
      await expect(page.locator('#getPasswordCodeBtn')).toBeDisabled()
    }).toPass({ timeout: 120_000, intervals: [5_000] })

    await page.locator('#setPasswordCode').fill(DEV_SMS_CODE)
    await page.locator('#setPasswordBtn').click()
    await expect(page.locator('.acct-ok')).toContainText('密码已设置')

    // 第三步：退出登录
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page.locator('.login')).toHaveText(/登录 \/ 注册/)

    // 第四步：改用**密码**登录
    await openLoginDialog(page)
    await page.locator('.switch-tab', { hasText: '密码登录' }).click()
    await checkMlCheck(page, '.ml-modal') // 切方式会重置同意，需重新勾选
    await page.locator('#loginMobile').fill(mobile)
    await page.locator('#loginPassword').fill(password)
    await page.locator('#loginSubmit').click()
    await page.locator('.ml-modal').waitFor({ state: 'detached' })

    // 关键断言：进入的是**同一个账号**
    expect(await page.locator('.login').textContent()).toBe(loggedInName)
  })

  test('密码错误时的提示不暴露账号是否存在（FR-012）', async ({ page }) => {
    await page.goto('/')
    await openLoginDialog(page)
    await page.locator('.switch-tab', { hasText: '密码登录' }).click()
    await checkMlCheck(page, '.ml-modal')
    await page.locator('#loginMobile').fill(testMobile())
    await page.locator('#loginPassword').fill('definitely-wrong')
    await page.locator('#loginSubmit').click()

    const msg = await page.locator('.login-error').textContent()
    expect(msg).toBeTruthy()
    // 不能把后端那句「账号不存在」原样透出去
    expect(msg).not.toContain('不存在')
  })

  test('关闭浏览器后重新打开仍是登录态（FR-013）', async ({ page, context }) => {
    const mobile = testMobile()
    await page.goto('/')
    await smsLogin(page, mobile)
    const name = ((await page.locator('.login').textContent()) ?? '').trim()
    expect(name).not.toBe('')

    // 新开一页（模拟重开浏览器，localStorage 仍在同一 context 内）
    const fresh = await context.newPage()
    await fresh.goto('/')
    // ⚠️ 断言的是**名字也一起恢复**，不只是"仍然是登录态"：令牌恢复是同步的，
    // 会员信息要等接口，只恢复令牌的话这里会匹配到空文本。
    await expect(fresh.locator('.login')).toHaveText(name)
    await fresh.close()
  })
})
