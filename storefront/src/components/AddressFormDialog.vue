<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createAddress, updateAddress, type AddressForm } from '@/api/address'
import { getAreaTree } from '@/api/area'
import MlCheck from '@/components/base/MlCheck.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { useToasts } from '@/components/base/useToasts'
import type { Address, Area } from '@/types'
import { parseAddress } from '@/utils/address-parse'

/**
 * 新增 / 编辑收货地址的弹层（C1 + C4 + C3）。
 *
 * 抽成共用组件是因为**两个地方都要能加地址**：
 *   · `/account/address` 地址管理页；
 *   · `/checkout` 结算页 —— 那里原本放的是指向地址页的 `RouterLink`，
 *     一跳走**结算页填的东西就全丢了**（选好的券、地址、备注都没了）。
 *
 * ⚠️ **行政区划必须来自后台** `/system/area/tree`，不得在前端内置：
 * 内置的会过期（撤县设区、更名），且与后台的地区编号对不上，保存时校验失败。
 * 这是宪法原则 III 的具体落点。树在这里**按需加载**（首次打开时拉一次）。
 *
 * 五个字段全必填（FR-027）：收件人 / 手机号 / 所在地区 / 详细地址 / 是否默认。
 * 元素 id（`#addrName` 等）保持在原处 —— e2e 的 `createAddress()` 依赖它们。
 */
const props = withDefaults(defineProps<{ open: boolean; address?: Address | null }>(), {
  address: null,
})
const emit = defineEmits<{ close: []; saved: [number] }>()

const toast = useToasts()

const tree = ref<Area[]>([])
const treeError = ref('')
const form = ref<AddressForm>({
  name: '',
  mobile: '',
  areaId: 0,
  detailAddress: '',
  defaultStatus: false,
})
const formError = ref('')
const saving = ref(false)

/** 三级联动的当前选择（省 / 市 / 区） */
const lv1 = ref<number | null>(null)
const lv2 = ref<number | null>(null)
const lv3 = ref<number | null>(null)

/** 「粘贴整段地址」输入框的内容 */
const pasteText = ref('')

/**
 * 地区树没到位时**不给识别**（R13）：没有解析依据，点了只会得到一次误导性的"未识别"。
 * 树加载失败与树还没回来这两种情况都算。
 */
const canParse = computed(() => tree.value.length > 0 && !treeError.value)

/** 识别的反馈文案：空框 / 认不出 / 认了一半。客户一动粘贴框就清掉，免得留着一句过期的提示 */
const parseHint = ref('')

/**
 * 识别前的快照，用于「撤销识别」（FR-123）。
 *
 * **只装识别会碰的那 4 个字段** —— 把整张表单快照进去，撤销就会顺带回退客户手打的
 * 收件人和手机号，那不是"撤销识别"而是"撤销一切"。
 * 每次识别**覆盖**上一次快照：撤销的语义是"退回最近一次识别前"。
 */
const parseSnapshot = ref<{
  lv1: number | null
  lv2: number | null
  lv3: number | null
  detail: string
} | null>(null)

const LEVEL_LABELS: Record<'province' | 'city' | 'district', string> = {
  province: '省份',
  city: '城市',
  district: '区县',
}

/** 粘贴框下面那行字：有反馈就显示反馈，否则显示例子 / 地区数据的状态 */
const pasteHint = computed(
  () =>
    parseHint.value ||
    (canParse.value ? '例如：浙江省杭州市余杭区文一西路969号' : treeError.value || '地区数据加载中…'),
)

/**
 * 点「自动识别」：把粘贴进来的一整段地址解析成省 / 市 / 区，写进三级联动与详细地址。
 *
 * 写回规则（Q15=A / Q16=A / Q17=A / FR-120 / FR-121）：
 *
 * - **`matchedLevels === 0` → 一个字段都不改**，也不回填详细地址（FR-121 / Q18=A）
 * - 识别到的层级一定写
 * - 未识别到的层级：**上级没变就保持客户原值**；**上级变了必须清空** ——
 *   否则三级联动会指向一条不存在的路径（省换成了浙江、区还留着北京的朝阳区，
 *   而 `pickedAreaId` 取的是最小层级，会把那个朝阳区存进去）
 */
function onParse() {
  // ① 空框与"认不出"必须分成两句：对着一个空框说"未识别到行政区划"，
  //    客户会以为是自己粘错了内容
  if (!pasteText.value.trim()) {
    parseHint.value = '请先粘贴地址'
    return
  }

  const r = parseAddress(pasteText.value, tree.value)
  if (r.matchedLevels === 0) {
    parseHint.value = '未识别到行政区划，请手动选择'
    return
  }

  // 真的要写入了，先把"识别前"存下来 —— 快照只保留最近一次，于是撤销退回的永远是最近一次
  parseSnapshot.value = {
    lv1: lv1.value,
    lv2: lv2.value,
    lv3: lv3.value,
    detail: form.value.detailAddress,
  }

  if (r.province && lv1.value !== r.province.id) {
    lv2.value = null
    lv3.value = null
  }
  if (r.province) lv1.value = r.province.id

  if (r.city && lv2.value !== r.city.id) lv3.value = null
  if (r.city) lv2.value = r.city.id

  if (r.district) lv3.value = r.district.id
  if (r.detail) form.value.detailAddress = r.detail

  // ③ 认了一半要说清是**哪一级**没认出来
  parseHint.value = r.missing.length
    ? `未识别到${r.missing.map((k) => LEVEL_LABELS[k]).join('、')}，请手动补选`
    : ''
}

/** 撤销识别：整体退回识别前（FR-123）。识别从没成功过时按钮不显示，故这里无需兜底 */
function onUndoParse() {
  const snap = parseSnapshot.value
  if (!snap) return
  lv1.value = snap.lv1
  lv2.value = snap.lv2
  lv3.value = snap.lv3
  form.value.detailAddress = snap.detail
  parseSnapshot.value = null
  parseHint.value = ''
}

const cities = computed(() => tree.value.find((a) => a.id === lv1.value)?.children ?? [])
const districts = computed(() => cities.value.find((a) => a.id === lv2.value)?.children ?? [])

/** 选到哪一级就算哪一级 —— 直辖市可能只有两级 */
const pickedAreaId = computed(() => lv3.value ?? lv2.value ?? lv1.value ?? 0)
const areaNameText = computed(() => {
  const n1 = tree.value.find((a) => a.id === lv1.value)?.name
  const n2 = cities.value.find((a) => a.id === lv2.value)?.name
  const n3 = districts.value.find((a) => a.id === lv3.value)?.name
  return [n1, n2, n3].filter(Boolean).join(' ')
})

const MOBILE_RE = /^1[3-9]\d{9}$/

/**
 * 在地区树里找出某个 `areaId` 的**路径**，用于编辑时回显三级选择。
 *
 * 不这么做的话，编辑既有地址时三个下拉都是空的、`pickedAreaId` 为 0，
 * 保存会被校验拦下（「请选择所在地区」）—— 用户必须把地区**重选一遍**才能改别的字段。
 */
function locateArea(areaId: number, nodes: Area[] = tree.value, path: number[] = []): number[] | null {
  for (const n of nodes) {
    const next = [...path, n.id]
    if (n.id === areaId) return next
    const found = locateArea(areaId, n.children ?? [], next)
    if (found) return found
  }
  return null
}

function reset() {
  const a = props.address
  form.value = a
    ? {
        name: a.name,
        mobile: a.mobile,
        areaId: a.areaId,
        detailAddress: a.detailAddress,
        defaultStatus: a.defaultStatus,
      }
    : { name: '', mobile: '', areaId: 0, detailAddress: '', defaultStatus: false }
  formError.value = ''
  lv1.value = null
  lv2.value = null
  lv3.value = null
  pasteText.value = ''
  parseHint.value = ''
  parseSnapshot.value = null
}

/** 地区树到位后再回显（否则树还没加载，找不到路径） */
function restorePickedArea() {
  const areaId = props.address?.areaId
  if (!areaId) return
  const path = locateArea(areaId)
  if (!path) return
  lv1.value = path[0] ?? null
  lv2.value = path[1] ?? null
  lv3.value = path[2] ?? null
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    reset()
    if (!tree.value.length) {
      try {
        tree.value = await getAreaTree()
        treeError.value = ''
      } catch {
        treeError.value = '地区数据加载失败，请关闭后重试'
      }
    }
    restorePickedArea()
  },
)

function onProvinceChange() {
  lv2.value = null
  lv3.value = null
}

function onCityChange() {
  lv3.value = null
}

function onSave() {
  formError.value = ''
  if (!form.value.name.trim()) {
    formError.value = '请填写收件人'
    return
  }
  if (!MOBILE_RE.test(form.value.mobile)) {
    formError.value = '手机号格式不正确'
    return
  }
  if (!pickedAreaId.value) {
    formError.value = '请选择所在地区'
    return
  }
  // 有下级区县时必须选到区县：运费模板按 areaId 精确匹配，
  // 停在地级市会匹配不到模板（用户 18613816876 的 140400 就是这种情况）。
  if (cities.value.length > 0 && lv2.value === null) {
    formError.value = '请选择城市'
    return
  }
  if (districts.value.length > 0 && lv3.value === null) {
    formError.value = '请选择区/县'
    return
  }
  if (!form.value.detailAddress.trim()) {
    formError.value = '请填写详细地址'
    return
  }
  void save()
}

async function save() {
  saving.value = true
  try {
    const payload: AddressForm = { ...form.value, areaId: pickedAreaId.value }
    const id = props.address
      ? (await updateAddress(props.address.id, payload), props.address.id)
      : await createAddress(payload)
    toast.success('地址已保存')
    emit('saved', id)
    emit('close')
  } catch (e) {
    formError.value = (e as { message?: string })?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <MlModal
    :open="open"
    :title="address ? '编辑收货地址' : '新增收货地址'"
    @close="emit('close')"
  >
    <!--
      「粘贴整段地址 + 自动识别」（Q15=A）。

      ⚠️ 这个入口是**显式**的：识别只在点按钮时发生。MUST NOT 在「详细地址」的
      input / paste 事件里调用解析 —— 客户常常只想手打详细地址，自动改写会让他措手不及。
    -->
    <MlField
      label="粘贴整段地址"
      :hint="pasteHint"
    >
      <div class="paste-row">
        <input
          id="addrPaste"
          v-model="pasteText"
          class="ml-input"
          placeholder="把整段地址粘到这里，再点右侧识别"
          @input="parseHint = ''"
        >
        <button
          id="addrParseBtn"
          class="btn-cart paste-btn"
          type="button"
          :disabled="!canParse"
          @click="onParse"
        >
          自动识别
        </button>
        <button
          v-if="parseSnapshot"
          id="addrParseUndo"
          class="parse-undo"
          type="button"
          @click="onUndoParse"
        >
          撤销识别
        </button>
      </div>
    </MlField>

    <MlField label="收件人" required>
      <input id="addrName" v-model.trim="form.name" class="ml-input" placeholder="请填写收件人姓名" />
    </MlField>
    <MlField label="手机号" required>
      <input
        id="addrMobile"
        v-model.trim="form.mobile"
        class="ml-input"
        maxlength="11"
        placeholder="11 位手机号"
      />
    </MlField>

    <MlField
      label="所在地区"
      required
      :hint="treeError || areaNameText || '请选择省 / 市 / 区'"
    >
      <div class="area-row">
        <select id="areaLv1" v-model.number="lv1" class="ml-select" @change="onProvinceChange">
          <option :value="null">省份</option>
          <option v-for="a in tree" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
        <select
          id="areaLv2"
          v-model.number="lv2"
          class="ml-select"
          :disabled="!lv1"
          @change="onCityChange"
        >
          <option :value="null">城市</option>
          <option v-for="a in cities" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
        <select
          id="areaLv3"
          v-model.number="lv3"
          class="ml-select"
          :disabled="!lv2 || !districts.length"
        >
          <option :value="null">区县</option>
          <option v-for="a in districts" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
      </div>
    </MlField>

    <MlField label="详细地址" required>
      <textarea
        id="addrDetail"
        v-model.trim="form.detailAddress"
        class="ml-textarea"
        placeholder="街道、门牌号等"
      />
    </MlField>

    <MlCheck
      :model-value="form.defaultStatus"
      @update:model-value="(v: boolean) => (form.defaultStatus = v)"
    >
      设为默认地址
    </MlCheck>

    <p v-if="formError" class="ml-error">{{ formError }}</p>

    <template #foot>
      <button id="addrSave" class="btn-primary" type="button" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </MlModal>
</template>

<style scoped>
/* 粘贴框 + 识别按钮同行。样式骨架照 LoginDialog 的 .code-row / .code-btn */
.paste-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.paste-row .ml-input {
  flex: 1;
}
.paste-btn {
  flex: 0 0 auto;
  white-space: nowrap;
  padding: 12px 18px;
  font-size: 14px;
}
.paste-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
/* 撤销是"反悔"动作，做成低调的文字按钮，不跟「自动识别」抢注意力 */
.parse-undo {
  flex: 0 0 auto;
  white-space: nowrap;
  background: none;
  border: none;
  padding: 0 4px;
  font-size: 13px;
  color: var(--ml-primary);
  text-decoration: underline;
  cursor: pointer;
}

.area-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
@media (max-width: 480px) {
  .area-row {
    grid-template-columns: 1fr;
  }
}
</style>
