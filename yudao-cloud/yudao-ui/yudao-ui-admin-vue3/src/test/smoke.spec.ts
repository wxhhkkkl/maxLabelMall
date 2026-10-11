import { describe, expect, it } from 'vitest'

import { handleTree } from '@/utils/tree'

/**
 * 测试栈 smoke（特性 003 · 任务 T247）。
 *
 * 它**不是**在给 `handleTree` 做覆盖率 —— 那是上游既有代码，本特性一行没改。
 * 它证明的是这台"车"能发动：测试文件能被发现、`@` 别名能解析、TypeScript 能编译、
 * jsdom 环境能起来。三点缺一，后面 T274 / T261 / T282 的测试都会以看不懂的方式失败。
 */
describe('管理端测试栈 smoke', () => {
  it('能解析 @ 别名并运行 TypeScript', () => {
    expect(typeof handleTree).toBe('function')
  })

  it('能跑一个真实的断言（jsdom 环境正常）', () => {
    const flat = [
      { id: 1, parentId: 0, name: '碳带' },
      { id: 2, parentId: 1, name: '蜡基' },
    ]
    const tree = handleTree(flat, 'id', 'parentId')
    expect(tree).toHaveLength(1)
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].name).toBe('蜡基')
  })
})
