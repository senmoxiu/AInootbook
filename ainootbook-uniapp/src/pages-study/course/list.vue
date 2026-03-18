<route lang="json5" type="page">
{
  layout: 'default',
  style: {
    navigationBarTitleText: '我的课程',
    navigationStyle: 'custom',
    disableScroll: true,
    'app-plus': {
      bounce: 'none',
    },
  },
}
</route>

<template>
  <PageLayout navTitle="我的课程">
    <view class="course-list-page">
      <z-paging
        ref="paging"
        :fixed="false"
        v-model="dataList"
        :default-page-size="PAGE_SIZE"
        @query="queryList"
      >
        <template #top>
          <view class="course-list-page__top">
            <wd-search
              v-model.trim="keyword"
              hide-cancel
              placeholder="搜索课程名或教师名"
              @search="handleSearch"
              @clear="handleClear"
            />
          </view>
        </template>

        <template #empty="{ isLoadFailed }">
          <view class="course-list-page__status">
            <wd-status-tip
              :image="isLoadFailed ? 'network' : 'content'"
              :tip="isLoadFailed ? loadErrorMessage : emptyTip"
            />
            <wd-button v-if="isLoadFailed" size="small" @click="handleRetry">重新加载</wd-button>
          </view>
        </template>

        <template v-for="course in dataList" :key="course.id">
          <CourseCard :course="course" @click="goDetail" />
        </template>
      </z-paging>
    </view>
  </PageLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useToast } from 'wot-design-uni'
import { courseApi, type Course } from '@/api/course'
import { useCourseStore } from '@/store'
import CourseCard from './components/CourseCard.vue'
import { getErrorMessage, normalizeCourseListResponse, withAutoRetry } from './utils'

defineOptions({
  name: 'courseList',
  options: {
    styleIsolation: 'shared',
  },
})

const PAGE_SIZE = 20

const toast = useToast()
const courseStore = useCourseStore()

const paging = ref<any>(null)
const dataList = ref<Course[]>([])
const keyword = ref('')
const loadErrorMessage = ref('课程列表加载失败，请稍后重试')

const emptyTip = computed(() => {
  return keyword.value.trim() ? '未找到匹配的课程' : '暂无课程内容'
})

watch(
  () => dataList.value,
  (value) => {
    courseStore.setCourses([...(value || [])])
  },
  { deep: true },
)

function buildQueryParams(pageNo: number, pageSize: number) {
  const searchValue = keyword.value.trim()
  return {
    pageNo,
    pageSize,
    courseName: searchValue || undefined,
    teacherName: searchValue || undefined,
  }
}

async function queryList(pageNo: number, pageSize: number) {
  loadErrorMessage.value = '课程列表加载失败，请稍后重试'

  try {
    const { records, total } = await withAutoRetry(
      async () => {
        const response = await courseApi.getCourseList(buildQueryParams(pageNo, pageSize), {
          cache: true,
          hideErrorToast: true,
          retryCount: 0,
          retryDelay: 0,
        })
        return normalizeCourseListResponse(response)
      },
      { retries: 3, baseDelay: 1000 },
    )

    paging.value?.completeByTotal(records, total)
    await nextTick()

    if (pageNo === 1 && records.length === 0) {
      courseStore.setCourses([])
    }
  } catch (error) {
    loadErrorMessage.value = getErrorMessage(error, '课程列表加载失败，请稍后重试')
    paging.value?.complete(false)
    toast.warning(loadErrorMessage.value)
  }
}

function handleSearch() {
  paging.value?.reload?.()
}

function handleClear() {
  keyword.value = ''
  handleSearch()
}

function handleRetry() {
  paging.value?.reload?.()
}

function goDetail(course: Course) {
  if (!course?.id) {
    toast.warning('课程信息不完整，暂时无法查看详情')
    return
  }

  courseStore.setCurrentCourse(course)
  uni.navigateTo({
    url: `/pages-study/course/detail?id=${encodeURIComponent(course.id)}`,
  })
}
</script>

<style lang="scss" scoped>
.course-list-page {
  height: 100%;

  &__top {
    background: linear-gradient(180deg, #eef5ff 0%, #f6f9ff 100%);
    padding: 20rpx 24rpx 12rpx;
  }

  &__status {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 120rpx 24rpx;
    gap: 24rpx;
  }
}

:deep(.wd-search) {
  border-radius: 24rpx;
  overflow: hidden;
}
</style>
