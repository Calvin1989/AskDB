<script setup lang="ts">
defineProps<{ needsSetup: boolean }>()
const emit = defineEmits<{ pick: [question: string]; settings: [] }>()

/** 新对话时给的示例：覆盖单值、分类对比、时间趋势、多表关联几种典型问题 */
const examples = [
  '一共有多少个订单？',
  '每种订单状态各有多少单？',
  '2018 年每个月分别有多少个订单？',
  '卖出商品件数最多的前 5 个商品类别是哪些？用英文类别名回答。',
  'RJ 州的客户一共支付了多少钱？',
]

const tables = ['订单', '订单商品', '支付', '评价', '商品', '商品类别', '客户', '卖家']
</script>

<template>
  <section class="empty">
    <div v-if="needsSetup" class="setup">
      <p>还没有配置模型 API Key，提问前需要先设置。</p>
      <button type="button" @click="emit('settings')">设置模型</button>
    </div>

    <h1>用中文提问，直接拿到数据</h1>
    <p class="intro">
      AskDB 会把你的问题翻译成 SQL，在数据库里执行后返回结果，并在合适时画成图表。
      生成的 SQL 都可以查看和复制，方便核对。
    </p>

    <h2>可以问的数据</h2>
    <p class="tables">
      <span v-for="t in tables" :key="t" class="table">{{ t }}</span>
    </p>

    <h2>试试这些问题</h2>
    <ul class="examples">
      <li v-for="q in examples" :key="q">
        <button type="button" @click="emit('pick', q)">{{ q }}</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.empty {
  max-width: 640px;
  padding-top: 8vh;
}

.setup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 32px;
  padding: 10px 12px 10px 14px;
  border-left: 3px solid var(--warn);
  background: var(--warn-soft);
  font-size: 14px;
}

.setup p {
  margin: 0;
}

.setup button {
  flex: none;
  padding: 5px 14px;
  border: 0;
  border-radius: var(--radius);
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
}

@media (prefers-color-scheme: dark) {
  .setup button {
    color: #10161a;
  }
}

h1 {
  margin: 0 0 12px;
  font-size: 26px;
  font-weight: 650;
  line-height: 1.3;
}

.intro {
  margin: 0 0 32px;
  color: var(--ink-2);
}

h2 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
}

.tables {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 28px;
}

.table {
  padding: 2px 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface);
  font-size: 13px;
  color: var(--ink-2);
}

.examples {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--line);
}

.examples li {
  border-bottom: 1px solid var(--line);
}

.examples button {
  display: block;
  width: 100%;
  padding: 10px 2px;
  border: 0;
  background: none;
  text-align: left;
  color: var(--ink);
}

.examples button:hover {
  color: var(--accent);
}
</style>
