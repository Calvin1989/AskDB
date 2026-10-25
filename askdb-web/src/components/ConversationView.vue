<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { Conversation } from '../types'
import EmptyState from './EmptyState.vue'
import QuestionInput from './QuestionInput.vue'
import TurnBlock from './TurnBlock.vue'

const props = defineProps<{
  conversation: Conversation | null
  busy: boolean
  needsSetup: boolean
}>()

const emit = defineEmits<{ ask: [question: string]; settings: [] }>()

const scroller = ref<HTMLElement | null>(null)

/** 新问题发出、结果返回、或切换到另一个对话时，滚到最下面 */
watch(
  () => [props.conversation?.id, props.conversation?.turns.length, props.conversation?.turns.at(-1)?.state],
  async () => {
    await nextTick()
    scroller.value?.scrollTo({ top: scroller.value.scrollHeight })
  },
)
</script>

<template>
  <div class="view">
    <div ref="scroller" class="scroll">
      <div class="column">
        <EmptyState
          v-if="!conversation || conversation.turns.length === 0"
          :needs-setup="needsSetup"
          @pick="emit('ask', $event)"
          @settings="emit('settings')"
        />
        <TurnBlock v-for="turn in conversation?.turns ?? []" v-else :key="turn.id" :turn="turn" />
      </div>
    </div>
    <div class="dock">
      <div class="column">
        <QuestionInput :disabled="busy" @submit="emit('ask', $event)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.scroll {
  flex: 1;
  /* 没有这一行，内容一多整个页面都会跟着滚，输入框会被推出屏幕 */
  min-height: 0;
  overflow-y: auto;
}

.column {
  max-width: 880px;
  margin: 0 auto;
  padding: 0 32px;
}

.scroll .column {
  padding-top: 32px;
  padding-bottom: 24px;
}

.dock {
  padding: 12px 0 20px;
  border-top: 1px solid var(--line);
  background: var(--paper);
}

@media (max-width: 860px) {
  .column {
    padding: 0 16px;
  }

  .scroll .column {
    padding-top: 60px;
  }
}
</style>
