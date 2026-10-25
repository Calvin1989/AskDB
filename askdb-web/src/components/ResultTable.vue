<script setup lang="ts">
import { computed } from 'vue'
import type { QueryResult } from '../types'
import { formatValue } from '../utils/format'

const props = defineProps<{ result: QueryResult }>()

/** 数值列右对齐，方便上下比较大小 */
const numericColumns = computed(() =>
  props.result.columns.map((_, c) => props.result.rows.some((row) => typeof row[c] === 'number')),
)
</script>

<template>
  <div class="wrap">
    <table>
      <thead>
        <tr>
          <th v-for="(col, c) in result.columns" :key="c" :class="{ num: numericColumns[c] }">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, r) in result.rows" :key="r">
          <td
            v-for="(cell, c) in row"
            :key="c"
            :class="{ num: numericColumns[c], null: cell === null }"
            :title="cell === null ? 'NULL' : undefined"
          >
            {{ formatValue(cell) }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.wrap {
  max-height: 420px;
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 7px 14px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--line);
}

th {
  position: sticky;
  top: 0;
  background: var(--surface);
  color: var(--ink-2);
  font-weight: 600;
  font-size: 13px;
  box-shadow: inset 0 -1px 0 var(--line-strong);
}

tbody tr:last-child td {
  border-bottom: 0;
}

tbody tr:hover td {
  background: var(--paper);
}

.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

td.null {
  color: var(--muted);
}
</style>
