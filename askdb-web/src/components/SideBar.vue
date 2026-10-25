<script setup lang="ts">
import { computed } from 'vue'
import type { LlmSettingsView } from '../api/settings'
import type { Conversation } from '../types'

const props = defineProps<{
  conversations: Conversation[]
  activeId: string | null
  llm: LlmSettingsView | null
  llmError: string
}>()

const emit = defineEmits<{
  new: []
  select: [id: string]
  remove: [id: string]
  settings: []
}>()

const DAY = 24 * 60 * 60 * 1000

/** 按最后提问时间分成"今天 / 最近 7 天 / 更早" */
const groups = computed(() => {
  const startOfToday = new Date().setHours(0, 0, 0, 0)
  const buckets: { label: string; items: Conversation[] }[] = [
    { label: '今天', items: [] },
    { label: '最近 7 天', items: [] },
    { label: '更早', items: [] },
  ]
  for (const c of props.conversations) {
    const index = c.updatedAt >= startOfToday ? 0 : c.updatedAt >= startOfToday - 6 * DAY ? 1 : 2
    buckets[index].items.push(c)
  }
  return buckets.filter((b) => b.items.length > 0)
})
</script>

<template>
  <aside class="sidebar">
    <div class="head">
      <span class="brand">AskDB</span>
      <button class="new" type="button" @click="emit('new')">新对话</button>
    </div>

    <nav class="list" aria-label="对话记录">
      <p v-if="conversations.length === 0" class="empty">提问后，对话会保存在这里。</p>
      <section v-for="group in groups" :key="group.label">
        <h2 class="group">{{ group.label }}</h2>
        <ul>
          <li v-for="c in group.items" :key="c.id" class="item" :class="{ active: c.id === activeId }">
            <button class="title" type="button" :title="c.title" @click="emit('select', c.id)">
              {{ c.title }}
            </button>
            <button class="remove" type="button" :aria-label="`删除对话：${c.title}`" @click="emit('remove', c.id)">
              <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
                <path d="M3.5 3.5l7 7m0-7l-7 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
            </button>
          </li>
        </ul>
      </section>
    </nav>

    <div class="foot">
      <div class="model">
        <span class="model-label">模型</span>
        <span v-if="llmError" class="model-value warn" :title="llmError">读取失败</span>
        <span v-else-if="llm?.configured" class="model-value" :title="llm.baseUrl">{{ llm.model }}</span>
        <span v-else-if="llm" class="model-value warn">未配置 API Key</span>
        <span v-else class="model-value" />
        <button class="settings" type="button" @click="emit('settings')">模型设置</button>
      </div>
      <p class="source">数据：Olist 巴西电商公开数据集，2016–2018 年约 10 万个订单</p>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface);
  border-right: 1px solid var(--line);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
}

.brand {
  font-weight: 650;
  font-size: 16px;
  letter-spacing: 0.01em;
}

.new {
  padding: 5px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 13px;
}

.new:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.empty {
  margin: 8px;
  color: var(--muted);
  font-size: 13px;
}

.group {
  margin: 16px 8px 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
}

ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item {
  display: flex;
  align-items: center;
  border-radius: var(--radius);
}

.item:hover {
  background: var(--paper);
}

.item.active {
  background: var(--accent-soft);
}

.title {
  flex: 1;
  min-width: 0;
  padding: 7px 8px;
  border: 0;
  background: none;
  text-align: left;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item.active .title {
  color: var(--accent);
  font-weight: 500;
}

.remove {
  display: inline-flex;
  margin-right: 4px;
  padding: 5px;
  border: 0;
  border-radius: 4px;
  background: none;
  color: var(--muted);
  opacity: 0;
}

.item:hover .remove,
.remove:focus-visible {
  opacity: 1;
}

.remove:hover {
  color: var(--danger);
}

@media (hover: none) {
  .remove {
    opacity: 1;
  }
}

.foot {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--line);
}

.model {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.model-label {
  color: var(--muted);
}

.model-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.model-value.warn {
  color: var(--warn);
}

.settings {
  flex: none;
  padding: 3px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 12.5px;
}

.settings:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.source {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}
</style>
