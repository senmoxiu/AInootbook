<!-- 使用 type="home" 属性设置首页，其他页面不需要设置，默认为page；推荐使用json5，更强大，且允许注释 -->
<route lang="json5" type="home">
{
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '首页',
    disableScroll: true,
    'app-plus': {
      bounce: 'none',
    },
  },
}
</route>
<template>
  <PageLayout :navbarShow="false">
    <scroll-view class="home-scroll" :scroll-y="true">
      <!-- 顶部欢迎区 -->
      <view class="header-area">
        <view class="greeting">
          <view class="greeting-text">
            <text class="hello">你好，</text>
            <text class="username">{{ userStore.userInfo.realname || '同学' }}</text>
          </view>
          <text class="subtitle">今天也要好好学习哦</text>
        </view>
        <image class="avatar" :src="avatarUrl" mode="aspectFill" @error="onAvatarError" />
      </view>

      <!-- 快捷入口 -->
      <view class="section">
        <view class="section-title">快捷入口</view>
        <view class="shortcut-grid">
          <view class="shortcut-item" @click="goStudy">
            <view class="shortcut-icon study-icon">
              <wd-icon name="books" size="28px" color="#0081ff" />
            </view>
            <text class="shortcut-label">我的课程</text>
          </view>
          <view class="shortcut-item" @click="goNoteList">
            <view class="shortcut-icon note-icon">
              <wd-icon name="edit-outline" size="28px" color="#07c160" />
            </view>
            <text class="shortcut-label">我的笔记</text>
          </view>
          <view class="shortcut-item" @click="goNoteWizard">
            <view class="shortcut-icon ai-icon">
              <wd-icon name="add-circle" size="28px" color="#ff6b35" />
            </view>
            <text class="shortcut-label">AI 创建</text>
          </view>
          <view class="shortcut-item" @click="goProfile">
            <view class="shortcut-icon user-icon">
              <wd-icon name="user" size="28px" color="#9b59b6" />
            </view>
            <text class="shortcut-label">个人中心</text>
          </view>
        </view>
      </view>

      <!-- 最近笔记 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">最近笔记</text>
          <text class="section-more" @click="goNoteList">查看全部</text>
        </view>
        <view v-if="recentNotes.length === 0" class="empty-tip">
          <wd-icon name="file-text" size="40px" color="#ccc" />
          <text class="empty-text">暂无笔记，去创建第一篇吧</text>
        </view>
        <view v-else class="note-list">
          <view
            v-for="note in recentNotes"
            :key="note.id"
            class="note-card"
            @click="goNoteDetail(note.id)"
          >
            <view class="note-card-header">
              <text class="note-title">{{ note.noteTitle }}</text>
              <text class="note-status" :class="statusClass(note.noteStatus)">
                {{ statusLabel(note.noteStatus) }}
              </text>
            </view>
            <text class="note-summary">{{ note.aiSummary || '暂无摘要' }}</text>
            <view class="note-card-footer">
              <text class="note-course">{{ note.courseName || '未关联课程' }}</text>
              <text class="note-time">{{ formatTime(note.updateTime) }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </PageLayout>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from '@/plugin/uni-mini-router'
import { useUserStore } from '@/store/user'
import { noteApi, type Note } from '@/api/note'
import { getFileAccessHttpUrl } from '@/common/uitls'

defineOptions({
  name: 'index',
  options: {
    styleIsolation: 'shared',
  },
})

const router = useRouter()
const userStore = useUserStore()
const recentNotes = ref<Note[]>([])
const avatarError = ref(false)

const avatarUrl = computed(() => {
  if (avatarError.value || !userStore.userInfo.avatar) return '/static/logo.png'
  return getFileAccessHttpUrl(userStore.userInfo.avatar) || '/static/logo.png'
})

const onAvatarError = () => {
  avatarError.value = true
}

const statusLabel = (status?: number) => {
  const map: Record<number, string> = { 0: '草稿', 1: '生成中', 2: '已完成', 3: '失败' }
  return map[status ?? 0] ?? '草稿'
}

const statusClass = (status?: number) => {
  const map: Record<number, string> = { 0: 'draft', 1: 'processing', 2: 'done', 3: 'failed' }
  return map[status ?? 0] ?? 'draft'
}

const formatTime = (time?: string) => {
  if (!time) return ''
  return time.slice(0, 10)
}

const goStudy = () => router.pushTab({ path: '/pages-study/course/list' })
const goNoteList = () => router.push({ name: 'study-note-list' })
const goNoteWizard = () => router.push({ name: 'study-note-wizard' })
const goNoteDetail = (id: string) => router.push({ name: 'study-note-detail', params: { id } })
const goProfile = () => router.pushTab({ path: '/pages/user/people' })

onMounted(async () => {
  try {
    const res = await noteApi.getNoteList({ pageNo: 1, pageSize: 5 })
    recentNotes.value = (res as any)?.result?.records ?? []
  } catch {
    recentNotes.value = []
  }
})
</script>

<style lang="scss" scoped>
.home-scroll {
  background-color: #f4f7f9;
  min-height: 100vh;
}

.header-area {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 60upx 40upx 40upx;
  background: linear-gradient(135deg, #0081ff 0%, #0066cc 100%);

  .greeting {
    .hello {
      font-size: 32upx;
      color: rgba(255, 255, 255, 0.85);
    }
    .username {
      font-size: 36upx;
      font-weight: 600;
      color: #fff;
    }
    .subtitle {
      display: block;
      font-size: 26upx;
      color: rgba(255, 255, 255, 0.7);
      margin-top: 8upx;
    }
  }

  .avatar {
    width: 100upx;
    height: 100upx;
    border-radius: 50%;
    border: 3px solid rgba(255, 255, 255, 0.5);
  }
}

.section {
  margin: 24upx 24upx 0;
  background: #fff;
  border-radius: 16upx;
  padding: 32upx;

  .section-title {
    font-size: 30upx;
    font-weight: 600;
    color: #333;
    margin-bottom: 24upx;
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24upx;

    .section-more {
      font-size: 26upx;
      color: #0081ff;
    }
  }
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16upx;

  .shortcut-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12upx;

    .shortcut-icon {
      width: 100upx;
      height: 100upx;
      border-radius: 24upx;
      display: flex;
      align-items: center;
      justify-content: center;

      &.study-icon { background: #e8f4ff; }
      &.note-icon  { background: #e8f9ee; }
      &.ai-icon    { background: #fff0ea; }
      &.user-icon  { background: #f3eaff; }
    }

    .shortcut-label {
      font-size: 24upx;
      color: #555;
    }
  }
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40upx 0;
  gap: 16upx;

  .empty-text {
    font-size: 26upx;
    color: #aaa;
  }
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: 20upx;
}

.note-card {
  padding: 24upx;
  background: #f8fafc;
  border-radius: 12upx;
  border-left: 4px solid #0081ff;

  .note-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10upx;

    .note-title {
      font-size: 28upx;
      font-weight: 500;
      color: #222;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .note-status {
      font-size: 22upx;
      padding: 4upx 12upx;
      border-radius: 20upx;
      margin-left: 12upx;
      flex-shrink: 0;

      &.draft      { background: #f0f0f0; color: #888; }
      &.processing { background: #fff7e6; color: #fa8c16; }
      &.done       { background: #f6ffed; color: #52c41a; }
      &.failed     { background: #fff1f0; color: #f5222d; }
    }
  }

  .note-summary {
    font-size: 24upx;
    color: #888;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    margin-bottom: 12upx;
  }

  .note-card-footer {
    display: flex;
    justify-content: space-between;

    .note-course {
      font-size: 22upx;
      color: #0081ff;
    }

    .note-time {
      font-size: 22upx;
      color: #bbb;
    }
  }
}
</style>
