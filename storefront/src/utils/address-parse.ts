import type { Area } from '@/types'

/**
 * 收货地址自动解析：把客户粘进来的一整段地址文本，映射到后台下发的行政区划树上。
 *
 * 契约见 `specs/004-address-auto-parse/contracts/address-parse.md`。三条硬约束：
 *
 * 1. **纯函数，不 import Vue** —— 它要靠密集的表驱动用例来锁"填错率为 0"（SC-042），
 *    塞进 SFC 就只能挂载组件才能断言，慢且测不准。
 * 2. **行政区划的唯一来源是入参 `tree`** —— MUST NOT 在本模块里定义省市名清单
 *    （FR-118 / SC-046）。后缀剥离表（省/市/区/县…）是词法规则、不是区划数据，允许内联。
 * 3. **永不抛异常** —— 空文本、树为空、火星文，一律退化为"未识别"（FR-121）。
 *
 * 它不负责"要不要写回表单"，只负责如实报告识别到了什么（见 `ParseResult`）。
 */

/** 三级行政区划的层级名；顺序固定为 省 → 市 → 区县 */
export type AddressLevel = 'province' | 'city' | 'district'

export interface ParseResult {
  province: Area | null
  city: Area | null
  district: Area | null
  /** 扣除行政区划（以及识别到的姓名、手机号）后的剩余文本；没有剩余时是空串 */
  detail: string
  /** 识别到的手机号（11 位，已去掉内部空格/短横）；没识别到时是空串（Q55） */
  phone: string
  /** 识别到的收件人姓名；没识别到时是空串（Q55） */
  name: string
  /** 未识别到的层级，顺序固定为 province → city → district */
  missing: AddressLevel[]
  /**
   * 识别到的层级数（0~3）。
   * **`0` 是"一个字段都不许改"的唯一判据**（FR-121）——组件只看这一个数。
   */
  matchedLevels: number
}

const ALL_LEVELS: AddressLevel[] = ['province', 'city', 'district']

/** 未识别：三个层级全空，且**不产生剩余文本**（R6 / FR-121） */
function nothing(): ParseResult {
  return {
    province: null,
    city: null,
    district: null,
    detail: '',
    phone: '',
    name: '',
    missing: [...ALL_LEVELS],
    matchedLevels: 0,
  }
}

// ══════════════════════════════════════════════════════════════════════════
// 归一化（R7）
// ══════════════════════════════════════════════════════════════════════════

/**
 * 只做两类归一化：**全角数字 / 字母 → 半角**、**连续空白折叠成一个半角空格并去首尾**。
 *
 * 标点**不转** —— 把「（」变成「(」只会让详细地址看起来不对，对匹配也毫无帮助
 * （行政区划名里没有 ASCII 标点）。繁简、拼音、错别字一律不碰：那些都要额外的映射表，
 * 而任何这类表都会变成"客户端内置的数据"（R7）。
 */
function normalize(text: string): string {
  return (
    text
      // 全角 Ａ-Ｚ ａ-ｚ ０-９ → 半角（码位差固定 0xFEE0）
      .replace(/[０-９Ａ-Ｚａ-ｚ]/g, (c) =>
        String.fromCharCode(c.charCodeAt(0) - 0xfee0),
      )
      // \s 已含全角空格（U+3000）、换行、制表
      .replace(/\s+/g, ' ')
      .trim()
  )
}

// ══════════════════════════════════════════════════════════════════════════
// 匹配（R2）
// ══════════════════════════════════════════════════════════════════════════

/**
 * 后缀剥离表。只剥**尾部**、且只剥已知行政区划后缀。
 *
 * 长后缀排前面：`特别行政区` 必须先于 `区` 试，否则「香港特别行政区」会只剩「香港特别」。
 * 「自治区」必须先于「区」：否则「内蒙古自治区」→「内蒙古自治」。
 */
const SUFFIXES = ['特别行政区', '自治区', '自治州', '自治县', '地区', '省', '市', '区', '县', '盟', '旗']

/**
 * 一个节点的候选名：完整名 + **逐层剥掉尾部行政区划后缀**的形式。
 *
 * 「浙江省」→「浙江」、「杭州市」→「杭州」、「余杭区」→「余杭」、「广西壮族自治区」→「广西壮族」。
 * 客户写地址时经常不带后缀（「浙江杭州余杭区…」），所以两种都要试。
 */
function candidates(name: string): string[] {
  const out = [name]
  let cur = name
  for (;;) {
    const hit = SUFFIXES.find((s) => cur.length > s.length && cur.endsWith(s))
    if (!hit) break
    cur = cur.slice(0, -hit.length)
    out.push(cur)
  }
  return out
}

interface Hit {
  node: Area
  /** 命中的候选串（可能是去后缀形式），用于后面从文本里扣除 */
  matched: string
  at: number
}

/**
 * 在 `nodes` 里找**最早出现**的候选名；同一位置有多个长度不同的命中时取**最长**的那个。
 *
 * 「位置最靠前」解决"文本里出现多个行政区"（「从浙江发到广东」取浙江）。
 * 「同位置取最长」解决「吉林省吉林市」——在 0 位置 `吉林省` 比 `吉林` 长，
 * 于是省不会误判成市那一级。
 */
function findEarliest(nodes: Area[], text: string): Hit | null {
  let best: Hit | null = null
  for (const node of nodes) {
    for (const cand of candidates(node.name)) {
      const at = text.indexOf(cand)
      if (at < 0) continue
      if (!best || at < best.at || (at === best.at && cand.length > best.matched.length)) {
        best = { node, matched: cand, at }
      }
    }
  }
  return best
}

/** 名字（含去后缀形式）与给定串相符的节点有几个 —— 用来判"唯不唯一" */
function countByName(nodes: Area[], name: string): number {
  return nodes.filter((n) => candidates(n.name).includes(name)).length
}

/** 摊平成 (省, 市, 区县) 三元组。区县可能没有（两级结构） */
interface Triple {
  province: Area
  city: Area
  district: Area | null
}

function triplesOf(provinces: Area[]): Triple[] {
  const out: Triple[] = []
  for (const province of provinces) {
    for (const city of province.children ?? []) {
      const kids = city.children ?? []
      if (!kids.length) out.push({ province, city, district: null })
      for (const district of kids) out.push({ province, city, district })
    }
  }
  return out
}

/** 从文本里扣掉某个节点名（挑**当前还在文本里的最长候选**），只扣第一次出现的那处 */
function consume(text: string, node: Area | null): string {
  if (!node) return text
  const hit = candidates(node.name)
    .map((cand) => ({ cand, at: text.indexOf(cand) }))
    .filter((x) => x.at >= 0)
    .sort((a, b) => b.cand.length - a.cand.length)[0]
  if (!hit) return text
  return text.slice(0, hit.at) + ' ' + text.slice(hit.at + hit.cand.length)
}

/** 剩余文本里还有没有"真东西" —— 只剩标点或空白就算没有（R6） */
function meaningful(text: string): boolean {
  return /[\p{L}\p{N}]/u.test(text)
}

// ══════════════════════════════════════════════════════════════════════════
// 收件人与手机号（Q55 / Q56，2026-10-23 所有者改判 —— 原 FR-128 说不识别）
// ══════════════════════════════════════════════════════════════════════════

/** 姓名与手机号之间的分隔符：空白与常见中英文标点 */
const SEPARATOR = /[\s,，、;；:：|]/

/**
 * 「收货人：张三」这类标签。只在**找姓名/手机号**用的副本上擦掉，
 * 不动原文 —— 详细地址的扣除仍用原文片段。
 *
 * 长词必须排在短词前面（「手机号」先于「手机」），否则会剩下一个「号」字。
 */
const PERSON_LABELS = /(收货人|收件人|联系人|姓名|手机号码|手机号|手机|联系电话|联系方式|电话|地址)/g

/** 首尾各取一个「只由分隔符隔开」的片段 */
function segmentBefore(text: string, end: number): string {
  let i = end
  while (i > 0 && SEPARATOR.test(text[i - 1])) i--
  let j = i
  while (j > 0 && !SEPARATOR.test(text[j - 1])) j--
  return text.slice(j, i)
}

function segmentAfter(text: string, start: number): string {
  let i = start
  while (i < text.length && SEPARATOR.test(text[i])) i++
  let j = i
  while (j < text.length && !SEPARATOR.test(text[j])) j++
  return text.slice(i, j)
}

/**
 * 找手机号：`1[3-9]` 开头的 11 位，允许号码内部夹空格或短横
 * （「187-4893-5785」「187 4893 5785」是复制来的号码的常见形态）。
 *
 * 前后紧邻数字的不算 —— 否则「138001380001」会被截成「13800138000」填进表单。
 */
function findPhone(text: string): { phone: string; raw: string; at: number } | null {
  const re = /1[3-9]\d(?:[\s-]?\d){8}/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    const before = text[m.index - 1] ?? ''
    const after = text[m.index + m[0].length] ?? ''
    if (/\d/.test(before) || /\d/.test(after)) {
      re.lastIndex = m.index + 1 // 往后挪一个字符继续找，别漏掉后面的号码
      continue
    }
    return { phone: m[0].replace(/[\s-]/g, ''), raw: m[0], at: m.index }
  }
  return null
}

/**
 * 详细地址里残留的**独立标签**（「收货人：」「电话：」）。
 *
 * 只在认出了姓名/手机号时才擦 —— 那种文本本来就是"标签 + 值"的结构，
 * 值被各自取走之后标签留在详细地址里毫无意义。
 *
 * 必须**有冒号或空格**才算标签：否则「地址不详」「联系电话区号」这类正常文字会被啃掉。
 */
const PERSON_LABEL_IN_REST =
  /(^|[\s,，、;；|])(?:(?:收货人|收件人|联系人|姓名|手机号码|手机号|手机|联系电话|联系方式|电话|地址)(?:\s*[:：]\s*|\s+))+/g

/** 姓名只认 2~4 个汉字（Q55=A） */
const NAME_RE = /^[一-龥]{2,4}$/

/** 含这些字的片段当地址看，不当姓名 —— 「文一西路」紧邻手机号时最容易误伤 */
const ADDRESS_CHARS = /[省市县区镇乡村路街巷弄号楼室层栋幢单元园厦场]/

/**
 * 姓名的判定**故意保守**（Q55=A）：与手机号相邻、2~4 个汉字、且不含地址用字，
 * 三条同时满足才认，否则留空让客户手填。
 *
 * 猜错比不猜更糟：把「文一西路」填进收件人，客户往往不会注意到。
 */
function findName(text: string, phone: { at: number; raw: string }): string {
  const before = segmentBefore(text, phone.at)
  if (NAME_RE.test(before) && !ADDRESS_CHARS.test(before)) return before
  const after = segmentAfter(text, phone.at + phone.raw.length)
  if (NAME_RE.test(after) && !ADDRESS_CHARS.test(after)) return after
  return ''
}

/** 一次找出姓名与手机号。两者都拿不到时返回空串 */
function findPerson(text: string): { name: string; phone: string; raw: string } {
  // 擦掉「收货人：」这类标签再找，否则紧邻手机号的那段会是「电话」两个字
  const t = text.replace(PERSON_LABELS, ' ')
  const hit = findPhone(t)
  if (!hit) return { name: '', phone: '', raw: '' }
  return { name: findName(t, hit), phone: hit.phone, raw: hit.raw }
}

// ══════════════════════════════════════════════════════════════════════════
// 入口
// ══════════════════════════════════════════════════════════════════════════

/**
 * 从一段自由文本里解析出省 / 市 / 区县。
 *
 * 主流程：
 * 1. 省名出现在文本里 → **只在该省的子树里**找区县与市，绝不越界
 * 2. 先找区县、再由**区县反推市级** —— 这一步是直辖市能work的关键：
 *    北京的市级节点与省级**同名**、重庆的市级叫「重庆城区」而文本里根本不会出现，
 *    「省→市→区逐级往下匹配」在这两个地方都会失败
 * 3. 省名没出现 → 先定**市**，且市名必须全国唯一；只找到区县时同理要求区县唯一（R4）
 * 4. 剩余文本 = 扣掉省 / 市 / 区名、认出来的姓名与手机号之后的部分（R6 / Q55）
 * 5. 姓名与手机号**独立于行政区划**识别（Q56）：区划一个没认出来时它们照样返回，
 *    是否写回表单由调用方决定
 *
 * @param text 客户粘贴或输入的整段地址（无需预归一化）
 * @param tree 行政区划树（省数组），来自 `getAreaTree()`
 * @returns 解析结果，**永不抛异常**
 */
export function parseAddress(text: string, tree: Area[]): ParseResult {
  const t = normalize(text)
  if (!tree.length || !t) return nothing()

  // 先认人：手机号/姓名不依赖行政区划，认不出区划时也要能填（Q56）
  const person = findPerson(t)
  const withPerson = (result: ParseResult): ParseResult => ({
    ...result,
    phone: person.phone,
    name: person.name,
  })

  const provinceHit = findEarliest(tree, t)

  let province: Area | null = provinceHit?.node ?? null
  let city: Area | null = null
  let district: Area | null = null

  if (provinceHit) {
    const triples = triplesOf([provinceHit.node])
    const districts = triples.map((x) => x.district).filter((d): d is Area => d !== null)

    // 先区县、再反推市级（R3）
    const districtHit = findEarliest(districts, t)
    if (districtHit) {
      district = districtHit.node
      city = triples.find((x) => x.district === districtHit.node)?.city ?? null
    } else {
      city = findEarliest(
        triples.map((x) => x.city),
        t,
      )?.node ?? null
    }
  } else {
    // 省名没出现：必须能**唯一定位到市**才补得出省份，否则整条不填（R4）
    const triples = triplesOf(tree)
    const cities = [...new Set(triples.map((x) => x.city))]
    const districts = triples.map((x) => x.district).filter((d): d is Area => d !== null)

    const cityHit = findEarliest(cities, t)
    if (cityHit) {
      if (countByName(cities, cityHit.node.name) !== 1) return withPerson(nothing())
      city = cityHit.node
    } else {
      const districtHit = findEarliest(districts, t)
      if (!districtHit || countByName(districts, districtHit.node.name) !== 1) return withPerson(nothing())
      district = districtHit.node
      city = triples.find((x) => x.district === districtHit.node)?.city ?? null
    }
    province = triples.find((x) => x.city === city)?.province ?? null

    // 市已定，再在该市子树里补找区县
    if (!district && city) {
      district = findEarliest(city.children ?? [], t)?.node ?? null
    }
  }

  if (!province) return withPerson(nothing())

  // 剩余文本：省名没在文本里出现过时**不扣它**（否则会把「吉林市」的「吉林」误扣掉）
  let rest = t
  if (provinceHit) rest = consume(rest, province)
  rest = consume(rest, city)
  rest = consume(rest, district)
  // 认出来的姓名与手机号已经各自有归属了，不能再留在详细地址里
  if (person.raw) rest = rest.replace(person.raw, ' ')
  if (person.name) rest = rest.replace(person.name, ' ')
  // 「收货人：」「电话：」这类标签同样不该留在详细地址里
  if (person.raw || person.name) rest = rest.replace(PERSON_LABEL_IN_REST, '$1 ')
  rest = rest.replace(/\s+/g, ' ').trim()

  const missing: AddressLevel[] = []
  if (!province) missing.push('province')
  if (!city) missing.push('city')
  if (!district) missing.push('district')

  return {
    province,
    city,
    district,
    detail: meaningful(rest) ? rest : '',
    phone: person.phone,
    name: person.name,
    missing,
    matchedLevels: 3 - missing.length,
  }
}
