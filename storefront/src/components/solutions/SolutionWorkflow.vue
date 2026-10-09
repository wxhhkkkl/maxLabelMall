<script setup lang="ts">
import { computed } from 'vue'
import type { IndustrySolution } from '@/data/industrySolutions'

const props = defineProps<{ industry: IndustrySolution }>()
const presentations = {
  warehouse: { mode: 'trace', title: '货在移动，身份始终对应', description: '用商品、库位和箱号，建立每次交接的查找路径。', identity: '出库箱档案', note: '商品编号 / 库位 / 装箱清单' },
  manufacturing: { mode: 'trace', title: '让同一个批次，走完每道工序', description: '容器与工序可以变化，物料身份持续保留。', identity: '生产批次档案', note: '原料批次 / 当前工序 / 质检状态' },
  crossborder: { mode: 'trace', title: '选对渠道版式，再按箱交付', description: '渠道规则与商品数据分开维护，箱号与清单配对复核。', identity: '出货任务档案', note: '目的仓 / 渠道版式 / 箱号与清单' },
  semiconductor: { mode: 'trace', title: '从一盘料，找到关联的批次', description: '料盘有独立身份，领用与包装保留对应关系。', identity: '料盘身份档案', note: '物料型号 / 来料批次 / 领用记录' },
  bakery: { mode: 'branch', title: '一份配方档案，表达三种门店信息', description: '固定配方统一维护，当批出炉时间单独更新。', identity: '烘焙产品档案', note: '产品名称 · 配料 · 规格 · 过敏原' },
  apparel: { mode: 'branch', title: '同一款商品，三种标签各有分工', description: '款号、颜色与尺码保持一致，按用途组织不同版式。', identity: '简约针织衫 · 浅蓝 / L', note: '款号 · 颜色尺码 · 面料成分' },
  retail: { mode: 'task', title: '一次改价，按货架逐区完成', description: '让更新、换标和核对成为一份清楚的门店任务。', identity: '门店换标任务', note: '商品 / 当前价格 / 活动期限 / 货架区域' },
  food: { mode: 'layers', title: '配方保持一致，日期随批次更新', description: '把固定信息与生产变量分开，减少反复改动整张标签。', identity: '产品信息', note: '固定档案 + 本批次信息 → 包装标识' },
  catering: { mode: 'timeline', title: '沿着时间，读懂食材的每次交接', description: '拆开原包装之后，仍然保留来源与分装记录。', identity: '来源批次贯穿周转', note: '以实际食材要求填写储存与使用期限' },
  medical: { mode: 'check', title: '把关键核对点，留在打印前后', description: '先核资料与版本，再核样张与记录，逐项确认关键信息。', identity: '产品身份 / 批号 / 效期', note: '资料、样张与归档记录相互对应' },
} as const
const presentation = computed(() => presentations[props.industry.id as keyof typeof presentations])
const branchNotes = computed(() => props.industry.id === 'bakery'
  ? ['展示配料、规格与过敏原', '填写本批次的出炉时间', '呈现商品名称与在售价格']
  : ['展示款色码与商品信息', '突出面料成分与护理方式', '用于包装、分码和入库识别'])
</script>

<template>
  <section class="solution-method-section">
    <div class="solution-container solution-section">
      <div class="solution-section-heading">
        <h2>{{ presentation.title }}</h2>
        <p>{{ presentation.description }}</p>
      </div>
      <div
        class="solution-business-flow"
        :class="'flow-' + presentation.mode"
      >
        <template v-if="presentation.mode === 'branch'">
          <div class="flow-source">
            <span class="flow-kicker">共享产品档案</span>
            <h3>{{ presentation.identity }}</h3>
            <p>{{ presentation.note }}</p>
          </div>
          <div class="flow-variable">
            <span>{{ industry.id === 'bakery' ? '当批生产信息' : '按款色码区分' }}</span>
            <p>{{ industry.id === 'bakery' ? '出炉时间与批次更新，不重复修改配方。' : '基础款式信息复用，颜色与尺码明确对应。' }}</p>
          </div>
          <div class="flow-branches">
            <article
              v-for="(sample, index) in industry.samples"
              :key="sample.name"
            >
              <span class="flow-output">{{ ['包装识别', '信息提示', '现场使用'][index] }}</span>
              <h3>{{ sample.name }}</h3>
              <p>{{ branchNotes[index] }}</p>
            </article>
          </div>
        </template>
        <template v-else-if="presentation.mode === 'trace'">
          <figure class="flow-scene">
            <img
              :src="industry.image"
              :alt="industry.name + '信息交接场景'"
              loading="lazy"
            >
            <figcaption><span class="flow-kicker">{{ presentation.identity }}</span><h3>{{ presentation.note }}</h3><p>每个交接点都保留可查找的对应信息。</p></figcaption>
          </figure>
          <ol class="flow-stages">
            <li
              v-for="([title, description], index) in industry.steps"
              :key="title"
            >
              <span class="flow-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div><h3>{{ title }}</h3><p>{{ description }}</p></div>
            </li>
          </ol>
        </template>
        <template v-else-if="presentation.mode === 'layers'">
          <div
            v-for="(label, group) in ['固定产品档案', '每批次更新']"
            :key="label"
            class="flow-layer"
          >
            <span class="flow-kicker">{{ group === 0 ? 'PRODUCT' : 'BATCH' }}</span>
            <h3>{{ label }}</h3>
            <ol>
              <li
                v-for="([title, description], index) in industry.steps.slice(group * 2, group * 2 + 2)"
                :key="title"
              >
                <span class="flow-index">{{ group * 2 + index + 1 }}</span><div><h4>{{ title }}</h4><p>{{ description }}</p></div>
              </li>
            </ol>
          </div>
          <p class="flow-footnote">
            {{ presentation.note }}
          </p>
        </template>
        <template v-else-if="presentation.mode === 'timeline'">
          <div class="flow-timeline-caption">
            <h3>{{ presentation.identity }}</h3><p>{{ presentation.note }}</p>
          </div>
          <ol class="flow-time-stages">
            <li
              v-for="([title, description], index) in industry.steps"
              :key="title"
            >
              <span class="flow-time-label">{{ ['来源', '分装', '储存', '领用'][index] }}</span>
              <span
                class="flow-time-dot"
                aria-hidden="true"
              />
              <h3>{{ title }}</h3><p>{{ description }}</p>
            </li>
          </ol>
        </template>
        <template v-else>
          <div class="flow-check-caption">
            <span class="flow-kicker">{{ presentation.identity }}</span><p>{{ presentation.note }}</p>
          </div>
          <ol class="flow-check-stages">
            <li
              v-for="([title, description], index) in industry.steps"
              :key="title"
            >
              <span class="flow-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div><h3>{{ title }}</h3><p>{{ description }}</p><span class="flow-check-note">{{ presentation.mode === 'task' ? ['商品与货架对应', '新价格与期限确认', '区域清单便于领取', '商品、价格、位置核对'][index] : ['产品资料与版本', '批号、效期与规格', '内容清晰且可读', '批次与复核记录'][index] }}</span></div>
            </li>
          </ol>
        </template>
      </div>
    </div>
  </section>
</template>
