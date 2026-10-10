import DOMPurify from 'dompurify'

/**
 * 富文本安全过滤（FR-005c / SC-020）—— 商品详情等后台富文本的唯一渲染入口。
 *
 * 为什么必须做：商品详情富文本由运营在后台富文本编辑器里撰写，以 HTML 存储并由
 * 前台渲染。**直接 `v-html` 注入等于把 XSS 面交给运营账号** —— 账号被盗或后台
 * 被越权写入，脚本就会在访客浏览器里执行。
 *
 * 为什么用 DOMPurify 而不是手写正则：手写过滤是 XSS 最常见的来源（大小写绕过、
 * 事件属性、`javascript:` 协议、`<svg><script>`、`<iframe>` 等），正则很难穷尽。
 * DOMPurify 是这一领域的既有成熟实现。
 *
 * 策略：**白名单**（默认配置即基于白名单），保留常见排版标签与图片/表格，
 * 剔除脚本、事件属性、危险协议与可嵌入执行的标签。
 */

/** 允许的标签：排版 + 图片 + 表格（详情页的实际需要） */
const ALLOWED_TAGS = [
  'p', 'br', 'span', 'div',
  'strong', 'b', 'em', 'i', 'u', 's', 'sub', 'sup',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'blockquote', 'hr',
  'a', 'img',
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
]

/** 允许的属性：链接、图片与表格排版所需 */
const ALLOWED_ATTR = ['href', 'title', 'target', 'rel', 'src', 'alt', 'width', 'height', 'colspan', 'rowspan']

export function sanitizeRichText(html: string): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 不允许 data: 协议的图片（可用于绕过内容策略）
    ALLOW_DATA_ATTR: false,
    FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form', 'input', 'svg', 'math'],
    FORBID_ATTR: ['style'],
  })
}

/**
 * 判断一段富文本**还有没有可见内容**（FR-064 / FR-065）。
 *
 * 为什么需要它：决定"要不要渲染详情区块"之前，得先知道里面到底有没有东西。
 *
 * ⚠️ **不能剥掉标签看还剩不剩文字** —— 那正是本缺陷的成因。纯图片详情（长图、参数图）
 * 在电商里是常态，剥完标签什么文字都不剩，于是被判成"没有详情"整块隐藏。
 * 所以判据是：**有 `<img>` 就算有内容**，文字只是另一种。
 *
 * 反过来，**空壳标签不算**：`<p></p>`、`<br>`、只剩空白的 `<div>` 的可见面积是零，
 * 放它们通过会渲染出一个什么都看不见的区块 —— 那是另一种空白块，同样要防。
 *
 * 用 DOM 解析而不是正则：正则判"有没有内容"本来就不可靠（自闭合标签、属性里的 `>`、
 * 注释都能骗过它），而浏览器与 jsdom 都现成有 DOM。
 */
export function hasVisibleContent(html: string): boolean {
  if (!html || !html.trim()) return false
  const el = document.createElement('div')
  el.innerHTML = html
  // 图片是可见内容 —— 不能因为"没有文字"就否定它。querySelector 会找到任意层级的 img
  if (el.querySelector('img')) return true
  return Boolean(el.textContent?.trim())
}
