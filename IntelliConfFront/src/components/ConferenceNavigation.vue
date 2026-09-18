<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowLeft, Calendar, Message, Setting, Stamp, UserFilled } from '@element-plus/icons-vue'
import { conferenceLocation, getConferenceId, type ConferenceRouteName } from '../router/navigation'

interface NavigationLeaf {
  name: ConferenceRouteName
  label: string
}

interface NavigationGroup {
  label: string
  icon: Component
  defaultName: ConferenceRouteName
  items: NavigationLeaf[]
}

const route = useRoute()
const router = useRouter()
const confId = computed(() => getConferenceId(route.query))
const visible = computed(() => Boolean(route.meta.conferenceNav && confId.value))

const navigationGroups: NavigationGroup[] = [
  {
    label: '工作台',
    icon: Calendar,
    defaultName: 'Dashboard',
    items: [{ name: 'Dashboard', label: '会议工作台' }],
  },
  {
    label: '会议设置',
    icon: Setting,
    defaultName: 'ConferenceInfo',
    items: [
      { name: 'ConferenceInfo', label: '会议信息' },
      { name: 'ConferenceSetup', label: '流程与时间轴' },
      { name: 'ConferenceCommitteeInit', label: '委员会邀请' },
    ],
  },
  {
    label: '人员中心',
    icon: UserFilled,
    defaultName: 'ConferenceUsers',
    items: [
      { name: 'ConferenceUsers', label: '参会者与委员会' },
      { name: 'AuthorDiscovery', label: '潜在投稿者发现' },
    ],
  },
  {
    label: '邮件中心',
    icon: Message,
    defaultName: 'MailWorkflow',
    items: [
      { name: 'MailWorkflow', label: '自动化与审核' },
      { name: 'MailCompose', label: '临时邮件' },
      { name: 'ConferenceMailAccount', label: '会议邮箱' },
    ],
  },
  {
    label: '电子印章',
    icon: Stamp,
    defaultName: 'ElectronicSeal',
    items: [{ name: 'ElectronicSeal', label: '电子印章' }],
  },
]

const currentPageLabel = computed(() => {
  const currentName = String(route.name || '')
  return navigationGroups.flatMap(group => group.items).find(item => item.name === currentName)?.label || '会议功能'
})

function isGroupActive(group: NavigationGroup) {
  return group.items.some(item => item.name === route.name)
}

function navigate(name: ConferenceRouteName) {
  if (route.name === name && getConferenceId(route.query) === confId.value) return
  router.push(conferenceLocation(name, confId.value))
}
</script>

<template>
  <nav v-if="visible" class="conference-navigation" aria-label="会议功能导航">
    <button class="conference-return" type="button" @click="router.push({ name: 'Portal' })">
      <el-icon><ArrowLeft /></el-icon>
      <span>我的会议</span>
    </button>
    <div class="conference-context" :title="confId">
      <small>当前会议 · {{ currentPageLabel }}</small>
      <strong>{{ confId }}</strong>
    </div>
    <div class="conference-links">
      <template v-for="group in navigationGroups" :key="group.label">
        <button
          v-if="group.items.length === 1"
          type="button"
          :class="{ active: isGroupActive(group) }"
          :aria-current="isGroupActive(group) ? 'page' : undefined"
          @click="navigate(group.defaultName)"
        >
          <el-icon><component :is="group.icon" /></el-icon>
          <span>{{ group.label }}</span>
        </button>

        <el-dropdown
          v-else
          trigger="click"
          placement="bottom-start"
          @command="navigate"
        >
          <button
            type="button"
            class="conference-group-trigger"
            :class="{ active: isGroupActive(group) }"
            :aria-current="isGroupActive(group) ? 'page' : undefined"
            :aria-label="`${group.label}功能菜单`"
          >
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.label }}</span>
            <el-icon class="group-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in group.items"
                :key="item.name"
                :command="item.name"
                :class="{ 'is-current-route': route.name === item.name }"
              >
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </div>
  </nav>
</template>

<style scoped>
.conference-navigation {
  position: sticky;
  top: 0;
  z-index: 100;
  min-height: 56px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 24px;
  border-bottom: 1px solid #dbe3ee;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(10px);
}

.conference-return,
.conference-links button {
  border: 0;
  background: transparent;
  color: #475569;
  cursor: pointer;
  font: inherit;
}

.conference-links :deep(.el-tooltip__trigger:focus-visible) {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
  border-radius: 8px;
}

.conference-return {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 4px;
  white-space: nowrap;
}

.conference-return:hover,
.conference-links button:hover {
  color: #2563eb;
}

.conference-context {
  min-width: 108px;
  max-width: 180px;
  padding-left: 14px;
  border-left: 1px solid #e2e8f0;
  overflow: hidden;
}

.conference-context small,
.conference-context strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conference-context small {
  color: #94a3b8;
  font-size: 11px;
}

.conference-context strong {
  margin-top: 2px;
  color: #0f172a;
  font-size: 14px;
}

.conference-links {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.conference-links::-webkit-scrollbar {
  display: none;
}

.conference-links button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 8px;
  white-space: nowrap;
  transition: color 0.16s ease, background 0.16s ease;
}

.conference-group-trigger .group-arrow {
  margin-left: -2px;
  font-size: 12px;
}

.conference-links button.active {
  color: #1d4ed8;
  background: #eff6ff;
  font-weight: 700;
}

:global(.el-dropdown-menu__item.is-current-route) {
  color: #1d4ed8;
  background: #eff6ff;
  font-weight: 700;
}

@media (max-width: 860px) {
  .conference-navigation {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 8px 14px;
  }

  .conference-context {
    flex: 1;
  }

  .conference-links {
    width: 100%;
  }
}
</style>
