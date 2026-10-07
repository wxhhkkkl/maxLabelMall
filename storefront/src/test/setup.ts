/**
 * 测试环境补齐 —— **只放 jsdom 没有实现、而浏览器里必然存在的 API**。
 *
 * 判断标准很窄：补进来的每一条都得说清"这是环境缺口，不是代码缺陷"。
 * 凡是"业务代码用错了"导致的失败，一律回去改业务代码，不要往这里塞。
 */

/**
 * `matchMedia` —— jsdom 未实现，调用直接
 * `TypeError: window.matchMedia is not a function`。
 * 浏览器里自 IE10 起就是标准 API，不是可选能力，所以补在环境层而不是
 * 在组件里加 `typeof` 守卫 —— 那等于为一个生产中不可能出现的场景写分支。
 *
 * 默认 `matches` 恒为 `false`（等同窄屏，也等同 jsdom 的既有行为）。
 * 需要走宽屏分支的用例自己覆盖，见 `HomeView.spec.ts` 的 `stubViewport()`。
 */
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
