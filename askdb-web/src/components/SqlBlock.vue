<script setup lang="ts">
import { computed } from 'vue'
import { tokenizeSql } from '../utils/sql'

const props = defineProps<{ sql: string }>()

const tokens = computed(() => tokenizeSql(props.sql))
</script>

<template>
  <pre class="sql"><code><span v-for="(t, i) in tokens" :key="i" :class="t.type">{{ t.text }}</span></code></pre>
</template>

<style scoped>
.sql {
  margin: 0;
  padding: 14px 16px;
  background: var(--code-bg);
  font: 13.5px/1.6 var(--mono);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--ink);
}

.keyword {
  color: var(--sql-keyword);
  font-weight: 600;
}

.string {
  color: var(--sql-string);
}

.number {
  color: var(--sql-number);
}
</style>
