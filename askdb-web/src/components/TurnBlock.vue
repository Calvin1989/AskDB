<script setup lang="ts">
import { useConversations } from '../stores/conversations'
import type { Turn } from '../types'
import AnswerView from './AnswerView.vue'

defineProps<{ turn: Turn }>()

const { giveFeedback } = useConversations()
</script>

<template>
  <article class="turn">
    <h2 class="question">{{ turn.question }}</h2>
    <!-- 追问：显示结合上文理解成的完整问题，理解错了一眼就能看出来 -->
    <p v-if="turn.answer?.rewrittenQuestion" class="rewritten">理解为：{{ turn.answer.rewrittenQuestion }}</p>

    <!-- 处理中：显示后端推送的每一步，最后一步是正在进行的 -->
    <ol v-if="turn.state === 'pending'" class="steps" role="status" aria-live="polite">
      <li v-for="(step, i) in turn.steps ?? []" :key="i" :class="{ current: i === (turn.steps?.length ?? 0) - 1 }">
        <span class="marker" aria-hidden="true" />
        {{ step.message }}
      </li>
      <li v-if="!turn.steps?.length" class="current">
        <span class="marker" aria-hidden="true" />
        正在连接服务
      </li>
    </ol>

    <div v-else-if="turn.state === 'failed'" class="notice" role="alert">
      {{ turn.requestError }}
    </div>

    <template v-else-if="turn.answer">
      <AnswerView :answer="turn.answer" />

      <!-- 反馈：标"对"的问题和 SQL 会进入示例库 -->
      <div v-if="turn.answer.status === 'SUCCESS' && turn.answer.sql" class="feedback">
        <template v-if="!turn.feedback">
          <span>结果对吗？</span>
          <button type="button" @click="giveFeedback(turn, 'CORRECT')">对</button>
          <button type="button" @click="giveFeedback(turn, 'WRONG')">不对</button>
        </template>
        <span v-else-if="turn.feedback === 'CORRECT'">已标记为正确，以后相似的问题会参考这条 SQL。</span>
        <span v-else>已标记为不对，这条 SQL 不会被当作示例。</span>
        <span v-if="turn.feedbackError" class="feedback-error">{{ turn.feedbackError }}</span>
      </div>
    </template>
  </article>
</template>

<style scoped>
.turn {
  padding: 4px 0 28px;
}

.turn + .turn {
  padding-top: 28px;
  border-top: 1px solid var(--line);
}

.rewritten {
  margin: -8px 0 14px;
  font-size: 13px;
  color: var(--muted);
}

.question {
  margin: 0 0 14px;
  font-size: 19px;
  font-weight: 600;
  line-height: 1.45;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 14px;
  color: var(--muted);
}

.steps li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 0;
}

.marker {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--line-strong);
}

/* 正在进行的一步：文字变深，圆点换成点缀色并轻微闪烁 */
.steps li.current {
  color: var(--ink);
}

.steps li.current .marker {
  background: var(--accent);
  animation: pulse 1s ease-in-out infinite alternate;
}

@keyframes pulse {
  to {
    opacity: 0.25;
  }
}

.notice {
  padding: 10px 12px;
  border-left: 3px solid var(--danger);
  background: var(--danger-soft);
  color: var(--ink);
  font-size: 14px;
}

.feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--muted);
}

.feedback button {
  padding: 2px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  font-size: 13px;
}

.feedback button:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.feedback-error {
  color: var(--danger);
}
</style>
