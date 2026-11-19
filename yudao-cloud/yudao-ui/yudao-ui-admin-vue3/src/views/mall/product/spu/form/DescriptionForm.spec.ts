/**
 * 商品详情富文本（2026-11-18）
 *
 * 所有者要求：商品详情里**不要**「视频」与「网络图片」，只保留本地上传图片。
 * 这里钉住传给编辑器的工具栏排除项；wangEditor 内部按 excludeKeys 过滤菜单
 * （分组里的菜单项也能单独排除，insertImage 就是「网络图片」）。
 */
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DescriptionForm from './DescriptionForm.vue'

vi.mock('@/components/Editor', () => ({
  Editor: {
    name: 'Editor',
    props: ['modelValue', 'readonly', 'excludeKeys'],
    template: '<div class="editor-stub" />'
  }
}))

// DescriptionForm 的校验规则用到 unplugin-auto-import 注入的 `required`，vitest 下手工补上
;(globalThis as any).required = { required: true, message: '必填', trigger: 'blur' }

describe('商品详情编辑器工具栏', () => {
  it('排除「视频」与「网络图片」，保留本地上传图片', async () => {
    const w = mount(DescriptionForm, { props: { propFormData: { description: '' } } })
    await flushPromises()
    const editor = w.findComponent({ name: 'Editor' })
    expect(editor.exists()).toBe(true)
    expect(editor.props('excludeKeys')).toEqual(['group-video', 'insertImage'])
  })
})
