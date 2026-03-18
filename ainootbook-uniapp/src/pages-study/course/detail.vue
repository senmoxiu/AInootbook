<route lang="json5" type="page">
{
  layout: 'default',
  style: {
    navigationBarTitleText: '课程详情',
    navigationStyle: 'custom',
    disableScroll: true,
    'app-plus': {
      bounce: 'none',
    },
  },
}
</route>

<template>
  <PageLayout navTitle="课程详情">
    <scroll-view class="course-detail-page" scroll-y>
      <view class="course-detail-page__content">
        <template v-if="loading">
          <view class="course-detail-page__status">
            <wd-status-tip image="network" tip="课程详情加载中..." />
          </view>
        </template>

        <template v-else-if="loadError">
          <view class="course-detail-page__status">
            <wd-status-tip image="network" :tip="loadErrorMessage" />
            <wd-button size="small" @click="handleRetry">重新加载</wd-button>
          </view>
        </template>

        <template v-else-if="courseDetail">
          <CourseCard :course="courseDetail" :clickable="false" show-description />

          <view class="detail-section">
            <view class="detail-section__header">
              <text class="detail-section__title">课程简介</text>
            </view>
            <text class="detail-section__desc">{{ courseDescription }}</text>
          </view>

          <view class="detail-section">
            <view class="detail-section__header">
              <view>
                <text class="detail-section__title">课程章节</text>
                <text class="detail-section__subtitle">点击末级章节进入笔记创建页</text>
              </view>
              <wd-tag type="primary" plain round>{{ totalChapterCount }} 章</wd-tag>
            </view>

            <ChapterTree :nodes="chapterTree" @select="handleChapterSelect" />
          </view>
        </template>

        <template v-else>
          <view class="course-detail-page__status">
            <wd-status-tip image="content" tip="暂无课程详情" />
          </view>
        </template>
      </view>
    </scroll-view>
  </PageLayout>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useToast } from 'wot-design-uni'
import { courseApi, type CourseDetail } from '@/api/course'
import { useCourseStore, useNoteWizardStore } from '@/store'
import ChapterTree from './components/ChapterTree.vue'
import CourseCard from './components/CourseCard.vue'
import {
  countLeafChapters,
  getErrorMessage,
  normalizeChapterTree,
  normalizeCourseDetailResponse,
  type CourseChapterTreeNode,
  withAutoRetry,
} from './utils'

defineOptions({
  name: 'courseDetail',
  options: {
    styleIsolation: 'shared',
  },
})

const toast = useToast()
const courseStore = useCourseStore()
const noteWizardStore = useNoteWizardStore()

const courseId = ref('')
const loading = ref(false)
const loadError = ref(false)
const loadErrorMessage = ref('课程详情加载失败，请稍后重试')
const courseDetail = ref<CourseDetail | null>(null)
const chapterTree = ref<CourseChapterTreeNode[]>([])

const courseDescription = computed(() => {
  return courseDetail.value?.description?.trim() || '暂无课程简介'
})

const totalChapterCount = computed(() => {
  const count = Number(courseDetail.value?.chapterCount ?? 0)
  if (Number.isFinite(count) && count > 0) {
    return count
  }
  return countLeafChapters(chapterTree.value)
})

onLoad((options) => {
  const optionId = typeof options?.id === 'string' ? options.id : ''
  const cachedCourse = courseStore.currentCourse
  const cachedId = cachedCourse?.id ? String(cachedCourse.id) : ''

  courseId.value = optionId || cachedId

  if (cachedCourse && cachedId === courseId.value) {
    courseDetail.value = cachedCourse as CourseDetail
    chapterTree.value = normalizeChapterTree(cachedCourse as CourseDetail)
  }

  loadCourseDetail()
})

async function loadCourseDetail() {
  if (!courseId.value) {
    loadError.value = true
    loadErrorMessage.value = '缺少课程ID，无法加载课程详情'
    return
  }

  loading.value = true
  loadError.value = false
  loadErrorMessage.value = '课程详情加载失败，请稍后重试'

  try {
    const detail = await withAutoRetry(
      async () => {
        const response = await courseApi.getCourseDetail(courseId.value, {
          cache: true,
          hideErrorToast: true,
          retryCount: 0,
          retryDelay: 0,
        })
        return normalizeCourseDetailResponse(response)
      },
      { retries: 3, baseDelay: 1000 },
    )

    courseDetail.value = detail
    chapterTree.value = normalizeChapterTree(detail)
    courseStore.setCurrentCourse(detail)
  } catch (error) {
    loadError.value = true
    loadErrorMessage.value = getErrorMessage(error, '课程详情加载失败，请稍后重试')
    toast.warning(loadErrorMessage.value)
  } finally {
    loading.value = false
  }
}

function handleRetry() {
  loadCourseDetail()
}

function handleChapterSelect(node: CourseChapterTreeNode) {
  if (!courseDetail.value?.id) {
    toast.warning('课程信息尚未准备完成，请稍后重试')
    return
  }

  noteWizardStore.clearWizardData()
  noteWizardStore.setWizardData({
    courseId: courseDetail.value.id,
    courseName: courseDetail.value.courseName,
    teacherName: courseDetail.value.teacherName,
    chapterId: node.id,
    chapterName: node.title,
  })

  const query = [
    `courseId=${encodeURIComponent(courseDetail.value.id)}`,
    `courseName=${encodeURIComponent(courseDetail.value.courseName || '')}`,
    `chapterId=${encodeURIComponent(node.id)}`,
    `chapterName=${encodeURIComponent(node.title)}`,
  ].join('&')

  uni.navigateTo({
    url: `/pages-study/note/wizard?${query}`,
  })
}
</script>

<style lang="scss" scoped>
.course-detail-page {
  height: 100%;

  &__content {
    padding: 24rpx 0 40rpx;
  }

  &__status {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24rpx;
    padding: 140rpx 24rpx;
  }
}

.detail-section {
  margin: 24rpx 24rpx 0;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 40rpx rgba(41, 78, 136, 0.08);

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16rpx;
    margin-bottom: 20rpx;
  }

  &__title {
    display: block;
    font-size: 32rpx;
    font-weight: 600;
    color: #20304a;
    line-height: 1.4;
  }

  &__subtitle {
    display: block;
    margin-top: 8rpx;
    font-size: 24rpx;
    color: #7b8aa3;
    line-height: 1.5;
  }

  &__desc {
    font-size: 28rpx;
    color: #4a5a73;
    line-height: 1.8;
  }
}
</style>
