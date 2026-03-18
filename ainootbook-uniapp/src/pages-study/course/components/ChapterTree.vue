<template>
  <view class="chapter-tree">
    <template v-if="nodes.length">
      <view class="chapter-tree__toolbar">
        <text class="chapter-tree__toolbar-text">共 {{ totalChapters }} 个章节</text>
        <view class="chapter-tree__toolbar-actions">
          <wd-button size="small" plain @click="expandAll">全部展开</wd-button>
          <wd-button size="small" plain @click="collapseAll">全部收起</wd-button>
        </view>
      </view>

      <view
        v-for="item in visibleNodes"
        :key="item.id"
        class="chapter-tree__item"
        :style="{ paddingLeft: `${32 + item.level * 36}rpx` }"
      >
        <view class="chapter-tree__row" @click="handleNodeTap(item)">
          <view class="chapter-tree__trigger" @click.stop="handleTriggerTap(item)">
            <wd-icon
              v-if="item.hasChildren"
              :name="isExpanded(item.id) ? 'arrow-down' : 'arrow-right'"
              size="14px"
            />
            <view v-else class="chapter-tree__dot"></view>
          </view>

          <view class="chapter-tree__body">
            <view class="chapter-tree__title-line">
              <text class="chapter-tree__title">{{ item.title }}</text>
              <wd-tag v-if="item.code" plain round>{{ item.code }}</wd-tag>
            </view>
            <text class="chapter-tree__meta">{{ item.meta }}</text>
            <wd-progress
              v-if="item.progress > 0"
              class="chapter-tree__progress"
              :percentage="item.progress"
              hide-text
              color="#5b9dff"
            />
          </view>

          <text v-if="!item.hasChildren" class="chapter-tree__action">记笔记</text>
        </view>
      </view>
    </template>

    <view v-else class="chapter-tree__empty">
      <wd-status-tip image="content" :tip="emptyText" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { countLeafChapters, type CourseChapterTreeNode } from '../utils'

interface VisibleChapterNode extends CourseChapterTreeNode {
  level: number
  hasChildren: boolean
  meta: string
}

const props = withDefaults(
  defineProps<{
    nodes: CourseChapterTreeNode[]
    emptyText?: string
  }>(),
  {
    emptyText: '暂无章节内容',
  },
)

const emit = defineEmits<{
  select: [node: CourseChapterTreeNode]
}>()

const expandedKeys = ref<string[]>([])

const expandedKeySet = computed(() => new Set(expandedKeys.value))

const totalChapters = computed(() => countLeafChapters(props.nodes))

const allExpandableKeys = computed(() => {
  const result: string[] = []

  const walk = (nodes: CourseChapterTreeNode[]) => {
    nodes.forEach((node) => {
      if (node.children.length > 0) {
        result.push(node.id)
        walk(node.children)
      }
    })
  }

  walk(props.nodes)
  return result
})

const visibleNodes = computed<VisibleChapterNode[]>(() => {
  const result: VisibleChapterNode[] = []

  const walk = (nodes: CourseChapterTreeNode[], level = 0) => {
    nodes.forEach((node) => {
      const hasChildren = node.children.length > 0
      const childCount = hasChildren ? countLeafChapters(node.children) : 0
      result.push({
        ...node,
        level,
        hasChildren,
        meta:
          node.description?.trim() ||
          (hasChildren ? `包含 ${childCount} 个子章节` : '点击创建章节笔记'),
      })

      if (hasChildren && expandedKeySet.value.has(node.id)) {
        walk(node.children, level + 1)
      }
    })
  }

  walk(props.nodes)
  return result
})

watch(
  () => props.nodes,
  (nodes) => {
    expandedKeys.value = nodes.filter((node) => node.children.length > 0).map((node) => node.id)
  },
  { immediate: true },
)

function isExpanded(id: string) {
  return expandedKeySet.value.has(id)
}

function toggleNode(id: string) {
  if (expandedKeySet.value.has(id)) {
    expandedKeys.value = expandedKeys.value.filter((key) => key !== id)
    return
  }

  expandedKeys.value = [...expandedKeys.value, id]
}

function expandAll() {
  expandedKeys.value = [...allExpandableKeys.value]
}

function collapseAll() {
  expandedKeys.value = []
}

function handleTriggerTap(node: VisibleChapterNode) {
  if (!node.hasChildren) {
    return
  }

  toggleNode(node.id)
}

function handleNodeTap(node: VisibleChapterNode) {
  if (node.hasChildren) {
    toggleNode(node.id)
    return
  }

  emit('select', node)
}
</script>

<style lang="scss" scoped>
.chapter-tree {
  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16rpx;
    margin-bottom: 20rpx;
  }

  &__toolbar-text {
    font-size: 24rpx;
    color: #7b8aa3;
  }

  &__toolbar-actions {
    display: flex;
    gap: 12rpx;
  }

  &__item + &__item {
    margin-top: 12rpx;
  }

  &__row {
    display: flex;
    align-items: flex-start;
    gap: 16rpx;
    padding: 22rpx 20rpx;
    border-radius: 20rpx;
    background: #f7faff;
  }

  &__trigger {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28rpx;
    min-height: 40rpx;
    color: #6280b9;
    flex-shrink: 0;
  }

  &__dot {
    width: 10rpx;
    height: 10rpx;
    border-radius: 50%;
    background: #9eb6de;
    margin-top: 14rpx;
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__title-line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10rpx;
  }

  &__title {
    font-size: 28rpx;
    font-weight: 500;
    color: #243551;
    line-height: 1.6;
    word-break: break-all;
  }

  &__meta {
    display: block;
    margin-top: 8rpx;
    font-size: 24rpx;
    color: #7b8aa3;
    line-height: 1.5;
  }

  &__progress {
    margin-top: 12rpx;
  }

  &__action {
    margin-top: 4rpx;
    font-size: 24rpx;
    font-weight: 600;
    color: #226bff;
    white-space: nowrap;
  }

  &__empty {
    padding: 60rpx 0 20rpx;
  }
}
</style>
