/**
 * 富文本「上传图片」的文件选择（2026-11-26）
 *
 * 背景：沿用 wangEditor 自带实现时，部分客户在 Edge 下点「上传图片」没有任何反应 ——
 * 它创建一个 `display:none` 的 input 再程序化 click()，Edge 会静默忽略这次点击。
 *
 * 这里钉住三条口径：
 *  1. 建出来的 input **不能**是 display:none / visibility:hidden，且必须挂到 body 上；
 *  2. change 必须在 click() **之前**绑好（Edge 下 click() 阻塞到对话框关闭，
 *     先 click 后绑会丢掉选中事件 —— 客户侧现象就是「能选图片，选完没反应」）；
 *  3. 选完 / 取消后把 input 从 DOM 移除，不留垃圾节点；
 *     选中结果为空时也要回调，好让页面给出可见提示而不是继续静默。
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { pickImageFiles } from './pickImageFiles'

const makeFile = (name: string) => new File(['x'], name, { type: 'image/png' })

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('pickImageFiles', () => {
  it('input 保留在渲染树里（非 display:none），并支持多选图片', () => {
    pickImageFiles(() => {})
    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement
    expect(input).toBeTruthy()
    expect(input.accept).toBe('image/*')
    expect(input.multiple).toBe(true)
    // 关键：Edge 会忽略 display:none 元素上的程序化 click
    expect(input.style.display).not.toBe('none')
    expect(input.style.visibility).not.toBe('hidden')
    expect(input.style.opacity).toBe('0')
  })

  it('先绑 change 再 click', () => {
    const order: string[] = []
    const rawAdd = EventTarget.prototype.addEventListener
    vi.spyOn(EventTarget.prototype, 'addEventListener').mockImplementation(function (
      this: EventTarget,
      type: string,
      ...rest: any[]
    ) {
      order.push(`on:${type}`)
      return rawAdd.call(this, type, ...(rest as [any]))
    } as any)
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {
      order.push('click')
    })

    pickImageFiles(() => {})

    expect(order).toContain('on:change')
    expect(order.indexOf('on:change')).toBeLessThan(order.indexOf('click'))
  })

  it('选中文件后回调拿到文件，并把 input 移出 DOM', () => {
    const onPick = vi.fn()
    pickImageFiles(onPick)
    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement
    const files = [makeFile('a.png'), makeFile('b.png')]
    Object.defineProperty(input, 'files', { value: files, configurable: true })

    input.dispatchEvent(new Event('change'))

    expect(onPick).toHaveBeenCalledTimes(1)
    expect(onPick.mock.calls[0][0]).toEqual(files)
    expect(document.body.contains(input)).toBe(false)
  })

  it('浏览器交回空列表时也要回调，便于页面提示而不是静默', () => {
    const onPick = vi.fn()
    pickImageFiles(onPick)
    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [], configurable: true })

    input.dispatchEvent(new Event('change'))

    expect(onPick).toHaveBeenCalledTimes(1)
    expect(onPick.mock.calls[0][0]).toEqual([])
    expect(document.body.contains(input)).toBe(false)
  })

  it('取消选择也会移除 input', () => {
    pickImageFiles(() => {})
    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement
    input.dispatchEvent(new Event('cancel'))
    expect(document.body.contains(input)).toBe(false)
  })
})
