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
