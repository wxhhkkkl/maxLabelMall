/**
 * 占位符门禁 ①（权威、可 CI）—— `pnpm check:placeholders`
 * ============================================================================
 *
 * 在业务方把 storefront/src/data/placeholders.ts 里的占位文案全部替换为真实内容之前，
 * 本命令以非零退出码失败，**从而阻止发布**。
 *
 * ── 为什么单独成脚本，而不是放进 vitest ──────────────────────────────────────
 * 早期版本把这条断言写进了 placeholders.spec.ts，同时又要求 `pnpm test` 全绿——
 * 两者极性相反：种子未替换时门禁必须失败，而 pnpm test 必须通过。
 * **不存在同时满足两者的状态**，那个"CI 权威门禁"因此不可实现。
 * 拆成独立脚本后：`pnpm test` 只跑单元/组件测试（全程可绿），
 * 门禁是一条**可独立运行、可独立失败**的命令。两者不再互斥。
 *
 * ── 三条门禁的分工 ────────────────────────────────────────────────────────
 *   ① 本脚本          —— 种子文件里是否还有未替换的占位文案（CI 可跑，权威）
 *   ② DOM 扫描        —— 页面上是否有元素带 data-content-pending（人工，见 quickstart §4.6）
 *   ③ 视图层 grep     —— 视图是否绕过占位符直接内联了业务文案（人工，可 grep）
 *
 * ⚠️ 早期版本的门禁 ③ 曾写作 `grep -c "p(" … 归零`，**按构造不可能通过**
 * （本文件所在目录的种子文件，其注释里就含字面 `p(`；且 grep -c 数的是行不是调用数）。
 * 该条已删除，替换为上面的 ③。
 */

import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const seedPath = resolve(here, '../src/data/placeholders.ts')

if (!existsSync(seedPath)) {
  console.error(`✗ 门禁①：找不到种子文件 ${seedPath}`)
  process.exit(1)
}

const src = readFileSync(seedPath, 'utf8')

// 统计仍未替换的占位调用 p('…')。注意用出现次数而非行数。
const pending = src.match(/\bp\(\s*'/g) ?? []
// 同时扫 `[[ ]]` 字面量（若有人直接把占位文本写死在别处）
const literal = src.match(/\[\[/g) ?? []
// RENDERED 必须存在且不得再叫 SEED（后者是历史上会把不渲染内容卷进来的那个名字）
const hasRendered = /export\s+const\s+RENDERED\s*=/.test(src)
const hasStaleSeed = /export\s+const\s+SEED\s*=/.test(src)

const problems = []
if (pending.length > 0) problems.push(`仍有 ${pending.length} 处 p('…') 未替换为真实文案`)
if (literal.length > 0) problems.push(`发现 ${literal.length} 处 [[ ]] 占位字面量`)
if (!hasRendered) problems.push('缺少 RENDERED 导出（占位扫描的根对象）')
if (hasStaleSeed) problems.push('仍存在旧名 SEED（会把不渲染内容卷入扫描范围）')

if (problems.length > 0) {
  console.error('✗ 门禁① 未通过 —— 站点不得发布：')
  for (const p of problems) console.error(`   · ${p}`)
  console.error('\n替换流程见 src/data/placeholders.ts 头部注释：只改该文件的字符串（把')
  console.error("p('…') 改成 '…'），视图一行都不用动。")
  process.exit(1)
}

console.log('✓ 门禁① 通过：种子文件已无占位文案，可以发布。')
