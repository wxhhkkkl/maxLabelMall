/**
 * 富文本「上传图片」的本地文件选择。
 *
 * 不用 wangEditor 自带的实现：它会创建一个 `display:none` 的 input 再程序化 `click()`。
 * 浏览器对「隐藏 input 的程序化点击」放行程度不一，部分 Edge 环境会静默忽略这次点击 ——
 * 运营看到的现象是「点了上传图片，什么也没发生」。
 *
 * 这里改成：input 放到视口外、仅把透明度设为 0（留在渲染树里），
 * 并且**先**绑好 change 再 click —— Edge 下 click() 会同步阻塞到对话框关闭，
 * 顺序反了的话选中事件在监听器绑上之前就派发完了，整条链路静默中断
 * （现象：能选图片，选完页面毫无反应，也不报错）。
 */
export const pickImageFiles = (onPick: (files: File[]) => void): void => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.multiple = true
  // ⚠️ 不要用 display:none / visibility:hidden：两者在 Edge 下都可能让 click() 失效
  input.style.cssText = 'position:fixed;left:-9999px;top:0;width:1px;height:1px;opacity:0'

  const cleanup = () => input.remove()
  input.addEventListener('change', () => {
    const files = Array.from(input.files || [])
    cleanup()
    // 空数组也要回调：说明浏览器没把选中项交给页面，调用方需要给出可见提示（否则又是静默失败）
    onPick(files)
  })
  // 老版本 Edge 没有 cancel 事件，取消时 input 会留在 DOM 里（无害的隐藏节点）
  input.addEventListener('cancel', cleanup)

  document.body.appendChild(input)
  input.click()
}
