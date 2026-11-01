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
import CustomizationDiagram from '@/components/CustomizationDiagram.vue'
import CheckoutInvoiceCard from '@/components/CheckoutInvoiceCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import MlCheck from '@/components/base/MlCheck.vue'
import MlField from '@/components/base/MlField.vue'
import MlModal from '@/components/base/MlModal.vue'
import { InvoiceTitleKind, InvoiceType, type Address, type InvoiceApplyForm } from '@/types'
import { useUserStore } from '@/store/user'
import { formatYuan } from '@/utils/money'

/**
 * 印刷定制（014）—— 单页渐进式四步（按参考站点截图）：
 *   选择第一步后，下面依次出现第二步 / 第三步 / 第四步；没有「上一步 / 下一步」。
 *
 * 价格仍然只来自服务端 `/quote`（宪法 III）；排版字段只随订单保存、作为生产要求。
 */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const FILE_ACCEPT = '.pdf,.ai,.cdr,.jpg,.jpeg,.png'
const FILE_EXTENSIONS = ['pdf', 'ai', 'cdr', 'jpg', 'jpeg', 'png']
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
/** 改价前的应付金额：弹层要展示「贵了多少」（Q101），只给新价等于让客户盲签 */
const priceChangedFrom = ref<number | null>(null)

const currentMaterial = computed(() => materials.value.find((m) => m.id === materialId.value) ?? null)
const loggedIn = computed(() => userStore.isLogin)
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
const sheetLayoutHeight = computed(() => height.value ? rowCount.value * (height.value + FIXED_GAP) : 0)
const sheetOverflow = computed(() => form.value === 2 && sheetSpec.value === 'A4'
  && (estimatedPaperWidth.value > 210 || sheetLayoutHeight.value > 297))
const configComplete = computed(() => {
  const m = currentMaterial.value
  return !!m && mode.value !== null && form.value !== null
    && Number.isFinite(width.value) && Number.isFinite(height.value) && Number.isInteger(quantity.value)
    && (width.value ?? 0) >= m.minWidth && (width.value ?? 0) <= m.maxWidth
    && (height.value ?? 0) >= m.minHeight && (height.value ?? 0) <= m.maxHeight
    && (quantity.value ?? 0) >= m.minQuantity && (quantity.value ?? 0) <= m.maxQuantity
    && Number.isInteger(columnCount.value) && columnCount.value >= 1 && columnCount.value <= 8
    && (form.value !== 2 || (Number.isInteger(rowCount.value) && rowCount.value >= 1 && !sheetOverflow.value))
})
const canSubmit = computed(() => loggedIn.value && configComplete.value && !!quote.value && !quoting.value && !uploading.value && !submitting.value)
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
let quoteRevision = 0
let quoteTimer: ReturnType<typeof setTimeout> | null = null

function scheduleQuote(): void {
  quoteRevision++
  quote.value = null
  quoting.value = loggedIn.value && configComplete.value
  if (quoteTimer) clearTimeout(quoteTimer)
  quoteTimer = setTimeout(() => {
    void refreshQuote()
  }, 300)
}

async function refreshQuote(): Promise<void> {
  const revision = ++quoteRevision
  if (!loggedIn.value || !configComplete.value || materialId.value === null || !width.value || !height.value || !quantity.value) {
    quote.value = null
    quoting.value = false
    return
  }
  quoting.value = true
  quoteError.value = ''
  try {
    const result = await quoteCustomization({
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
    if (revision === quoteRevision && loggedIn.value) quote.value = result
  } catch (err) {
    if (revision !== quoteRevision) return
    quote.value = null
    quoteError.value = err instanceof Error ? err.message : '报价失败，请稍后重试'
  } finally {
    if (revision === quoteRevision) quoting.value = false
  }
}

watch([mode, materialId, width, height, quantity, form, sheetSpec, finishing, tearLine,
  rowCount, columnCount, axisDiameter, urgent, addressId, loggedIn], scheduleQuote, { flush: 'sync' })

watch(loggedIn, (active) => {
  priceChanged.value = null
  if (active) void load()
  else {
    addresses.value = []
    addressId.value = null
  }
})

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
      // `accept` 只是选择器的提示，用户仍可切到「所有文件」—— 白名单要自己判
      const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
      if (!FILE_EXTENSIONS.includes(ext)) {
        fileError.value = `不支持的文件格式：${file.name}（仅支持 PDF / AI / CDR / JPG / PNG）`
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
  if (!configComplete.value || quoting.value || uploading.value || submitting.value) return
  if (!validateSize() || !validateQuantity()) return
  if (!loggedIn.value) {
    openLogin()
    return
  }
  if (!quote.value) {
    submitError.value = '请等待自动报价完成'
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
      // 先留住旧价，再让新报价覆盖面板
      priceChangedFrom.value = quote.value?.payable ?? null
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

    <section class="custom-hero">
      <div class="hero-copy"><span class="hero-eyebrow">MAXLABEL · 标签定制</span><h1>让每一枚标签，都恰到好处。</h1><p>从材质到成品，一步步选出适合您的标签。边选边看效果，确认规格后即可下单。</p><div class="hero-points"><span>按需选择加工方式</span><span>规格实时示意</span><span>费用明细可查看</span></div></div>
      <div class="hero-art"><CustomizationDiagram :mode="mode || 3" :width="width || 60" :height="height || 40" :material="currentMaterial?.name" /><span>您的标签，由您定义</span></div>
    </section>


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

    <div v-else class="ml-2col customization-layout">
      <div>
        <!-- 第一步：加工形式 -->
        <div class="ml-card customization-step">
          <div class="step-head">您想制作哪种标签？</div>
          <div class="mode-list">
            <button
              v-for="m in MODE_OPTIONS"
              :id="`customizationMode-${m.value}`"
              :key="m.value"
              type="button"
              class="mode-item"
              :class="{ 'is-active': mode === m.value }"
              :aria-pressed="mode === m.value"
              @click="mode = m.value"
            >
              <CustomizationDiagram :mode="m.value" />
              <b>{{ m.label }}</b><span>{{ m.desc }}</span><small>{{ m.value === 1 ? '适合品牌贴纸、商品彩标' : m.value === 2 ? '适合条码、编号等可变内容' : m.value === 3 ? '品牌图案与条码同时呈现' : '适合自有设备后续打印' }}</small>
            </button>
          </div>
        </div>

        <!-- 第二步：材质 -->
        <div v-if="showStep2" class="ml-card customization-step">
          <div class="step-head">选择适合使用场景的材质</div>
          <div class="material-list">
            <button
              v-for="m in materials"
              :id="`customizationMaterial-${m.id}`"
              :key="m.id"
              type="button"
              class="material-item"
              :class="{ 'is-active': m.id === materialId }"
              :aria-pressed="materialId === m.id"
              @click="pickMaterial(m.id)"
            >
              <CustomizationDiagram kind="material" :material="m.name" />
              <b>{{ m.name }}</b>
              <span>{{ m.minWidth }}~{{ m.maxWidth }}mm</span>
              <em v-if="m.description">{{ m.description }}</em>
            </button>
          </div>
          <p class="section-help">材质示意用于区分外观，实际颜色与质感以实物为准。选中材质后可查看可定制范围。</p>
          <p v-if="materialHint" class="ml-hint">{{ materialHint }}</p>
        </div>

        <!-- 第三步：成品形态 -->
        <div v-if="showStep3" class="ml-card customization-step">
          <div class="step-head">设置成品规格与排版</div>

          <MlField label="成品形态" required>
            <div class="seg-row form-choices">
              <button
                v-for="f in FORM_OPTIONS"
                :id="`customizationForm-${f.value}`"
                :key="f.value"
                type="button"
                class="seg-item"
                :class="{ 'is-active': form === f.value }"
                :aria-pressed="form === f.value"
                @click="form = f.value"
              >
                <CustomizationDiagram kind="layout" :form="f.value" :columns="2" :rows="3" />
                <b>{{ f.label }}</b><small>{{ f.value === 1 ? '连续出标，搭配标签打印机' : '整张交付，按行列排列' }}</small>
              </button>
            </div>
          </MlField>

          <div class="spec-guide"><CustomizationDiagram kind="label" :mode="mode" :width="width" :height="height" :rounded="finishing === 1" :material="currentMaterial?.name" /><div><b>填写的是单枚标签尺寸</b><p>宽度是横向尺寸，高度是纵向尺寸，单位为毫米（mm）。排版列数决定一行有几枚标签。</p><span>示意图用于理解规格，非印刷稿或实际比例。</span></div></div>
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
            <div class="seg-row axis-choices">
              <button
                v-for="a in AXIS_OPTIONS"
                :id="`customizationAxis-${a}`"
                :key="a"
                type="button"
                class="seg-item"
                :class="{ 'is-active': axisDiameter === a }"
                :aria-pressed="axisDiameter === a"
                @click="axisDiameter = a"
              >
                <CustomizationDiagram kind="core" :axis="a" />{{ a }}
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
              :aria-pressed="finishing === c.value"
              @click="finishing = c.value"
            >
              <CustomizationDiagram kind="corner" :rounded="c.value === 1" />{{ c.label }}
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
            <span v-if="form === 2" class="paper-width">排版宽度：{{ estimatedPaperWidth }}mm</span>
            <span class="ml-20px">行间距：{{ FIXED_GAP }}mm　列间距：{{ FIXED_GAP }}mm</span>
          </div>

          <p class="section-help">撕裂线位于标签行之间的底纸上，便于沿线分段撕取；蓝色虚线仅表示位置。轴芯内径请按打印机支持的规格选择。</p>
          <MlField v-if="form === 2" label="每张行数">
            <input id="customizationRows" v-model.number="rowCount" class="ml-input !w-120px" type="number" min="1">
          </MlField>
        </div>

        <!-- 第四步：数量与提交 -->
        <div v-if="showStep4" class="ml-card customization-step">
          <div class="step-head">确认数量，上传文件并下单</div>

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
            <div class="file-title">上传设计图</div>
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


        </div>
      </div>

      <aside class="ml-card quote-panel">
        <div class="quote-scroll">
        <div class="preview-heading"><span>您的定制方案</span><small>随选择实时更新</small></div>
        <div class="live-preview"><CustomizationDiagram kind="layout" :form="form || 2" :columns="columnCount" :rows="rowCount" :sheet-spec="sheetSpec" :width="width" :height="height" :rounded="finishing === 1" :tear-line="tearLine" :material="currentMaterial?.name" /></div>
        <p class="preview-caption">{{ formLabel || '选择成品形态后查看排版' }}<template v-if="tearLine"> · 蓝色虚线为撕裂线</template></p>
        <p v-if="sheetOverflow" class="ml-error">当前排版超出 A4（210 × 297 mm），请调整标签尺寸或行列数。</p>
        <p v-else-if="form === 2" class="preview-caption">排版尺寸含行列间距；纸张内按实际比例展示。</p>
        <dl class="live-spec">
          <div><dt>加工方式</dt><dd>{{ modeLabel || '待选择' }}</dd></div>
          <div><dt>标签材质</dt><dd>{{ currentMaterial?.name || '待选择' }}</dd></div>
          <div><dt>单枚尺寸</dt><dd>{{ width && height ? `${width} × ${height} mm` : '待填写' }}</dd></div>
          <div><dt>成品形态</dt><dd>{{ formLabel || '待选择' }}<template v-if="form === 2"> · {{ sheetSpec }}</template></dd></div>
          <div v-if="form === 1"><dt>轴芯内径</dt><dd>{{ axisDiameter }}</dd></div>
          <div><dt>边角</dt><dd>{{ cornerLabel }}</dd></div>
          <div><dt>撕裂线</dt><dd>{{ tearLine ? '需要' : '不需要' }}</dd></div>
          <div><dt>排版</dt><dd>{{ columnCount }} 列<template v-if="form === 2"> · {{ rowCount }} 行</template></dd></div>
          <div v-if="form === 2"><dt>纸张尺寸</dt><dd>{{ sheetSpec === 'A4' ? '210 × 297 mm' : '按排版尺寸定制' }}</dd></div>
          <div v-if="form === 2"><dt>排版尺寸</dt><dd>{{ width && height ? `${estimatedPaperWidth} × ${sheetLayoutHeight} mm` : '待填写' }}</dd></div>
          <div><dt>行 / 列间距</dt><dd>{{ FIXED_GAP }} / {{ FIXED_GAP }} mm</dd></div>
          <div><dt>定制数量</dt><dd>{{ quantity ? `${quantity} 枚` : '待填写' }}</dd></div>
          <div><dt>加急处理</dt><dd>{{ urgent ? '需要' : '不需要' }}</dd></div>
        </dl>
        <template v-if="loggedIn">
        <div class="ml-card-title">费用明细</div>
        <p v-if="quoting" class="ml-hint">正在计算…</p>
        <template v-else-if="quote">
          <div class="quote-row"><span>打印</span><b>{{ formatYuan(quote.breakdown.print) }}</b></div>
          <div v-if="quote.breakdown.printing" class="quote-row"><span>印刷</span><b>{{ formatYuan(quote.breakdown.printing) }}</b></div>
          <div v-if="quote.breakdown.dieCut" class="quote-row"><span>模切</span><b>{{ formatYuan(quote.breakdown.dieCut) }}</b></div>
          <div v-if="quote.breakdown.urgent" class="quote-row"><span>加急</span><b>{{ formatYuan(quote.breakdown.urgent) }}</b></div>
          <div class="quote-row"><span>运费</span><b>{{ formatYuan(quote.freight) }}</b></div>

          <p class="ml-hint">面积 {{ quote.totalArea }} ㎡，金额以提交时服务端报价为准</p>
        </template>
        <p v-else-if="quoteError" class="ml-error">{{ quoteError }}</p>
        <p v-else class="ml-hint">选择成品形态后自动计算价格</p>

        </template>
        </div>
        <div class="quote-actions">
          <div class="quote-total fixed-total"><span>总价</span><b v-if="loggedIn && quote && !quoting" id="customizationQuoteTotal">{{ formatYuan(quote.payable) }}</b><span v-else>{{ !loggedIn ? '登录后查看报价' : quoting ? '报价更新中…' : '待完成配置' }}</span></div>
          <p v-if="!configComplete" class="submit-hint">请先完成有效的定制配置</p>
          <p v-else-if="quoting" class="submit-hint">正在更新报价，请稍候</p>
        <button
          id="customizationSubmit"
          type="button"
          class="btn-buy quote-submit"
          :disabled="loggedIn ? !canSubmit : !configComplete"
          @click="loggedIn ? submit() : openLogin()"
        >
          {{ !loggedIn ? '登录查看报价并下单' : submitting ? '提交中…' : '确认配置，提交订单' }}
        </button>
        </div>
      </aside>
    </div>

    <section class="order-guide"><div><span>下单前了解</span><h2>从一份设计，到一枚好标签。</h2></div><div><b>确认使用环境</b><p>根据贴标表面、使用环境和打印设备选择材质；不确定时可先咨询客服。</p></div><div><b>准备印刷文件</b><p>支持 PDF、AI、CDR 及图片。请确保尺寸、文字、条码清晰；可变内容要求请在备注中说明。</p></div><div><b>核对再提交</b><p>检查单枚尺寸、行列数、轴芯和数量。示意图辅助选型，具体交期由客服沟通确认。</p></div></section>

    <MlModal :open="!!priceChanged" title="价格已更新" @close="priceChanged = null">
      <p>配置期间的参数发生了变化，请确认新价格后再提交：</p>
      <div v-if="priceChanged" class="price-change-table">
        <p><span>原价</span><b>{{ formatYuan(priceChangedFrom ?? 0) }}</b></p>
        <p><span>新价</span><b class="price-change">{{ formatYuan(quote?.payable ?? 0) }}</b></p>
        <p>
          <span>差额</span>
          <b class="price-change">{{ formatYuan((quote?.payable ?? 0) - (priceChangedFrom ?? 0)) }}</b>
        </p>
      </div>
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
.customization-step { margin-bottom: 16px; }
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
.quote-submit { width: 100%; margin-top: 10px; }
.price-change { font-size: 22px; font-weight: 700; }
.price-change-table { margin: 8px 0; }
.price-change-table p { display: flex; justify-content: space-between; align-items: baseline; margin: 6px 0; font-size: 14px; }
.price-change-table span { color: #5b6c8f; }
.ml-20px { margin-left: 20px; }
@media (max-width: 768px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>

<style scoped>
.custom-hero { display: grid; grid-template-columns: 1fr 290px; align-items: center; gap: 40px; padding: 32px 40px; background: linear-gradient(115deg, #eaf1ff, #f5f8ff); border: 1px solid #e3e9f4; border-radius: 16px; margin: 12px 0 24px; overflow: hidden; }
.hero-eyebrow { color: #2e7cd6; font-size: 12px; letter-spacing: 2px; font-weight: 700; }
.hero-copy h1 { color: #16233f; font-size: 32px; letter-spacing: -1px; margin: 12px 0; line-height: 1.4; }
.hero-copy p { color: #5b6c8f; line-height: 1.9; max-width: 580px; }
.hero-points { display: flex; gap: 20px; flex-wrap: wrap; color: #2e7cd6; font-size: 12px; margin-top: 20px; }
.hero-points span::before { content: '✓'; margin-right: 6px; }
.hero-art { text-align: center; transform: rotate(-4deg); }
.hero-art > span { color: #5b6c8f; font-size: 12px; letter-spacing: 3px; }
.custom-progress { display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid #e3e9f4; border-radius: 10px; background: #ffffff; margin-bottom: 24px; }
.custom-progress > span { padding: 17px; font-size: 14px; color: #5b6c8f; }
.custom-progress b { color: #8a97b5; margin-right: 8px; }
.custom-progress em { display: block; font-style: normal; font-size: 11px; margin: 6px 0 0 27px; color: #8a97b5; }
.custom-progress .done b, .custom-progress .done { color: #2e7cd6; }
.customization-layout > div { min-width: 0; }
.customization-layout { grid-template-columns: minmax(0, 1fr) 330px; gap: 24px; }
.customization-step { padding: 24px; border: 1px solid #e3e9f4; box-shadow: none; border-radius: 12px; margin-bottom: 20px; }
.step-head { color: #16233f; font-size: 18px; margin-bottom: 22px; }
.step-head i { background: #eaf1ff; color: #2e7cd6; font-size: 12px; padding: 0 10px; }
.mode-list, .material-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.mode-item, .material-item { min-width: 0; max-width: none; padding: 12px 16px 18px; color: #16233f; position: relative; transition: border-color .15s, background .15s; }
.mode-item:hover, .material-item:hover, .seg-item:hover { border-color: #2e7cd6; background: #f5f8ff; }
.mode-item.is-active, .material-item.is-active, .seg-item.is-active { border-color: #2e7cd6; box-shadow: 0 0 0 1px #2e7cd6 inset; background: #f5f8ff; color: #2e7cd6; }
.mode-item.is-active::after, .material-item.is-active::after { content: '✓'; position: absolute; right: 12px; top: 12px; background: #2e7cd6; color: #ffffff; width: 20px; height: 20px; border-radius: 50%; text-align: center; }
.mode-item .customization-diagram { max-height: 125px; }
.mode-item small, .form-choices small { font-size: 11px; color: #8a97b5; font-weight: 400; }
.material-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.material-item .customization-diagram { max-height: 100px; }
.material-item em { color: #5b6c8f; }
.section-help { color: #5b6c8f; font-size: 12px; line-height: 1.8; margin: 14px 0; }
.form-choices .seg-item { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 12px; gap: 6px; }
.form-choices .customization-diagram { max-height: 140px; }
.spec-guide { display: flex; gap: 20px; align-items: center; padding: 16px; background: #f5f8ff; border-radius: 8px; margin: 20px 0; }
.spec-guide .customization-diagram { width: 160px; flex-shrink: 0; }
.spec-guide b { font-size: 14px; color: #16233f; }
.spec-guide p, .spec-guide span { color: #5b6c8f; font-size: 12px; line-height: 1.8; }
.axis-choices .seg-item { width: 110px; padding: 6px; font-size: 12px; }
.inline-row .seg-item .customization-diagram { width: 58px; height: 45px; }
.quote-panel { padding: 22px; border: 1px solid #e3e9f4; border-radius: 12px; top: 20px; }
.preview-heading { display: flex; justify-content: space-between; align-items: center; font-weight: 700; color: #16233f; }
.preview-heading small { font-weight: 400; color: #8a97b5; font-size: 11px; }
.live-preview { background: #f5f8ff; border-radius: 8px; padding: 12px; margin-top: 16px; }
.preview-caption { color: #5b6c8f; font-size: 11px; text-align: center; margin-top: 8px; }
.live-spec { padding: 12px 0; border-bottom: 1px solid #e3e9f4; margin-bottom: 20px; }
.live-spec > div { display: flex; justify-content: space-between; gap: 12px; padding: 7px 0; font-size: 12px; }
.live-spec dt { color: #8a97b5; flex-shrink: 0; }.live-spec dd { margin: 0; color: #16233f; text-align: right; }
.quote-total b { color: #ff5c22; font-size: 26px; }.quote-submit { min-height: 46px; }
.file-pick { display: block; text-align: center; padding: 24px; background: #f5f8ff; }
.order-guide { display: grid; grid-template-columns: 1.1fr 1fr 1fr 1fr; gap: 30px; padding: 30px 0; margin-top: 16px; border-top: 1px solid #e3e9f4; }
.order-guide span { color: #2e7cd6; font-size: 12px; }.order-guide h2 { color: #16233f; font-size: 20px; line-height: 1.5; }.order-guide b { color: #16233f; font-size: 13px; }.order-guide p { color: #5b6c8f; font-size: 12px; line-height: 1.9; }
button:focus-visible { outline: 3px solid #35a5ff; outline-offset: 3px; }
@media (max-width: 1100px) { .customization-layout { grid-template-columns: minmax(0, 1fr) 290px; gap: 16px; }.customization-step { padding: 18px; }.material-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 768px) { .custom-hero { grid-template-columns: 1fr; padding: 24px; gap: 8px; }.hero-copy h1 { font-size: 26px; }.hero-art { display: none; }.customization-layout { grid-template-columns: 1fr; }.quote-panel { position: static; }.custom-progress { grid-template-columns: repeat(2, 1fr); }.custom-progress > span { padding: 12px; }.order-guide { grid-template-columns: repeat(2, 1fr); gap: 20px; }.step-head { font-size: 16px; }.spec-guide { gap: 12px; }.spec-guide .customization-diagram { width: 120px; } }
@media (max-width: 480px) { .mode-list { grid-template-columns: 1fr; }.mode-item { display: grid; grid-template-columns: 110px 1fr; align-items: center; column-gap: 12px; }.mode-item .customization-diagram { grid-row: span 3; }.hero-points { gap: 10px; }.customization-step { padding: 16px; }.spec-guide { flex-direction: column; }.form-grid { grid-template-columns: 1fr; }.order-guide { grid-template-columns: 1fr; }.form-choices small { line-height: 1.6; } }
</style>

<style scoped>
.quote-panel { position: sticky; top: 96px; max-height: calc(100dvh - 112px); display: flex; flex-direction: column; padding: 0; overflow: hidden; }
.quote-scroll { padding: 22px; overflow-y: auto; min-height: 0; overscroll-behavior: contain; }
.quote-actions { flex-shrink: 0; padding: 12px 22px 18px; background: #ffffff; border-top: 1px solid #e3e9f4; }
.submit-hint { margin: 0 0 8px; color: #5b6c8f; font-size: 12px; }
.quote-submit { margin-top: 0; }
.quote-submit:disabled { opacity: .45; cursor: not-allowed; }
@media (max-width: 768px) {
  .customization-page { padding-bottom: 140px; }
  .quote-panel { position: static; max-height: none; overflow: visible; }
  .quote-scroll { overflow: visible; }
  .quote-actions { position: fixed; bottom: 0; left: 0; right: 0; z-index: 60; padding: 12px 20px calc(12px + env(safe-area-inset-bottom)); box-shadow: 0 -4px 20px #e3e9f4; }
}
</style>

<style scoped>
.fixed-total { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin: 0 0 10px; padding: 0; border: 0; font-size: 14px; }
.fixed-total > span:last-child { color: #5b6c8f; font-size: 13px; }
@media (max-width: 768px) { .customization-page { padding-bottom: 170px; } }
</style>
