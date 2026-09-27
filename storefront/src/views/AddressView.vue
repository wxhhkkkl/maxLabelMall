<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  createAddress,
  deleteAddress,
  listAddress,
  updateAddress,
  type AddressForm,
} from '@/api/address'
import { getAreaTree } from '@/api/area'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { useToasts } from '@/components/base/useToasts'
import type { Address, Area } from '@/types'

/**
 * 收货地址管理 —— 版式见 design-new-pages.md §3.6（无设计稿）。
 *
 * ⚠️ **行政区划必须来自后台** `/system/area/tree`，不得在前端内置：
 * 内置的行政区划会过期（撤县设区、更名），且与后台的地区编号对不上，
 * 导致地址保存时校验失败。这是宪法原则 III 的具体落点。
 *
 * 五个字段全必填（FR-027）：收件人 / 手机号 / 所在地区 / 详细地址 / 是否默认。
 */
const list = ref<Address[]>([])
const tree = ref<Area[]>([])
const loading = ref(true)
const error = ref(false)

const open = ref(false)
const editingId = ref<number | null>(null)
const form = ref<AddressForm>({
  name: '',
  mobile: '',
  areaId: 0,
  detailAddress: '',
  defaultStatus: false,
})
const formError = ref('')
const saving = ref(false)

const toast = useToasts()

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

async function load() {
  loading.value = true
  error.value = false
  try {
    const [addrs, areas] = await Promise.all([listAddress(), getAreaTree()])
    list.value = addrs
    tree.value = areas
  } catch {
    error.value = true
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.value = { name: '', mobile: '', areaId: 0, detailAddress: '', defaultStatus: false }
  lv1.value = null
  lv2.value = null
  lv3.value = null
  formError.value = ''
}

function openCreate() {
  resetForm()
  open.value = true
}

function openEdit(a: Address) {
  resetForm()
  editingId.value = a.id
  form.value = {
    name: a.name,
    mobile: a.mobile,
    areaId: a.areaId,
    detailAddress: a.detailAddress,
    defaultStatus: a.defaultStatus,
  }
  open.value = true
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
    if (editingId.value === null) await createAddress(payload)
    else await updateAddress(editingId.value, payload)
    open.value = false
    toast.success('地址已保存')
    await load()
  } catch (e) {
    formError.value = (e as { message?: string })?.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function onDelete(a: Address) {
  try {
    await deleteAddress(a.id)
    toast.success('地址已删除')
    await load()
  } catch {
    toast.error('删除失败，请重试')
  }
}

async function onSetDefault(a: Address) {
  try {
    await updateAddress(a.id, {
      name: a.name,
      mobile: a.mobile,
      areaId: a.areaId,
      detailAddress: a.detailAddress,
      defaultStatus: true,
    })
    await load()
  } catch {
    toast.error('设置失败，请重试')
  }
}

onMounted(load)
</script>

<template>
  <div class="crumbs">
    <RouterLink to="/">首页</RouterLink><span>/ 收货地址</span>
  </div>

  <div class="ml-page-head">
    <h1 class="ml-page-title">收货地址</h1>
  </div>

  <div class="ml-wrap">
    <LoadingState v-if="loading" :count="2" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="地址加载失败"
      desc="网络或服务暂时不可用"
      action-text="重新加载"
      @action="load"
    />

    <template v-else>
      <div v-if="list.length" class="addr-list">
        <div v-for="a in list" :key="a.id" class="ml-card addr-card">
          <div class="addr-head">
            <span class="addr-name">{{ a.name }}</span>
            <span class="addr-mobile">{{ a.mobile }}</span>
            <span v-if="a.defaultStatus" class="ml-pill is-done">默认</span>
          </div>
          <p class="addr-detail">{{ a.areaName }} {{ a.detailAddress }}</p>
          <div class="addr-actions">
            <button class="btn-cart" type="button" @click="openEdit(a)">编辑</button>
            <button v-if="!a.defaultStatus" class="btn-cart" type="button" @click="onSetDefault(a)">
              设为默认
            </button>
            <button class="btn-cart" type="button" @click="onDelete(a)">删除</button>
          </div>
        </div>
      </div>

      <EmptyState
        v-else
        icon="📍"
        title="还没有收货地址"
        desc="添加一个地址，下单时就能直接选"
        action-text="新增地址"
        @action="openCreate"
      />

      <button
        v-if="list.length"
        id="addAddress"
        class="btn-cyan addr-add"
        type="button"
        @click="openCreate"
      >
        新增地址
      </button>
    </template>

    <!-- 新增 / 编辑弹层。地区三级联动，选项来自后台 -->
    <MlModal
      :open="open"
      :title="editingId === null ? '新增收货地址' : '编辑收货地址'"
      @close="open = false"
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

      <MlField label="所在地区" required :hint="areaNameText || '请选择省 / 市 / 区'">
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
  </div>
</template>

<style scoped>
.addr-list {
  display: grid;
  gap: 12px;
}
.addr-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.addr-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ml-text);
}
.addr-mobile {
  font-size: 14px;
  color: var(--ml-text-sub);
}
.addr-detail {
  font-size: 14px;
  color: var(--ml-text-sub);
  margin-bottom: 12px;
}
.addr-actions {
  display: flex;
  gap: 10px;
}
.addr-actions .btn-cart {
  padding: 4px 12px;
  font-size: 13px;
}
.addr-add {
  margin-top: 16px;
}
.area-row {
  display: flex;
  gap: 8px;
}
.area-row .ml-select {
  flex: 1;
}
</style>
