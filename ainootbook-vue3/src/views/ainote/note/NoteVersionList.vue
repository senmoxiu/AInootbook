<template>
  <div class="version-list">
    <a-spin :spinning="loading">
      <div
        v-for="version in versions"
        :key="version.version"
        :class="['version-item', { active: version.version === selectedVersion }]"
        @click="handleSelect(version)"
      >
        <div class="version-header">
          <span class="version-number">v{{ version.version }}</span>
          <span class="version-time">{{ version.createdAt || version.createTime }}</span>
        </div>
        <div class="version-summary">
          {{ (version.aiSummary || version.summary) ? truncate(version.aiSummary || version.summary || '', 50) : '无摘要' }}
        </div>
      </div>
      <a-button
        v-if="hasMore"
        type="link"
        block
        @click="loadMore"
        :loading="loading"
      >
        加载更多
      </a-button>
      <a-empty v-if="!loading && versions.length === 0" description="暂无版本历史" />
    </a-spin>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
import { getNoteVersions } from '/@/api/ainote/note.api';
import type { NoteVersionRecord } from '/@/api/ainote/note.api';

const props = defineProps<{
  noteId: string;
  selectedVersion?: number;
}>();

const emit = defineEmits<{
  (e: 'select', version: number, record: NoteVersionRecord): void;
}>();

const loading = ref(false);
const versions = ref<NoteVersionRecord[]>([]);
const pageNo = ref(1);
const pageSize = 10;
const total = ref(0);

const hasMore = computed(() => versions.value.length < total.value);

watch(() => props.noteId, (id) => {
  if (id) {
    versions.value = [];
    pageNo.value = 1;
    total.value = 0;
    loadVersions();
  }
}, { immediate: true });

async function loadVersions() {
  if (loading.value) return;

  loading.value = true;
  try {
    const res = await getNoteVersions({
      noteId: props.noteId,
      pageNo: pageNo.value,
      pageSize,
    });
    versions.value.push(...res.records);
    total.value = res.total;
  } catch (error) {
    console.error('加载版本列表失败', error);
  } finally {
    loading.value = false;
  }
}

function loadMore() {
  pageNo.value++;
  loadVersions();
}

function handleSelect(version: NoteVersionRecord) {
  emit('select', version.version, version);
}

function truncate(text: string, length: number): string {
  if (text.length <= length) return text;
  return text.substring(0, length) + '...';
}
</script>

<style lang="less" scoped>
.version-list {
  padding: 16px 0;
}

.version-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid transparent;

  &:hover {
    background: #f5f5f5;
  }

  &.active {
    background: #e6f7ff;
    border-color: #1890ff;
    border-left-width: 3px;
  }
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.version-number {
  font-weight: 600;
  color: #1890ff;
}

.version-time {
  font-size: 12px;
  color: #999;
}

.version-summary {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}
</style>
