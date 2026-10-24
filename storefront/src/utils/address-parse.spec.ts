import { describe, expect, it } from 'vitest'

import type { Area } from '@/types'

import { parseAddress } from './address-parse'

/**
 * 解析器测试。**样本量按 SC-042 定死**：「省市区齐全」类 ≥5 条、其余每类 ≥2 条、
 * 三个测试任务（T314 / T315 / T325）合计 ≥20 条。
 *
 * 夹具是**合成的**，不是 `area.csv` 的一份拷贝（否则就成了"客户端内置行政区划"，
 * SC-046 禁止）。只保留测试真正需要的那几种结构陷阱。
 */

function node(id: number, name: string, children?: Area[]): Area {
  return children ? { id, name, children } : { id, name }
}

const 余杭区 = node(330110, '余杭区')
const 西湖区 = node(330106, '西湖区')
const 江北区宁波 = node(330205, '江北区')
const 朝阳区北京 = node(110105, '朝阳区')
const 渝中区 = node(500103, '渝中区')
const 江北区重庆 = node(500105, '江北区')
const 朝阳区长春 = node(220104, '朝阳区')

const 杭州市 = node(330100, '杭州市', [余杭区, 西湖区])
const 宁波市 = node(330200, '宁波市', [江北区宁波])
const 北京市 = node(110100, '北京市', [朝阳区北京])
const 重庆城区 = node(500100, '重庆城区', [渝中区, 江北区重庆])
const 长春市 = node(220100, '长春市', [朝阳区长春])

const 浙江省 = node(330000, '浙江省', [杭州市, 宁波市])
const 北京市省 = node(110000, '北京市', [北京市])
const 重庆市 = node(500000, '重庆市', [重庆城区])
const 吉林省 = node(220000, '吉林省', [长春市])

const TREE: Area[] = [浙江省, 北京市省, 重庆市, 吉林省]

/**
 * 只用于 R4「市名不唯一」那一条分支。
 *
 * 实测 `area.csv` 里 **390 个市级行政区的跨省重名数是 0**（区县才有 47 组重名），
 * 所以这个分支在真实数据上不会触发。仍然造一个合成用例盯着它 ——
 * 免得那个"恰好 1 个"的判据哪天被当成死代码删掉。
 */
const TREE_WITH_DUPLICATE_CITY: Area[] = [
  ...TREE,
  node(990000, '示例省', [node(990100, '杭州市', [node(990110, '示例区')])]),
]

/** 一条断言 = 一个样本：期望的省 / 市 / 区名（null 表示"不该填"）与剩余文本 */
interface Sample {
  text: string
  province: string | null
  city: string | null
  district: string | null
  detail: string
}

function assertSamples(samples: Sample[], tree: Area[] = TREE) {
  for (const s of samples) {
    const r = parseAddress(s.text, tree)
    const got = {
      province: r.province?.name ?? null,
      city: r.city?.name ?? null,
      district: r.district?.name ?? null,
      detail: r.detail,
    }
    expect(got, `输入：${JSON.stringify(s.text)}`).toEqual({
      province: s.province,
      city: s.city,
      district: s.district,
      detail: s.detail,
    })
  }
}

describe('address-parse —— 主路径与书写差异（T314 / FR-117 / FR-119 / FR-122）', () => {
  it('省市区齐全：5 条（SC-042 的「齐全」类下限）', () => {
    assertSamples([
      {
        text: '浙江省杭州市余杭区文一西路969号',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '文一西路969号',
      },
      {
        text: '浙江省杭州市西湖区文三路100号',
        province: '浙江省', city: '杭州市', district: '西湖区', detail: '文三路100号',
      },
      {
        text: '浙江省宁波市江北区人民路1号',
        province: '浙江省', city: '宁波市', district: '江北区', detail: '人民路1号',
      },
      {
        // 只有行政区划、没有详细地址 → 剩余为空串，**不回填**（R6）
        text: '浙江省杭州市余杭区',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '',
      },
      {
        text: '浙江省杭州市西湖区文一西路969号3号楼2单元501',
        province: '浙江省', city: '杭州市', district: '西湖区', detail: '文一西路969号3号楼2单元501',
      },
    ])
  })

  it('缺「省 / 市」后缀：2 条', () => {
    assertSamples([
      {
        text: '浙江杭州余杭区文一西路969号',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '文一西路969号',
      },
      {
        text: '浙江省杭州西湖区文三路1号',
        province: '浙江省', city: '杭州市', district: '西湖区', detail: '文三路1号',
      },
    ])
  })

  it('全角数字、多余空白与换行：2 条', () => {
    assertSamples([
      {
        text: '浙江省杭州市余杭区文一西路９６９号',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '文一西路969号',
      },
      {
        text: '  浙江省  杭州市\n余杭区\t文一西路969号  ',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '文一西路969号',
      },
    ])
  })
})

describe('address-parse —— 结构难点（T315 / R3 / R4 / R5）', () => {
  it('直辖市：北京（市级与省级同名）与重庆（市级叫「重庆城区」）', () => {
    assertSamples([
      {
        text: '北京市朝阳区建国路88号',
        province: '北京市', city: '北京市', district: '朝阳区', detail: '建国路88号',
      },
      {
        text: '北京朝阳区建国路88号',
        province: '北京市', city: '北京市', district: '朝阳区', detail: '建国路88号',
      },
      {
        text: '重庆市渝中区解放碑1号',
        province: '重庆市', city: '重庆城区', district: '渝中区', detail: '解放碑1号',
      },
    ])
  })

  it('漏写省份：市名唯一时补出省份；不唯一时不填（R4）', () => {
    assertSamples([
      {
        text: '杭州市余杭区文一西路969号',
        province: '浙江省', city: '杭州市', district: '余杭区', detail: '文一西路969号',
      },
      {
        text: '宁波市江北区人民路1号',
        province: '浙江省', city: '宁波市', district: '江北区', detail: '人民路1号',
      },
    ])
  })

  it('市名跨省重名时**不填**（R4；真实数据里不会触发，故用合成树）', () => {
    const r = parseAddress('杭州市余杭区文一西路969号', TREE_WITH_DUPLICATE_CITY)
    expect(r.province).toBeNull()
    expect(r.city).toBeNull()
    expect(r.district).toBeNull()
    expect(r.matchedLevels).toBe(0)
  })

  it('重名区县（两个「朝阳区」）：靠已定的省 / 市收敛（R5）', () => {
    assertSamples([
      {
        text: '北京市朝阳区建国路88号',
        province: '北京市', city: '北京市', district: '朝阳区', detail: '建国路88号',
      },
      {
        text: '吉林省长春市朝阳区人民大街1号',
        province: '吉林省', city: '长春市', district: '朝阳区', detail: '人民大街1号',
      },
      {
        text: '浙江省宁波市江北区人民路1号',
        province: '浙江省', city: '宁波市', district: '江北区', detail: '人民路1号',
      },
      {
        text: '重庆市渝中区解放碑1号',
        province: '重庆市', city: '重庆城区', district: '渝中区', detail: '解放碑1号',
      },
    ])
  })

  it('多个候选省时取**位置最靠前**的那个（R2）', () => {
    const r = parseAddress('从浙江省发到广东省', TREE)
    expect(r.province?.name).toBe('浙江省')
    expect(r.city).toBeNull()
    expect(r.district).toBeNull()
  })
})

describe('address-parse —— 未识别与部分识别（T325 / FR-120 / FR-121）', () => {
  it('完全认不出行政区划：不填任何层级，且**不产生剩余文本**', () => {
    assertSamples([
      {
        text: '',
        province: null, city: null, district: null, detail: '',
      },
      {
        text: '   \n\t ',
        province: null, city: null, district: null, detail: '',
      },
      {
        text: '某某小区3号楼2单元501',
        province: null, city: null, district: null, detail: '',
      },
      {
        text: '!!@@##',
        province: null, city: null, district: null, detail: '',
      },
    ])
  })

  it('**空树 + 一条看起来合法的真实地址** → 依然认不出来（SC-046 唯一的自动守卫）', () => {
    // 若模块里偷偷内置了省市名清单，这条会失败 —— 那正是 FR-118 / SC-046 禁止的事。
    // 只传空串的"空树"用例是平凡成立的，守不住任何东西。
    const r = parseAddress('浙江省杭州市余杭区文一西路969号', [])
    expect(r.matchedLevels).toBe(0)
    expect(r.province).toBeNull()
    expect(r.detail).toBe('')
  })

  it('只到市：区县缺失进 missing，剩余文本为空不回声', () => {
    assertSamples([
      {
        text: '浙江省杭州市',
        province: '浙江省', city: '杭州市', district: null, detail: '',
      },
      {
        text: '浙江省杭州市，',
        province: '浙江省', city: '杭州市', district: null, detail: '',
      },
    ])
  })

  it('只到省', () => {
    assertSamples([
      {
        text: '浙江省',
        province: '浙江省', city: null, district: null, detail: '',
      },
      {
        text: '浙江',
        province: '浙江省', city: null, district: null, detail: '',
      },
    ])
  })

  it('missing 的层级与 matchedLevels 对得上，且顺序固定为 省 → 市 → 区县', () => {
    expect(parseAddress('浙江省', TREE).missing).toEqual(['city', 'district'])
    expect(parseAddress('浙江省杭州市', TREE).missing).toEqual(['district'])
    expect(parseAddress('某某小区', TREE).missing).toEqual(['province', 'city', 'district'])
  })
})

describe('address-parse —— ParseResult 不变量（T316 / contracts §2.4）', () => {
  it('识别到的节点是入参树里的**同一个对象引用**（组件直接取 id 做选中）', () => {
    const r = parseAddress('浙江省杭州市余杭区文一西路969号', TREE)
    expect(r.province).toBe(浙江省)
    expect(r.city).toBe(杭州市)
    expect(r.district).toBe(余杭区)
  })

  it('matchedLevels === 3 - missing.length', () => {
    for (const text of ['浙江省杭州市余杭区文一西路969号', '浙江省杭州市', '浙江省', '随便写点什么']) {
      const r = parseAddress(text, TREE)
      expect(r.matchedLevels, text).toBe(3 - r.missing.length)
    }
  })

  it('有区县就必有省；有市就必有省', () => {
    for (const text of ['浙江省杭州市余杭区文一西路969号', '北京市朝阳区建国路88号', '杭州市余杭区某路1号']) {
      const r = parseAddress(text, TREE)
      if (r.district) expect(r.province, text).not.toBeNull()
      if (r.city) expect(r.province, text).not.toBeNull()
    }
  })

  it('missing 里不会出现"识别到了但没显示"的层级', () => {
    const r = parseAddress('浙江省杭州市', TREE)
    expect(r.province).not.toBeNull()
    expect(r.city).not.toBeNull()
    expect(r.missing).not.toContain('province')
    expect(r.missing).not.toContain('city')
    expect(r.missing).toContain('district')
  })
})

/**
 * 收件人与手机号的识别（Q55 / Q56，2026-10-23 所有者改判 —— 原 FR-128 明确规定"不识别"，
 * 理由是有歧义；现在的要求是"宁可少填、不可填错"地认）。
 */
describe('address-parse —— 收件人与手机号（Q55 / Q56）', () => {
  it('姓名紧邻手机号 → 都识别出来，并从详细地址里扣除', () => {
    const r = parseAddress('张三 18748935785 浙江省杭州市余杭区文一西路969号', TREE)
    expect(r.name).toBe('张三')
    expect(r.phone).toBe('18748935785')
    expect(r.district).toBe(余杭区)
    expect(r.detail).toBe('文一西路969号')
  })

  it('姓名在手机号后面也认得出来', () => {
    const r = parseAddress('浙江省杭州市余杭区文一西路969号 13800138000 张三', TREE)
    expect(r.name).toBe('张三')
    expect(r.phone).toBe('13800138000')
    expect(r.detail).toBe('文一西路969号')
  })

  it('带「收货人 / 电话」这类标签也认得出来', () => {
    const r = parseAddress('收货人：张三 电话：18748935785 浙江省杭州市余杭区文一西路969号', TREE)
    expect(r.name).toBe('张三')
    expect(r.phone).toBe('18748935785')
    expect(r.detail).toBe('文一西路969号')
  })

  it('手机号夹空格或短横也认得', () => {
    expect(parseAddress('张三 187-4893-5785 浙江省杭州市余杭区文一西路969号', TREE).phone).toBe('18748935785')
    expect(parseAddress('张三 187 4893 5785 浙江省杭州市余杭区文一西路969号', TREE).phone).toBe('18748935785')
  })

  it('不紧邻手机号的姓名不认（宁可少填，不可填错）', () => {
    const r = parseAddress('张三 浙江省杭州市余杭区文一西路969号', TREE)
    expect(r.name).toBe('')
    expect(r.phone).toBe('')
  })

  it('像地址的片段即使紧邻手机号也不当姓名（含路/号/区…）', () => {
    const r = parseAddress('浙江省杭州市余杭区 文一西路 18748935785', TREE)
    expect(r.name).toBe('')
    expect(r.detail).toBe('文一西路')
  })

  it('超过 4 个字的名字不认', () => {
    const r = parseAddress('广东省东莞市某某贸易有限公司 18748935785', TREE)
    expect(r.name).toBe('')
  })

  it('号码更长时不会截出错号码', () => {
    const r = parseAddress('浙江省杭州市余杭区文一西路969号 138001380001', TREE)
    expect(r.phone).toBe('')
  })

  it('认不出行政区划时手机号与姓名照填，但省市区与详细地址一个都不动（Q56）', () => {
    const r = parseAddress('张三 18748935785 某某小区3栋', TREE)
    expect(r.matchedLevels).toBe(0)
    expect(r.name).toBe('张三')
    expect(r.phone).toBe('18748935785')
    expect(r.detail).toBe('')
    expect(r.province).toBeNull()
  })

  it('普通地址里没有这两样时都是空串', () => {
    const r = parseAddress('浙江省杭州市余杭区文一西路969号', TREE)
    expect(r.name).toBe('')
    expect(r.phone).toBe('')
  })
})
