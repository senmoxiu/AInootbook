<template>
  <view
    :class="['course-card', { 'course-card--static': !clickable }]"
    hover-class="course-card--hover"
    @click="handleClick"
  >
    <view class="course-card__header">
      <view class="course-card__title-wrap">
        <text class="course-card__title">{{ course.courseName || '未命名课程' }}</text>
        <text v-if="course.courseCode" class="course-card__code">{{ course.courseCode }}</text>
      </view>
      <wd-icon v-if="clickable" name="arrow-right" size="16px" />
    </view>

    <view class="course-card__meta">
      <wd-tag round plain>{{ course.teacherName || '未设置教师' }}</wd-tag>
      <wd-tag type="primary" plain round>{{ course.semester || '学期待定' }}</wd-tag>
      <wd-tag type="success" plain round>{{ chapterText }}</wd-tag>
    </view>

    <text v-if="showDescription" class="course-card__desc">{{ description }}</text>

    <view class="course-card__progress">
      <view class="course-card__progress-top">
        <text class="course-card__progress-label">学习进度</text>
        <text class="course-card__progress-value">{{ progress }}%</text>
      </view>
      <wd-progress :percentage="progress" hide-text color="#3d8bff" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Course } from '@/api/course'
import { normalizeProgress } from '../utils'

const props = withDefaults(
  defineProps<{
    course: Course
    clickable?: boolean
    showDescription?: boolean
  }>(),
  {
    clickable: true,
    showDescription: false,
  },
)

const emit = defineEmits<{
  click: [course: Course]
}>()

const progress = computed(() => normalizeProgress(props.course.studyProgress))

const chapterText = computed(() => {
  const count = Number(props.course.chapterCount ?? 0)
  return Number.isFinite(count) && count > 0 ? `${count} 个章节` : '章节待更新'
})

const description = computed(() => {
  return props.course.description?.trim() || '暂无课程简介'
})

function handleClick() {
  if (!props.clickable) {
    return
  }

  emit('click', props.course)
}
</script>

<style lang="scss" scoped>
.course-card {
  margin: 20rpx 24rpx 0;
  padding: 28rpx;
  border-radius: 24rpx;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 18rpx 40rpx rgba(37, 71, 128, 0.08);
  transition: transform 0.2s ease;

  &--hover {
    transform: translateY(-2rpx);
  }

  &--static {
    margin-top: 0;
  }

  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16rpx;
  }

  &__title-wrap {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
  }

  &__title {
    font-size: 34rpx;
    font-weight: 600;
    color: #20304a;
    line-height: 1.45;
    word-break: break-all;
  }

  &__code {
    margin-top: 8rpx;
    font-size: 24rpx;
    color: #7c8aa4;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin-top: 20rpx;
  }

  &__desc {
    display: block;
    margin-top: 20rpx;
    font-size: 28rpx;
    color: #50607a;
    line-height: 1.8;
  }

  &__progress {
    margin-top: 24rpx;
  }

  &__progress-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;
  }

  &__progress-label {
    font-size: 26rpx;
    color: #61708a;
  }

  &__progress-value {
    font-size: 26rpx;
    font-weight: 600;
    color: #1d74ff;
  }
}
</style>
