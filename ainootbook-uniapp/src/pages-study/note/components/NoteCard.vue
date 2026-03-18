<template>
  <view class="note-card" @click="$emit('click', note)">
    <view class="note-card__header">
      <text class="note-card__title">{{ note.title }}</text>
      <wd-tag v-if="note.isPublic" type="success" plain size="small">公开</wd-tag>
    </view>

    <view class="note-card__info">
      <view class="note-card__row">
        <wd-icon name="notes" size="14px" color="#999" />
        <text class="note-card__label">{{ note.courseName || '未归类课程' }}</text>
      </view>
      <view v-if="note.chapterName" class="note-card__row">
        <wd-icon name="list" size="14px" color="#999" />
        <text class="note-card__label">{{ note.chapterName }}</text>
      </view>
    </view>

    <view v-if="note.summary" class="note-card__summary">
      {{ note.summary }}
    </view>

    <view class="note-card__footer">
      <text class="note-card__time">{{ note.createTime || '-' }}</text>
      <wd-icon name="arrow-right" size="16px" color="#ccc" />
    </view>
  </view>
</template>

<script setup lang="ts">
import type { Note } from '@/api/note'

defineProps<{
  note: Note
}>()

defineEmits<{
  (e: 'click', note: Note): void
}>()
</script>

<style lang="scss" scoped>
.note-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16rpx;
  }

  &__title {
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
    flex: 1;
    margin-right: 16rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__info {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
    margin-bottom: 16rpx;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 8rpx;
  }

  &__label {
    font-size: 24rpx;
    color: #666;
  }

  &__summary {
    font-size: 26rpx;
    color: #888;
    line-height: 1.5;
    margin-bottom: 20rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1rpx solid #f2f3f5;
    padding-top: 16rpx;
  }

  &__time {
    font-size: 22rpx;
    color: #999;
  }
}
</style>
