import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const createOrderItemComment = vi.fn()
vi.mock('@/api/tradeComment', () => ({
  createOrderItemComment: (...a: unknown[]) => createOrderItemComment(...a),
}))
vi.mock('@/api/upload', () => ({ uploadFile: vi.fn() }))

const CommentCreateDialog = (await import('./CommentCreateDialog.vue')).default

const ITEM = { id: 456, spuName: '三防热敏标签纸', picUrl: 'http://x/a.png' }

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(CommentCreateDialog, {
    props: { open: true, item: ITEM, ...props },
  })
}

/** 填一份合法表单 */
async function fillValid(w: ReturnType<typeof mount>, content = '很好用') {
  await w.get('#commentContent').setValue(content)
  await flushPromises()
}

beforeEach(() => {
  createOrderItemComment.mockReset()
  createOrderItemComment.mockResolvedValue(1)
})

/**
 * 写评价弹层（FR-074 / FR-075 / FR-077 / FR-079）。
 *
 * ⚠️ **本组用例刻意断言请求体，不只断言界面**。本项目吃过"单测只断言 UI、
 * 于是请求体发错了也没人发现"的亏 —— 写操作必须钉住发出去的每一个字段名与取值，
 * 因为后端就是按这些名字读的，错一个字母就是"提交了但没生效"。
 */
describe('CommentCreateDialog —— 请求体', () => {
  it('提交的 body 字段名与取值精确匹配后端契约', async () => {
    const w = mountDialog()
    await fillValid(w)
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(createOrderItemComment).toHaveBeenCalledWith({
      orderItemId: 456,
      descriptionScores: 5,
      benefitScores: 5,
      content: '很好用',
      picUrls: [],
      anonymous: false,
    })
  })

  it('勾了匿名 → anonymous 为 true', async () => {
    const w = mountDialog()
    await fillValid(w)
    await w.get('#commentAnonymous').setValue(true)
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(createOrderItemComment.mock.calls[0]![0]).toMatchObject({ anonymous: true })
  })

  it('改了评分 → 按改后的分数提交', async () => {
    const w = mountDialog()
    await fillValid(w)
    // 第一组星的第 3 颗（商品质量）
    await w.findAll('.ml-rate')[0]!.findAll('.ml-star')[2]!.trigger('click')
    await flushPromises()
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(createOrderItemComment.mock.calls[0]![0]).toMatchObject({ descriptionScores: 3 })
  })
})

describe('CommentCreateDialog —— 提交前校验（FR-075）', () => {
  it('内容为空时不发请求，并指出是哪一项', async () => {
    const w = mountDialog()
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(createOrderItemComment).not.toHaveBeenCalled()
    expect(w.text()).toContain('评价内容')
  })

  it('内容只有空白也算空，不发请求', async () => {
    const w = mountDialog()
    await fillValid(w, '    ')
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()
    expect(createOrderItemComment).not.toHaveBeenCalled()
  })
})

describe('CommentCreateDialog —— 失败与成功', () => {
  it('**后端拒绝时显示的文案与后端返回的那句一致**（不得被通用文案替换）', async () => {
    createOrderItemComment.mockRejectedValue({ message: '创建交易订单项的评价失败，订单不是【已完成】状态' })
    const w = mountDialog()
    await fillValid(w)
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('创建交易订单项的评价失败，订单不是【已完成】状态')
    // 失败不关弹层，用户能重试
    expect(w.find('#commentSubmit').exists()).toBe(true)
  })

  it('成功后 emit submitted，**但先不关弹层** —— 要先把"审核通过后展示"给用户看到', async () => {
    const w = mountDialog()
    await fillValid(w)
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    expect(w.emitted('submitted')).toHaveLength(1)
    // Q8：评价默认不可见（要运营审核才展示），不说清楚用户会以为没提交上、再提一次
    expect(w.text()).toContain('审核通过后展示')
    // 立即关闭 = 用户根本看不到上面那句，等于没提示
    expect(w.emitted('close')).toBeFalsy()
  })

  it('看到提示后点「知道了」才关闭', async () => {
    const w = mountDialog()
    await fillValid(w)
    await w.get('#commentSubmit').trigger('click')
    await flushPromises()

    await w.get('#commentDone').trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('每次打开都重置表单（不留上一次的输入）', async () => {
    const w = mountDialog({ open: false })
    await w.setProps({ open: true })
    await fillValid(w, '第一次')
    await w.setProps({ open: false })
    await w.setProps({ open: true })
    await flushPromises()

    expect((w.get('#commentContent').element as HTMLTextAreaElement).value).toBe('')
  })
})
