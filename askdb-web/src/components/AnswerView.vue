<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import type { AskResult } from '../types'
import { planChart } from '../utils/chart'
import { downloadCsv, formatDuration, formatValue } from '../utils/format'
import { hintFor } from '../utils/hints'
import ResultTable from './ResultTable.vue'
import SqlBlock from './SqlBlock.vue'

// 图表库较大，第一次需要画图时再加载，首屏更快
const ResultChart = defineAsyncComponent(() => import('./ResultChart.vue'))

const props = defineProps<{ answer: AskResult }>()

type View = 'value' | 'chart' | 'table' | 'sql'
const VIEW_LABELS: Record<View, string> = { value: '结果', chart: '图表', table: '表格', sql: 'SQL' }

const plan = computed(() => planChart(props.answer.result))

/** 能展示哪些视图：单个值 → 结果/SQL；能画图 → 图表/表格/SQL；其他 → 表格/SQL */
const views = computed<View[]>(() => {
  if (plan.value.kind === 'value') return ['value', 'sql']
  if (plan.value.kind === 'none') return ['table', 'sql']
  return ['chart', 'table', 'sql']
})

const view = ref<View>(views.value[0])
watch(views, (v) => (view.value = v[0]))

const failure = computed(() => {
  switch (props.answer.status) {
    case 'GENERATION_FAILED':
      return { tone: 'danger', title: '没有生成 SQL' }
    case 'REJECTED':
      return { tone: 'warn', title: '已拦截，没有执行' }
    case 'EXECUTION_FAILED':
      return { tone: 'warn', title: '生成的 SQL 执行出错' }
    default:
      return null
  }
})

const hint = computed(() => hintFor(props.answer.error))

const copied = ref(false)
async function copySql() {
  if (!props.answer.sql) return
  try {
    await navigator.clipboard.writeText(props.answer.sql)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // 非 https 或浏览器拒绝时复制会失败，此时可以在 SQL 视图里手动选中复制
    view.value = 'sql'
  }
}

function exportCsv() {
  const result = props.answer.result
  if (!result) return
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')
  downloadCsv(result, `askdb-${stamp}.csv`)
}
</script>

<template>
  <div class="answer">
    <!-- 失败：说明原因，有 SQL 就一起显示，方便看错在哪 -->
    <template v-if="failure">
      <div class="notice" :class="failure.tone" role="alert">
        <p class="notice-title">{{ failure.title }}</p>
        <p v-if="answer.error" class="notice-detail">{{ answer.error }}</p>
        <p v-if="hint" class="notice-hint">{{ hint }}</p>
      </div>
      <SqlBlock v-if="answer.sql" :sql="answer.sql" class="failed-sql" />
      <p v-if="answer.trace?.corrections" class="trace">已根据报错自动修正 {{ answer.trace.corrections }} 次，仍然没有成功，上面是最后一次的 SQL。</p>
    </template>

    <!-- 成功 -->
    <template v-else-if="answer.result">
      <div class="toolbar">
        <div class="tabs" role="tablist" aria-label="查看方式">
          <button
            v-for="v in views"
            :key="v"
            type="button"
            role="tab"
            :aria-selected="view === v"
            :class="{ on: view === v }"
            @click="view = v"
          >
            {{ VIEW_LABELS[v] }}
          </button>
        </div>
        <div class="actions">
          <button type="button" :disabled="!answer.sql" @click="copySql">{{ copied ? '已复制' : '复制 SQL' }}</button>
          <button type="button" :disabled="answer.result.rows.length === 0" @click="exportCsv">导出 CSV</button>
        </div>
      </div>

      <div class="body">
        <div v-if="view === 'value' && plan.kind === 'value'" class="value">
          <span class="value-number">{{ formatValue(plan.value) }}</span>
          <span class="value-label">{{ plan.label }}</span>
        </div>
        <ResultChart
          v-else-if="view === 'chart' && (plan.kind === 'bar' || plan.kind === 'line')"
          :result="answer.result"
          :kind="plan.kind"
          :dimension="plan.dimension"
          :measures="plan.measures"
        />
        <ResultTable v-else-if="view === 'table'" :result="answer.result" />
        <SqlBlock v-else-if="view === 'sql' && answer.sql" :sql="answer.sql" />
        <p v-if="answer.result.rows.length === 0 && view !== 'sql'" class="no-rows">查询成功，但没有符合条件的数据。</p>
      </div>

      <p v-if="answer.trace" class="trace">
        <span><template v-if="answer.trace.tables.length < answer.trace.totalTables">从 {{ answer.trace.totalTables }} 张表里选了 {{ answer.trace.tables.length }} 张：</template><template v-else>用到全部表：</template>{{ answer.trace.tables.join('、') }}</span>
        <span v-if="answer.trace.terms.length">业务术语：{{ answer.trace.terms.join('、') }}</span>
        <span v-if="answer.trace.examples">参考了 {{ answer.trace.examples }} 条相似问题</span>
        <span v-if="answer.trace.corrections">根据报错自动修正了 {{ answer.trace.corrections }} 次</span>
        <span v-if="answer.trace.toolCalls?.length">写 SQL 前查看了数据：{{ answer.trace.toolCalls.join('；') }}</span>
      </p>

      <p class="footnote">
        <span>{{ answer.result.rows.length }} 行<template v-if="answer.result.truncated">（超出上限，只返回前 {{ answer.result.rows.length }} 行）</template></span>
        <span>用时 {{ formatDuration(answer.elapsedMs) }}</span>
        <span v-if="answer.usage">输入 {{ answer.usage.promptTokens }} tokens，输出 {{ answer.usage.completionTokens }}</span>
        <span v-if="answer.cached">来自缓存，未调用模型</span>
      </p>
    </template>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.tabs {
  display: inline-flex;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.tabs button {
  padding: 3px 14px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ink-2);
  font-size: 13px;
}

.tabs button.on {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 4px;
}

.actions button {
  padding: 3px 10px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--ink-2);
  font-size: 13px;
}

.actions button:hover:not(:disabled) {
  background: var(--surface);
  color: var(--accent);
}

.actions button:disabled {
  color: var(--line-strong);
  cursor: not-allowed;
}

.body {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  overflow: hidden;
}

.value {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 24px 24px 20px;
}

.value-number {
  font-size: 40px;
  font-weight: 600;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.value-label {
  color: var(--muted);
  font-size: 13px;
}

.no-rows {
  margin: 0;
  padding: 16px;
  color: var(--muted);
  font-size: 14px;
}

.trace {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin: 10px 2px 0;
  color: var(--ink-2);
  font-size: 12.5px;
}

.footnote {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin: 8px 2px 0;
  color: var(--muted);
  font-size: 12.5px;
}

.notice {
  padding: 10px 14px;
  border-left: 3px solid;
  border-radius: 0 var(--radius) var(--radius) 0;
  font-size: 14px;
}

.notice.danger {
  border-color: var(--danger);
  background: var(--danger-soft);
}

.notice.warn {
  border-color: var(--warn);
  background: var(--warn-soft);
}

.notice p {
  margin: 0;
}

.notice-title {
  font-weight: 600;
}

.notice .notice-detail {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.notice .notice-hint {
  margin-top: 6px;
}

.failed-sql {
  margin-top: 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
}
</style>
