<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createAddress, updateAddress, type AddressForm } from '@/api/address'
import { getAreaTree } from '@/api/area'
import MlCheck from '@/components/base/MlCheck.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { useToasts } from '@/components/base/useToasts'
import type { Address, Area } from '@/types'

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
        <select id="areaLv1" v-model.number="lv1" class="ml-select">
          <option :value="null">省份</option>
          <option v-for="a in tree" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
        <select id="areaLv2" v-model.number="lv2" class="ml-select" :disabled="!lv1">
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
