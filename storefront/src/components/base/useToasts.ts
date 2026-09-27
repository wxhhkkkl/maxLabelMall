import { readonly, ref } from 'vue'

/**
 * C2 轻提示的状态。契约见 design-new-pages.md §2：
 * 3 秒自动消失；**同时最多 3 条**。
 */
export type ToastKind = 'success' | 'warn' | 'error'

export interface Toast {
  id: number
  kind: ToastKind
  text: string
}

const MAX_TOASTS = 3
const DURATION = 3000

const items = ref<Toast[]>([])
let seq = 0
const timers = new Map<number, ReturnType<typeof setTimeout>>()

function dismiss(id: number): void {
  items.value = items.value.filter((t) => t.id !== id)
  const timer = timers.get(id)
  if (timer) {
    clearTimeout(timer)
    timers.delete(id)
  }
}

function push(kind: ToastKind, text: string): number {
  const id = ++seq
  items.value = [...items.value, { id, kind, text }]
  // 超出上限时丢弃最早的，保证「同时最多 3 条」
  while (items.value.length > MAX_TOASTS) {
    const oldest = items.value[0]
    if (!oldest) break
    dismiss(oldest.id)
  }
  timers.set(
    id,
    setTimeout(() => dismiss(id), DURATION),
  )
  return id
}

export function useToasts() {
  return {
    items: readonly(items),
    success: (t: string) => push('success', t),
    warn: (t: string) => push('warn', t),
    error: (t: string) => push('error', t),
    dismiss,
    /** 测试用：清空并取消所有定时器 */
    reset: () => {
      timers.forEach((t) => clearTimeout(t))
      timers.clear()
      items.value = []
    },
    MAX_TOASTS,
    DURATION,
  }
}
