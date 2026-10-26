<script setup lang="ts">
import { nextTick, ref } from 'vue'

const props = defineProps<{ disabled: boolean }>()
const emit = defineEmits<{ submit: [question: string] }>()

const MAX_LENGTH = 500
const text = ref('')
const textarea = ref<HTMLTextAreaElement | null>(null)

function submit() {
  const question = text.value.trim()
  if (!question || props.disabled) return
  emit('submit', question)
  text.value = ''
  nextTick(resize)
}

/**
 * 回车发送，Shift+回车换行。
 * 中文输入法选字时按回车是确认候选词，不能当成发送（isComposing 为 true）。
 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

/** 输入框随内容变高，最多 6 行 */
function resize() {
  const el = textarea.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}
</script>

<template>
  <form class="input" @submit.prevent="submit">
    <textarea
      ref="textarea"
      v-model="text"
      rows="1"
      :maxlength="MAX_LENGTH"
      placeholder="输入问题，回车发送；可以接着上一个问题追问"
      aria-label="问题"
      @keydown="onKeydown"
      @input="resize"
    />
    <button class="send" type="submit" :disabled="disabled || !text.trim()">
      {{ disabled ? '查询中' : '提问' }}
    </button>
  </form>
</template>

<style scoped>
.input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface);
}

.input:focus-within {
  border-color: var(--accent);
}

textarea {
  flex: 1;
  min-width: 0;
  padding: 6px 6px;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--ink);
  font: inherit;
  line-height: 1.5;
}

textarea::placeholder {
  color: var(--muted);
}

.send {
  flex: none;
  padding: 6px 16px;
  border: 0;
  border-radius: var(--radius);
  background: var(--accent);
  color: #fff;
  font-weight: 500;
}

.send:hover:not(:disabled) {
  background: var(--accent-hover);
}

.send:disabled {
  background: var(--line-strong);
  color: var(--muted);
  cursor: not-allowed;
}

@media (prefers-color-scheme: dark) {
  .send:not(:disabled) {
    color: #10161a;
  }
}
</style>
