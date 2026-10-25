<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ConversationView from './components/ConversationView.vue'
import SettingsDialog from './components/SettingsDialog.vue'
import SideBar from './components/SideBar.vue'
import { useConversations } from './stores/conversations'
import { useLlmSettings } from './stores/llmSettings'

const { conversations, active, busy, startNew, select, remove, ask } = useConversations()
const llm = useLlmSettings()

const settingsOpen = ref(false)
/** 读到设置且确认没配 Key 时，在首页提示去设置 */
const needsSetup = computed(() => llm.state.view !== null && !llm.state.view.configured)

onMounted(llm.load)

function openSettings() {
  settingsOpen.value = true
  sidebarOpen.value = false
}

/** 窄屏时侧栏收起，点左上角按钮展开 */
const sidebarOpen = ref(false)

function onSelect(id: string) {
  select(id)
  sidebarOpen.value = false
}

function onNew() {
  startNew()
  sidebarOpen.value = false
}
</script>

<template>
  <div class="layout" :class="{ 'sidebar-open': sidebarOpen }">
    <SideBar
      class="sidebar"
      :conversations="conversations"
      :active-id="active?.id ?? null"
      @new="onNew"
      @select="onSelect"
      @remove="remove"
      @settings="openSettings"
      :llm="llm.state.view"
      :llm-error="llm.state.loadError"
    />
    <div class="scrim" @click="sidebarOpen = false" />
    <main class="main">
      <button class="menu" type="button" aria-label="打开对话列表" @click="sidebarOpen = true">
        <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
          <path d="M3 5h12M3 9h12M3 13h12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
      </button>
      <ConversationView
        :conversation="active"
        :busy="busy"
        :needs-setup="needsSetup"
        @ask="ask"
        @settings="openSettings"
      />
    </main>
    <SettingsDialog :open="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  /* 行高固定为一屏，内容只在右侧对话区内部滚动，侧栏和输入框始终可见 */
  grid-template-rows: minmax(0, 1fr);
  height: 100%;
}

.main {
  position: relative;
  min-width: 0;
  height: 100%;
}

.menu,
.scrim {
  display: none;
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(300px, 85vw);
    z-index: 20;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .sidebar-open .sidebar {
    transform: none;
  }

  .sidebar-open .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 10;
    background: rgb(0 0 0 / 0.3);
  }

  .menu {
    display: inline-flex;
    position: absolute;
    top: 12px;
    left: 12px;
    z-index: 5;
    padding: 6px;
    border: 1px solid var(--line);
    border-radius: var(--radius);
    background: var(--surface);
  }
}
</style>
