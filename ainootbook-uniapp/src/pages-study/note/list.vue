<route lang="json5" type="page">
{
  layout: 'default',
  style: {
    navigationBarTitleText: '我的笔记',
    navigationStyle: 'custom',
    disableScroll: true,
    'app-plus': {
      bounce: 'none',
    },
  },
}
</route>

<template>
  <PageLayout navTitle="我的笔记">
    <view class="note-list-page">
      <z-paging
        ref="paging"
        :fixed="false"
        v-model="dataList"
        :default-page-size="PAGE_SIZE"
        @query="queryList"
      >
        <template #top>
          <view class="note-list-page__top">
            <wd-search
              v-model.trim="keyword"
              hide-cancel
              placeholder="搜索笔记标题或内容"
              @search="handleSearch"
              @clear="handleClear"
            />
          </view>
        </template>

        <template #empty="{ isLoadFailed }">
          <view class="note-list-page__status">
            <view class="empty-tip">{{ isLoadFailed ? loadErrorMessage : '暂无笔记，点击右下角按钮创建' }}</view>
            <wd-button v-if="isLoadFailed" size="small" @click="handleRetry">重新加载</wd-button>
          </view>
        </template>

        <view class="note-list-container">
          <template v-for="note in dataList" :key="note.id">
            <NoteCard :note="note" @click="goDetail" />
          </template>
        </view>
      </z-paging>

      <!-- 悬浮创建按钮 -->
      <view class="fab-button" @click="handleCreate">
        <wd-icon name="add" size="24px" color="#fff" />
      </view>
    </view>
  </PageLayout>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useToast } from 'wot-design-uni'
import { noteApi, type Note } from '@/api/note'
import { useNoteStore } from '@/store'
import NoteCard from './components/NoteCard.vue'
import { withAutoRetry, getErrorMessage } from '../course/utils'

defineOptions({
  name: 'noteList',
  options: {
    styleIsolation: 'shared',
  },
})

const PAGE_SIZE = 20
const toast = useToast()
const noteStore = useNoteStore()

const paging = ref<any>(null)
const dataList = ref<Note[]>([])
const keyword = ref('')
const loadErrorMessage = ref('笔记列表加载失败，请稍后重试')

async function queryList(pageNo: number, pageSize: number) {
  loadErrorMessage.value = '笔记列表加载失败，请稍后重试'

  try {
    const response = await withAutoRetry(
      async () => {
        return await noteApi.getNoteList({
          pageNo,
          pageSize,
          title: keyword.value || undefined,
        })
      },
      { retries: 3, baseDelay: 2000 },
    )

    const records = response.records || []
    const total = response.total || 0

    paging.value?.completeByTotal(records, total)
    await nextTick()

    if (pageNo === 1) {
      noteStore.setNotes([...records])
    }
  } catch (error) {
    loadErrorMessage.value = getErrorMessage(error, '笔记列表加载失败，请稍后重试')
    paging.value?.complete(false)
    toast.error(loadErrorMessage.value)
  }
}

function handleSearch() {
  paging.value?.reload()
}

function handleClear() {
  keyword.value = ''
  handleSearch()
}

function handleRetry() {
  paging.value?.reload()
}

function handleCreate() {
  uni.navigateTo({
    url: '/pages-study/note/wizard',
  })
}

function goDetail(note: Note) {
  noteStore.setCurrentNote(note)
  uni.navigateTo({
    url: `/pages-study/note/detail?id=${note.id}`,
  })
}
</script>

<style lang="scss" scoped>
.note-list-page {
  position: relative;
  height: 100%;
  background-color: #f7f8fa;

  &__top {
    background: #fff;
    padding: 16rpx 24rpx;
  }

  &__status {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 120rpx 40rpx;
    gap: 32rpx;
  }
}

.note-list-container {
  padding: 12rpx 24rpx;
}

.fab-button {
  position: fixed;
  right: 40rpx;
  bottom: 60rpx;
  width: 100rpx;
  height: 100rpx;
  background-color: #2b5cff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(43, 92, 255, 0.3);
  z-index: 99;
}
</style>
