<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listAddress } from '@/api/address'
import {
  createCustomizationOrder,
  listCustomizationMaterials,
  quoteCustomization,
  type CustomizationFile,
  type CustomizationMaterial,
  type CustomizationQuote,
} from '@/api/customization'
import { DELIVERY_TYPE_EXPRESS } from '@/api/order'
import { uploadFile } from '@/api/upload'
import CheckoutInvoiceCard from '@/components/CheckoutInvoiceCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { InvoiceTitleKind, InvoiceType, type Address, type InvoiceApplyForm } from '@/types'
import { getAccessToken } from '@/utils/auth'
import { formatYuan } from '@/utils/money'

/**
 * 印刷定制（014）—— 单页渐进式四步（按参考站点截图）：
 *   选择第一步后，下面依次出现第二步 / 第三步 / 第四步；没有「上一步 / 下一步」。
 *
 * 价格仍然只来自服务端 `/quote`（宪法 III）；排版字段只随订单保存、作为生产要求。
 */

const route = useRoute()
const router = useRouter()

const FILE_ACCEPT = '.pdf,.ai,.cdr,.jpg,.jpeg,.png'
const FILE_MAX_SIZE = 16 * 1024 * 1024
const FILE_MAX_COUNT = 5
const FIXED_GAP = 3

const MODE_OPTIONS = [
  { value: 1, label: '标签印刷（多色）', desc: '内容全部一致的彩色标签' },
  { value: 2, label: '标签打印（黑色）', desc: '可变数据的条码 / 文字标签' },
  { value: 3, label: '多色印刷+黑色打印', desc: '彩色底 + 可变数据' },
  { value: 4, label: '定制空白标签', desc: '只加工空白标签，后续自行打印' },
]
const FORM_OPTIONS = [
  { value: 1, label: '卷筒（卷轴）' },
  { value: 2, label: '平张（单张）' },
]
const CORNER_OPTIONS = [
  { value: 1, label: '圆角' },
  { value: 2, label: '直角' },
]
const AXIS_OPTIONS = ['25.4mm', '76.2mm', '40mm']
const COLUMN_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8]

const loading = ref(true)
const error = ref('')
const materials = ref<CustomizationMaterial[]>([])

const mode = ref<number | null>(null)
const materialId = ref<number | null>(null)
const width = ref<number | null>(null)
const height = ref<number | null>(null)
const quantity = ref<number | null>(null)
const form = ref<number | null>(null)
const sheetSpec = ref('A4')
const finishing = ref(1)
const tearLine = ref(false)
const rowCount = ref(1)
const columnCount = ref(1)
const axisDiameter = ref('25.4mm')
const urgent = ref(false)
const remark = ref('')

const files = ref<CustomizationFile[]>([])
const uploading = ref(false)
const fileError = ref('')

const quote = ref<CustomizationQuote | null>(null)
const quoteError = ref('')
const quoting = ref(false)

const addresses = ref<Address[]>([])
const addressId = ref<number | null>(null)
const invoiceEnabled = ref(false)
const invoiceForm = ref<InvoiceApplyForm>({
  type: InvoiceType.NORMAL,
  titleKind: InvoiceTitleKind.COMPANY,
  titleName: '',
  taxNo: '',
  titleAddress: '',
  titlePhone: '',
  bankName: '',
  email: '',
})

const submitting = ref(false)
const submitError = ref('')
const sizeError = ref('')
const quantityError = ref('')
const priceChanged = ref<CustomizationQuote | null>(null)

const currentMaterial = computed(() => materials.value.find((m) => m.id === materialId.value) ?? null)
const loggedIn = computed(() => !!getAccessToken())
const materialHint = computed(() => {
  const m = currentMaterial.value
  return m ? `尺寸 ${m.minWidth}~${m.maxWidth} mm，数量 ${m.minQuantity}~${m.maxQuantity}` : ''
})
const modeLabel = computed(() => MODE_OPTIONS.find((m) => m.value === mode.value)?.label ?? '')
const formLabel = computed(() => FORM_OPTIONS.find((f) => f.value === form.value)?.label ?? '')
const cornerLabel = computed(() => CORNER_OPTIONS.find((f) => f.value === finishing.value)?.label ?? '')
const showStep2 = computed(() => mode.value !== null)
const showStep3 = computed(() => materialId.value !== null)
const showStep4 = computed(() => showStep3.value && form.value !== null
  && !!width.value && !!height.value && columnCount.value >= 1
  && (form.value === 1 || rowCount.value >= 1))
/** 纸宽预估：列数 × (标签宽 + 列间距)，与参考站点 3×60 + 3×3 = 189 一致 */
const estimatedPaperWidth = computed(() => {
  if (!width.value) return 0
  return columnCount.value * (width.value + FIXED_GAP)
})
/** 模切收费：圆角或撕裂线；直角不收 */
const dieCut = computed(() => finishing.value === 1 || tearLine.value)

function openLogin(): void {
  void router.push({
    path: route.path,
    query: { ...route.query, login: '1', redirect: route.fullPath },
  })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    materials.value = await listCustomizationMaterials()
    if (loggedIn.value) {
      addresses.value = await listAddress()
      const def = addresses.value.find((a) => a.defaultStatus) ?? addresses.value[0]
      addressId.value = def?.id ?? null
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '定制信息加载失败'
  } finally {
    loading.value = false
  }
}

function pickMaterial(id: number): void {
  materialId.value = id
  const material = materials.value.find((m) => m.id === id)
  if (material) {
    if (width.value === null) width.value = material.minWidth
    if (height.value === null) height.value = material.minHeight
    if (quantity.value === null) quantity.value = material.minQuantity
  }
}

function validateSize(): boolean {
  sizeError.value = ''
  const m = currentMaterial.value
  if (!m || !width.value || !height.value) {
    sizeError.value = '请填写标签宽度与高度'
    return false
  }
  if (width.value < m.minWidth || width.value > m.maxWidth
    || height.value < m.minHeight || height.value > m.maxHeight) {
    sizeError.value = `尺寸需在 ${m.minWidth}~${m.maxWidth} × ${m.minHeight}~${m.maxHeight} mm 内`
    return false
  }
  return true
}

function validateQuantity(): boolean {
  quantityError.value = ''
  const m = currentMaterial.value
  if (!m || !quantity.value) {
    quantityError.value = '请填写定制数量'
    return false
  }
  if (quantity.value < m.minQuantity || quantity.value > m.maxQuantity) {
    quantityError.value = `数量需在 ${m.minQuantity}~${m.maxQuantity} 之间`
    return false
  }
  return true
}

// ========== 报价 ==========
let quoteTimer: ReturnType<typeof setTimeout> | null = null

function scheduleQuote(): void {
  if (quoteTimer) clearTimeout(quoteTimer)
  quoteTimer = setTimeout(() => {
    void refreshQuote()
  }, 300)
}

async function refreshQuote(): Promise<void> {
  if (!showStep4.value || materialId.value === null || !width.value || !height.value || !quantity.value) {
    return
  }
  quoting.value = true
  quoteError.value = ''
  try {
    quote.value = await quoteCustomization({
      materialId: materialId.value,
      width: width.value,
      height: height.value,
      quantity: quantity.value,
      mode: mode.value as number,
      form: form.value as number,
      sheetSpec: sheetSpec.value,
      finishing: finishing.value,
      tearLine: tearLine.value,
      rowCount: rowCount.value,
      columnCount: columnCount.value,
      rowGap: FIXED_GAP,
      columnGap: FIXED_GAP,
      axisDiameter: axisDiameter.value,
      dieCut: dieCut.value,
      urgent: urgent.value,
      addressId: addressId.value ?? undefined,
      deliveryType: DELIVERY_TYPE_EXPRESS,
    })
  } catch (err) {
    quote.value = null
    quoteError.value = err instanceof Error ? err.message : '报价失败，请稍后重试'
  } finally {
    quoting.value = false
  }
}

watch([mode, materialId, width, height, quantity, form, sheetSpec, finishing, tearLine,
  rowCount, columnCount, axisDiameter, urgent, addressId], scheduleQuote)

async function onPickFiles(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  input.value = ''
  if (!picked.length || uploading.value) return
  if (!loggedIn.value) {
    openLogin()
    return
  }
  fileError.value = ''
  const room = FILE_MAX_COUNT - files.value.length
  if (room <= 0) {
    fileError.value = `最多上传 ${FILE_MAX_COUNT} 个文件`
    return
  }
  const take = picked.slice(0, room)
  if (picked.length > room) fileError.value = `最多上传 ${FILE_MAX_COUNT} 个文件，多余的已忽略`
  uploading.value = true
  try {
    for (const file of take) {
      if (file.size > FILE_MAX_SIZE) {
        fileError.value = '单个文件不能超过 16MB'
        continue
      }
      const url = await uploadFile(file)
      files.value = [...files.value, { name: file.name, url, size: file.size, type: file.type }]
    }
  } catch (err) {
    fileError.value = err instanceof Error ? err.message : '文件上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

function removeFile(index: number): void {
  files.value = files.value.filter((_, i) => i !== index)
}

async function submit(expectedAmount?: number): Promise<void> {
  if (!validateSize() || !validateQuantity()) return
  if (!loggedIn.value) {
    openLogin()
    return
  }
  if (!quote.value) {
    submitError.value = '请先计算价格'
    return
  }
  if (!addressId.value) {
    submitError.value = '请选择收货地址'
    return
  }
  if (files.value.length < 1) {
    submitError.value = '请至少上传 1 个设计文件'
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    const resp = await createCustomizationOrder({
      materialId: materialId.value as number,
      width: width.value as number,
      height: height.value as number,
      quantity: quantity.value as number,
      mode: mode.value as number,
      form: form.value as number,
      sheetSpec: sheetSpec.value,
      finishing: finishing.value,
      tearLine: tearLine.value,
      rowCount: rowCount.value,
      columnCount: columnCount.value,
      rowGap: FIXED_GAP,
      columnGap: FIXED_GAP,
      axisDiameter: axisDiameter.value,
      dieCut: dieCut.value,
      urgent: urgent.value,
      files: files.value,
      remark: remark.value || undefined,
      expectedAmount: expectedAmount ?? quote.value.payable,
      addressId: addressId.value,
      deliveryType: DELIVERY_TYPE_EXPRESS,
      invoice: invoiceEnabled.value ? { ...invoiceForm.value } : undefined,
    })
    if (resp.changed) {
      priceChanged.value = resp.quote ?? null
      if (resp.quote) quote.value = resp.quote
      return
    }
    if (resp.id) await router.push(`/order/${resp.id}`)
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function confirmNewPrice(): Promise<void> {
  const latest = priceChanged.value
  priceChanged.value = null
  if (latest) {
    quote.value = latest
    await submit(latest.payable)
  }
}

onMounted(load)
</script>

<template>
  <div class="ml-wrap customization-page">
    <div class="cr-breadcrumb">
      <RouterLink to="/">首页</RouterLink><span>/ 印刷定制</span>
    </div>

    <LoadingState v-if="loading" :count="2" />

    <EmptyState
      v-else-if="error"
      mode="error"
      title="定制信息加载失败"
      :desc="error"
      action-text="重新加载"
      @action="load"
    />

    <EmptyState
      v-else-if="!materials.length"
      title="暂无可定制的材质"
      desc="运营还没有配置可售材质，请稍后再试或联系客服"
    />

    <div v-else class="ml-2col">
      <div>
        <!-- 第一步：加工形式 -->
        <div class="ml-card step-card">
          <div class="step-head"><i>第一步</i> 请根据您的需要选择加工形式（单选）</div>
          <div class="mode-list">
            <button
              v-for="m in MODE_OPTIONS"
              :id="`customizationMode-${m.value}`"
              :key="m.value"
              type="button"
              class="mode-item"
              :class="{ 'is-active': mode === m.value }"
              @click="mode = m.value"
            >
              <b>{{ m.label }}</b><span>{{ m.desc }}</span>
            </button>
          </div>
        </div>

        <!-- 第二步：材质 -->
        <div v-if="showStep2" class="ml-card step-card">
          <div class="step-head"><i>第二步</i> 请根据您的需要选择材质（单选）</div>
          <div class="material-list">
            <button
              v-for="m in materials"
              :id="`customizationMaterial-${m.id}`"
              :key="m.id"
              type="button"
              class="material-item"
              :class="{ 'is-active': m.id === materialId }"
              @click="pickMaterial(m.id)"
            >
              <b>{{ m.name }}</b>
              <span>{{ m.minWidth }}~{{ m.maxWidth }}mm</span>
              <em v-if="m.description">{{ m.description }}</em>
            </button>
          </div>
          <p v-if="materialHint" class="ml-hint">{{ materialHint }}</p>
        </div>

        <!-- 第三步：成品形态 -->
        <div v-if="showStep3" class="ml-card step-card">
          <div class="step-head"><i>第三步</i> 请根据您的需要选择成品形态</div>

          <MlField label="成品形态" required>
            <div class="seg-row">
              <button
                v-for="f in FORM_OPTIONS"
                :id="`customizationForm-${f.value}`"
                :key="f.value"
                type="button"
                class="seg-item"
                :class="{ 'is-active': form === f.value }"
                @click="form = f.value"
              >
                {{ f.label }}
              </button>
            </div>
          </MlField>

          <div class="form-grid">
            <MlField label="标签宽度（mm）" required>
              <input id="customizationWidth" v-model.number="width" class="ml-input" type="number" min="1" @blur="validateSize">
            </MlField>
            <MlField label="标签高度（mm）" required>
              <input id="customizationHeight" v-model.number="height" class="ml-input" type="number" min="1" @blur="validateSize">
            </MlField>
            <MlField v-if="form === 2" label="整张规格">
              <select id="customizationSheetSpec" v-model="sheetSpec" class="ml-select">
                <option value="A4">A4</option>
                <option value="其他">其他</option>
              </select>
            </MlField>
          </div>
          <p v-if="sizeError" class="ml-error">{{ sizeError }}</p>

          <MlField v-if="form === 1" label="轴芯内径">
            <div class="seg-row">
              <button
                v-for="a in AXIS_OPTIONS"
                :id="`customizationAxis-${a}`"
                :key="a"
                type="button"
                class="seg-item"
                :class="{ 'is-active': axisDiameter === a }"
                @click="axisDiameter = a"
              >
                {{ a }}
              </button>
            </div>
          </MlField>

          <div class="inline-row">
            <span class="inline-label">边角：</span>
            <button
              v-for="c in CORNER_OPTIONS"
              :id="`customizationCorner-${c.value}`"
              :key="c.value"
              type="button"
              class="seg-item"
              :class="{ 'is-active': finishing === c.value }"
              @click="finishing = c.value"
            >
              {{ c.label }}
            </button>
            <span class="inline-label ml-20px">撕裂线：</span>
            <MlCheck v-model="tearLine">
              撕裂线
            </MlCheck>
          </div>

          <div class="inline-row">
            <span class="inline-label">排列：</span>
            <select id="customizationColumns" v-model.number="columnCount" class="ml-select !w-100px">
              <option v-for="c in COLUMN_OPTIONS" :key="c" :value="c">{{ c }}</option>
            </select>
            <span>列</span>
            <span class="paper-width">纸宽：{{ estimatedPaperWidth }}mm</span>
            <span class="ml-20px">行间距：{{ FIXED_GAP }}mm　列间距：{{ FIXED_GAP }}mm</span>
          </div>

          <MlField v-if="form === 2" label="每张行数">
            <input id="customizationRows" v-model.number="rowCount" class="ml-input !w-120px" type="number" min="1">
          </MlField>
        </div>

        <!-- 第四步：数量与提交 -->
        <div v-if="showStep4" class="ml-card step-card">
          <div class="step-head"><i>第四步</i> 请选择定制数量，确认信息后提交订单</div>

          <div class="form-grid">
            <MlField label="定制数量" required>
              <input id="customizationQuantity" v-model.number="quantity" class="ml-input" type="number" min="1" @blur="validateQuantity">
            </MlField>
          </div>
          <p v-if="quantityError" class="ml-error">{{ quantityError }}</p>

          <div class="process-list">
            <MlCheck v-model="urgent">加急（优先处理）</MlCheck>
          </div>
          <p class="ml-hint">加急只做优先处理，具体交期由客服沟通确认。</p>

          <MlField label="备注（选填）" hint="附加要求，最多 500 字">
            <textarea v-model="remark" class="ml-textarea" maxlength="500" />
          </MlField>

          <div class="file-block">
            <div class="file-title">文件管理</div>
            <ul v-if="files.length" class="file-list">
              <li v-for="(file, index) in files" :key="file.url">
                <span>{{ file.name }}</span>
                <button type="button" class="file-remove" @click="removeFile(index)">删除</button>
              </li>
            </ul>
            <label class="file-pick">
              {{ uploading ? '上传中…' : `添加文件（${files.length}/${FILE_MAX_COUNT}）` }}
              <input id="customizationFile" class="file-input" type="file" :accept="FILE_ACCEPT" multiple @change="onPickFiles">
            </label>
            <p class="ml-hint">支持 PDF / AI / CDR / JPG / PNG，单个不超过 16MB，最多 5 个</p>
            <p v-if="fileError" class="ml-error">{{ fileError }}</p>
          </div>

          <template v-if="loggedIn">
            <MlField label="收货地址" required>
              <select id="customizationAddress" v-model.number="addressId" class="ml-select">
                <option v-if="!addresses.length" :value="null">暂无收货地址，请先在个人中心添加</option>
                <option v-for="a in addresses" :key="a.id" :value="a.id">
                  {{ a.name }} {{ a.mobile }} {{ a.areaName }} {{ a.detailAddress }}
                </option>
              </select>
            </MlField>
            <CheckoutInvoiceCard v-model:enabled="invoiceEnabled" v-model:form="invoiceForm" />
          </template>
          <p v-else class="ml-hint">
            上传文件与提交订单前需要登录。
            <button type="button" class="link-button" @click="openLogin">立即登录</button>
          </p>

          <p v-if="submitError" class="ml-error">{{ submitError }}</p>

          <div class="summary">
            <p><span>加工形式</span><b>{{ modeLabel }}</b></p>
            <p><span>材质</span><b>{{ currentMaterial?.name }}</b></p>
            <p><span>尺寸</span><b>{{ width }} × {{ height }} mm</b></p>
            <p>
              <span>成品形态</span>
              <b>{{ formLabel }}<template v-if="form === 2"> · {{ sheetSpec }}</template><template v-else> · 轴芯 {{ axisDiameter }}</template></b>
            </p>
            <p><span>处理</span><b>{{ cornerLabel }}<template v-if="tearLine"> · 撕裂线</template></b></p>
            <p><span>排版</span><b>{{ columnCount }} 列<template v-if="form === 2"> · {{ rowCount }} 行</template> · 纸宽 {{ estimatedPaperWidth }}mm</b></p>
            <p><span>数量</span><b>{{ quantity }}</b></p>
          </div>
        </div>
      </div>

      <aside class="ml-card quote-panel">
        <div class="ml-card-title">最终价格</div>
        <p v-if="quoting" class="ml-hint">正在计算…</p>
        <template v-else-if="quote">
          <div class="quote-row"><span>打印</span><b>{{ formatYuan(quote.breakdown.print) }}</b></div>
          <div v-if="quote.breakdown.printing" class="quote-row"><span>印刷</span><b>{{ formatYuan(quote.breakdown.printing) }}</b></div>
          <div v-if="quote.breakdown.dieCut" class="quote-row"><span>模切</span><b>{{ formatYuan(quote.breakdown.dieCut) }}</b></div>
          <div v-if="quote.breakdown.urgent" class="quote-row"><span>加急</span><b>{{ formatYuan(quote.breakdown.urgent) }}</b></div>
          <div class="quote-row"><span>运费</span><b>{{ formatYuan(quote.freight) }}</b></div>
          <div class="quote-row quote-total">
            <span>应付</span><b id="customizationQuoteTotal">{{ formatYuan(quote.payable) }}</b>
          </div>
          <p class="ml-hint">面积 {{ quote.totalArea }} ㎡，金额以提交时服务端报价为准</p>
        </template>
        <p v-else-if="quoteError" class="ml-error">{{ quoteError }}</p>
        <p v-else class="ml-hint">选择成品形态后自动计算价格</p>

        <button id="customizationCalc" class="btn-cyan quote-calc" type="button" @click="refreshQuote">
          计算价格
        </button>
        <button
          id="customizationSubmit"
          type="button"
          class="btn-buy quote-submit"
          :disabled="submitting || !quote || !showStep4"
          @click="submit()"
        >
          {{ submitting ? '提交中…' : '以上信息确认无误，立即提交' }}
        </button>
      </aside>
    </div>

    <MlModal :open="!!priceChanged" title="价格已更新" @close="priceChanged = null">
      <p>配置期间的参数发生了变化，请确认新价格后再提交：</p>
      <p v-if="priceChanged" class="price-change">{{ formatYuan(quote?.payable ?? 0) }}</p>
      <template #foot>
        <button class="btn-cyan" type="button" @click="priceChanged = null">先不提交</button>
        <button id="customizationConfirmPrice" class="btn-buy" type="button" @click="confirmNewPrice">按新价格提交</button>
      </template>
    </MlModal>
  </div>
</template>

<style scoped>
.customization-page { padding-bottom: 48px; }
.cr-breadcrumb { display: flex; gap: 8px; padding: 18px 0 10px; font-size: 13px; color: #5b6c8f; }
.step-card { margin-bottom: 16px; }
.step-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; font-size: 16px; font-weight: 600; }
.step-head i {
  display: inline-flex; align-items: center; justify-content: center; height: 28px; padding: 0 14px;
  border-radius: 4px; background: #0e7490; color: #fff; font-style: normal; font-size: 14px;
}
.mode-list { display: flex; flex-wrap: wrap; gap: 10px; }
.mode-item, .material-item {
  display: flex; flex-direction: column; gap: 4px; padding: 12px 16px;
  border: 1px solid #d9e2f0; border-radius: 8px; background: #fff; cursor: pointer; text-align: left;
}
.mode-item { min-width: 180px; }
.mode-item.is-active, .material-item.is-active { border-color: #0e7490; box-shadow: 0 0 0 1px #0e7490 inset; }
.mode-item span, .material-item span { font-size: 12px; color: #5b6c8f; }
.material-list { display: flex; flex-wrap: wrap; gap: 10px; }
.material-item { min-width: 170px; max-width: 260px; }
.material-item em { font-style: normal; font-size: 12px; color: #8a97b5; line-height: 1.4; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.seg-row { display: flex; gap: 10px; }
.seg-item { padding: 8px 18px; border: 1px solid #d9e2f0; border-radius: 8px; background: #fff; cursor: pointer; }
.seg-item.is-active { border-color: #0e7490; box-shadow: 0 0 0 1px #0e7490 inset; color: #0e7490; }
.inline-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 12px 0; }
.inline-label { color: #5b6c8f; }
.paper-width { color: #0e7490; font-weight: 600; }
.summary { margin-top: 16px; padding: 12px; border-radius: 8px; background: #f5f8fc; }
.summary p { display: flex; justify-content: space-between; margin: 6px 0; font-size: 13px; color: #5b6c8f; }
.summary b { color: #1f2d3d; font-weight: 600; }
.process-list { display: flex; flex-wrap: wrap; gap: 24px; margin-top: 8px; }
.file-title { margin: 12px 0 8px; font-weight: 600; }
.file-list { margin: 0 0 12px; padding: 0; list-style: none; }
.file-list li { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed #e6ecf5; }
.file-remove { border: 0; background: none; color: #b42318; cursor: pointer; }
.file-pick { display: inline-block; padding: 8px 16px; border: 1px dashed #9fb3d1; border-radius: 8px; cursor: pointer; color: #0e7490; }
.file-input { display: none; }
.link-button { border: 0; background: none; color: #0e7490; cursor: pointer; text-decoration: underline; }
.quote-panel { position: sticky; top: 16px; align-self: start; }
.quote-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; }
.quote-total { margin-top: 8px; padding-top: 12px; border-top: 1px solid #e6ecf5; font-size: 18px; }
.quote-calc, .quote-submit { width: 100%; margin-top: 10px; }
.price-change { font-size: 22px; font-weight: 700; }
.ml-20px { margin-left: 20px; }
@media (max-width: 720px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
