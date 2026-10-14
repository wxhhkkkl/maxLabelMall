import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Area } from '@/types'

/**
 * 收货地址弹层的交互测试（本文件此前不存在 —— 既有覆盖只有
 * `AccountView.spec.ts` / `CheckoutView.spec.ts` 里"点按钮弹出弹层"那种很浅的层次）。
 *
 * 重点不是重复解析器已经测过的匹配逻辑，而是**组件的写回语义**：
 * 识别到哪一级就写哪一级、没识别到的**不许动**（Q16=A / FR-120）、
 * 完全没识别到时**一个字段都不改**（FR-121）。
 */

const getAreaTree = vi.fn()
vi.mock('@/api/area', () => ({
  getAreaTree: (...a: unknown[]) => getAreaTree(...a),
}))

const createAddress = vi.fn()
const updateAddress = vi.fn()
vi.mock('@/api/address', () => ({
  createAddress: (...a: unknown[]) => createAddress(...a),
  updateAddress: (...a: unknown[]) => updateAddress(...a),
}))

const AddressFormDialog = (await import('./AddressFormDialog.vue')).default
const { useToasts } = await import('@/components/base/useToasts')

/** 合成树，含直辖市与重名区县两个陷阱（与 address-parse.spec.ts 同构，各自独立一份） */
function node(id: number, name: string, children?: Area[]): Area {
  return children ? { id, name, children } : { id, name }
}
const TREE: Area[] = [
  node(330000, '浙江省', [
    node(330100, '杭州市', [node(330110, '余杭区'), node(330106, '西湖区')]),
  ]),
  node(110000, '北京市', [node(110100, '北京市', [node(110105, '朝阳区')])]),
]

/**
 * 挂载弹层。
 *
 * ⚠️ **必须先 `open: false` 再置 true**：地区树是在 `watch(props.open)` 里加载的
 * （树上没有 `immediate`），直接以 `open: true` 挂载时那个 watch 不会触发，
 * 树永远为空 —— 真实使用里父组件也总是先关着再打开。
 */
async function mountDialog(props: Record<string, unknown> = {}) {
  const w = mount(AddressFormDialog, {
    props: { open: false, ...props },
    global: { plugins: [createPinia()] },
  })
  await w.setProps({ open: true })
  await flushPromises()
  return w
}

/** 读三个下拉当前选中的值（DOM 上是字符串） */
function picked(w: Awaited<ReturnType<typeof mountDialog>>) {
  return ['#areaLv1', '#areaLv2', '#areaLv3'].map(
    (s) => (w.get(s).element as HTMLSelectElement).value,
  )
}

function detailText(w: Awaited<ReturnType<typeof mountDialog>>) {
  return (w.get('#addrDetail').element as HTMLTextAreaElement).value
}

beforeEach(() => {
  getAreaTree.mockReset()
  getAreaTree.mockResolvedValue(TREE)
  createAddress.mockReset()
  createAddress.mockResolvedValue(1)
  updateAddress.mockReset()
  useToasts().reset()
})

describe('AddressFormDialog —— 粘贴整段地址后自动识别（T317 / US1）', () => {
  it('识别后三个下拉与详细地址被正确填充', async () => {
    const w = await mountDialog()
    await w.get('#addrPaste').setValue('浙江省杭州市余杭区文一西路969号')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    expect(picked(w)).toEqual(['330000', '330100', '330110'])
    expect(detailText(w)).toBe('文一西路969号')
  })

  it('识别后直接保存即可成功，且落库的地区编号就是识别到的那个（US1 场景 4）', async () => {
    const w = await mountDialog()
    await w.get('#addrPaste').setValue('浙江省杭州市余杭区文一西路969号')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    await w.get('#addrName').setValue('张三')
    await w.get('#addrMobile').setValue('13800138000')
    await w.get('#addrSave').trigger('click')
    await flushPromises()

    expect(createAddress).toHaveBeenCalledTimes(1)
    expect(createAddress.mock.calls[0][0]).toMatchObject({
      name: '张三',
      mobile: '13800138000',
      areaId: 330110,
      detailAddress: '文一西路969号',
    })
  })

  it('直辖市也认得出来（北京：市级与省级同名）', async () => {
    const w = await mountDialog()
    await w.get('#addrPaste').setValue('北京市朝阳区建国路88号')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    expect(picked(w)).toEqual(['110000', '110100', '110105'])
    expect(detailText(w)).toBe('建国路88号')
  })

  it('识别全程**不出现任何加载态**（SC-045 的可观察判据）', async () => {
    const w = await mountDialog()
    await w.get('#addrPaste').setValue('浙江省杭州市余杭区文一西路969号')
    await w.get('#addrParseBtn').trigger('click')

    // 触发后**不 await**，立刻看：不应出现 loading / 禁用 / 骨架屏
    const html = w.html()
    expect(html).not.toMatch(/loading|skeleton|spinner/i)
    expect((w.get('#addrParseBtn').element as HTMLButtonElement).disabled).toBe(false)
    expect((w.get('#areaLv1').element as HTMLSelectElement).disabled).toBe(false)

    await flushPromises()
    expect(detailText(w)).toBe('文一西路969号')
  })
})

describe('AddressFormDialog —— 识别不全时不破坏客户已填的内容（T326 / US2）', () => {
  /** 先手动选好省 / 市 / 区，模拟"客户已经填了一半" */
  async function preset(w: Awaited<ReturnType<typeof mountDialog>>) {
    await w.get('#areaLv1').setValue('330000')
    await w.get('#areaLv2').setValue('330100')
    await w.get('#areaLv3').setValue('330110')
    await w.get('#addrDetail').setValue('客户自己写的详细地址')
  }

  it('**未识别到的层级保持客户原值** —— 识别到市级时，已选的区不能被清掉（Q16=A 的反面）', async () => {
    const w = await mountDialog()
    await preset(w)

    await w.get('#addrPaste').setValue('浙江省杭州市')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    // 省、市照旧；区**一个没识别到，就不许动** —— 顺手置空会把客户选好的区清掉
    expect(picked(w)).toEqual(['330000', '330100', '330110'])
    // 并且要说清楚"没识别到哪一级"（FR-120）
    expect(w.text()).toContain('未识别到区县')
  })

  it('但**上级变了就必须清空下级** —— 否则三级联动会指向一条不存在的路径', async () => {
    const w = await mountDialog()
    await preset(w)

    // 换成北京（区县与浙江那套完全无关）
    await w.get('#addrPaste').setValue('北京市')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    // 省、市都被识别到（北京的**市级节点也叫「北京市」**，所以市名同样命中）；
    // 识别里没有区县，而上级已经从浙江换成了北京 —— 此时原来的「余杭区」必须清掉，
    // 否则 pickedAreaId 会拼出一个"北京 + 杭州"的路径存进库
    expect(picked(w)).toEqual(['110000', '110100', '区县'])
  })

  it('**完全认不出时一个字段都不改**，并提示未识别到行政区划（FR-121）', async () => {
    const w = await mountDialog()
    await preset(w)

    await w.get('#addrPaste').setValue('某某小区3号楼2单元501')
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    expect(picked(w)).toEqual(['330000', '330100', '330110'])
    expect(detailText(w)).toBe('客户自己写的详细地址')
    expect(w.text()).toContain('未识别到行政区划')
  })

  it('粘贴框为空时提示**请先粘贴地址**，而不是"未识别到行政区划"（M4）', async () => {
    const w = await mountDialog()
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('请先粘贴地址')
    expect(w.text()).not.toContain('未识别到行政区划')
  })

  it('地区数据加载失败时识别按钮**禁用**（R13 / US2 场景 3）', async () => {
    getAreaTree.mockRejectedValue(new Error('boom'))
    const w = await mountDialog()

    expect((w.get('#addrParseBtn').element as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('AddressFormDialog —— 撤销识别（T330 / US3 / FR-123）', () => {
  async function parse(w: Awaited<ReturnType<typeof mountDialog>>, text: string) {
    await w.get('#addrPaste').setValue(text)
    await w.get('#addrParseBtn').trigger('click')
    await flushPromises()
  }

  it('识别后点撤销 → 三级选择与详细地址回到识别前', async () => {
    const w = await mountDialog()
    await w.get('#areaLv1').setValue('330000')
    await w.get('#areaLv2').setValue('330100')
    await w.get('#areaLv3').setValue('330110')
    await w.get('#addrDetail').setValue('原来的详细地址')

    await parse(w, '北京市朝阳区建国路88号')
    expect(picked(w)).toEqual(['110000', '110100', '110105'])

    await w.get('#addrParseUndo').trigger('click')
    await flushPromises()

    expect(picked(w)).toEqual(['330000', '330100', '330110'])
    expect(detailText(w)).toBe('原来的详细地址')
  })

  it('连续两次识别后撤销，只回退**最近一次**（US3 场景 2）', async () => {
    const w = await mountDialog()
    await parse(w, '浙江省杭州市余杭区文一西路969号')
    await parse(w, '北京市朝阳区建国路88号')

    await w.get('#addrParseUndo').trigger('click')
    await flushPromises()

    // 回到第一次识别之后的状态，而不是最初的空表单
    expect(picked(w)).toEqual(['330000', '330100', '330110'])
    expect(detailText(w)).toBe('文一西路969号')
  })

  it('撤销**只碰识别动过的那 4 个字段**，收件人 / 手机号 / 是否默认一律不动', async () => {
    const w = await mountDialog()
    await w.get('#addrName').setValue('张三')
    await w.get('#addrMobile').setValue('13800138000')

    await parse(w, '浙江省杭州市余杭区文一西路969号')
    await w.get('#addrParseUndo').trigger('click')
    await flushPromises()

    expect((w.get('#addrName').element as HTMLInputElement).value).toBe('张三')
    expect((w.get('#addrMobile').element as HTMLInputElement).value).toBe('13800138000')
  })
})
