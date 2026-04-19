<template>
  <BasicModal
    v-bind="$attrs"
    title="版本对比"
    :width="1400"
    :footer="null"
    @register="registerModal"
  >
    <div class="diff-header">
      <div class="diff-col-title">
        <span>历史版本 v{{ versionRecord?.version }}</span>
        <span class="diff-time">{{ displayTime }}</span>
      </div>
      <div class="diff-col-title">
        <span>当前版本（最新）</span>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div class="diff-container">
        <!-- 左侧：历史版本 -->
        <div class="diff-panel diff-panel-old">
          <div
            v-for="(line, idx) in diffResult"
            :key="'old-' + idx"
            :class="['diff-line', getDiffClass(line, 'old')]"
          >
            <span class="diff-line-no">{{ getLineNo(line, 'old', idx) }}</span>
            <span class="diff-line-sign">{{ getDiffSign(line, 'removed') }}</span>
            <pre class="diff-line-content">{{ line.removed ? line.value : (line.added ? '' : line.value) }}</pre>
          </div>
        </div>

        <!-- 右侧：当前版本 -->
        <div class="diff-panel diff-panel-new">
          <div
            v-for="(line, idx) in diffResult"
            :key="'new-' + idx"
            :class="['diff-line', getDiffClass(line, 'new')]"
          >
            <span class="diff-line-no">{{ getLineNo(line, 'new', idx) }}</span>
            <span class="diff-line-sign">{{ getDiffSign(line, 'added') }}</span>
            <pre class="diff-line-content">{{ line.added ? line.value : (line.removed ? '' : line.value) }}</pre>
          </div>
        </div>
      </div>

      <a-empty v-if="!loading && diffResult.length === 0" description="暂无差异内容" />
    </a-spin>

    <div class="diff-legend">
      <span class="legend-item legend-removed">删除</span>
      <span class="legend-item legend-added">新增</span>
      <span class="legend-item legend-unchanged">未变更</span>
    </div>
  </BasicModal>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { diffLines as computeDiff } from 'diff';
import type { Change } from 'diff';
import { BasicModal, useModalInner } from '/@/components/Modal';
import { getNoteVersionDetail } from '/@/api/ainote/note.api';
import type { NoteVersionRecord } from '/@/api/ainote/note.api';
import { useMessage } from '/@/hooks/web/useMessage';

const emit = defineEmits(['register']);
const { createMessage } = useMessage();

const loading = ref(false);
const versionRecord = ref<NoteVersionRecord | null>(null);
const historyContent = ref('');
const currentContent = ref('');

const displayTime = computed(() =>
  versionRecord.value?.createTime || versionRecord.value?.createdAt || ''
);

const diffResult = computed<Change[]>(() => {
  if (!historyContent.value && !currentContent.value) return [];
  return computeDiff(historyContent.value, currentContent.value);
});

const [registerModal] = useModalInner(async (data: {
  versionRecord: NoteVersionRecord;
  currentContent: string;
}) => {
  versionRecord.value = data.versionRecord;
  currentContent.value = data.currentContent || '';
  historyContent.value = '';

  if (!data.versionRecord.id) {
    createMessage.error('版本 ID 缺失，无法加载对比内容');
    return;
  }

  loading.value = true;
  try {
    const detail = await getNoteVersionDetail(data.versionRecord.id);
    historyContent.value = detail.noteContent || '';
  } catch {
    createMessage.error('加载历史版本内容失败');
  } finally {
    loading.value = false;
  }
});

function getDiffClass(line: Change, side: 'old' | 'new'): string {
  if (line.removed && side === 'old') return 'diff-removed';
  if (line.added && side === 'new') return 'diff-added';
  if (line.removed && side === 'new') return 'diff-empty';
  if (line.added && side === 'old') return 'diff-empty';
  return 'diff-unchanged';
}

function getDiffSign(line: Change, type: 'added' | 'removed'): string {
  if (type === 'removed' && line.removed) return '-';
  if (type === 'added' && line.added) return '+';
  return ' ';
}

function getLineNo(line: Change, side: 'old' | 'new', idx: number): string {
  if (side === 'old' && line.added) return '';
  if (side === 'new' && line.removed) return '';
  return String(idx + 1);
}
</script>

<style lang="less" scoped>
.diff-header {
  display: flex;
  gap: 2px;
  margin-bottom: 8px;
}

.diff-col-title {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 4px;
  font-weight: 600;
  font-size: 14px;
}

.diff-time {
  font-size: 12px;
  color: #999;
  font-weight: normal;
}

.diff-container {
  display: flex;
  gap: 2px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
  max-height: 65vh;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.diff-panel {
  flex: 1;
  overflow-x: auto;
}

.diff-panel-old {
  border-right: 1px solid #e8e8e8;
}

.diff-line {
  display: flex;
  align-items: flex-start;
  min-height: 22px;
  line-height: 22px;

  &.diff-removed {
    background: #fff1f0;
  }

  &.diff-added {
    background: #f6ffed;
  }

  &.diff-empty {
    background: #fafafa;
  }

  &.diff-unchanged {
    background: #fff;
  }
}

.diff-line-no {
  min-width: 36px;
  padding: 0 8px;
  color: #bbb;
  text-align: right;
  user-select: none;
  border-right: 1px solid #e8e8e8;
  font-size: 12px;
}

.diff-line-sign {
  min-width: 20px;
  padding: 0 4px;
  font-weight: bold;
  user-select: none;

  .diff-removed & { color: #f5222d; }
  .diff-added & { color: #52c41a; }
}

.diff-line-content {
  flex: 1;
  margin: 0;
  padding: 0 8px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  font-size: inherit;
  line-height: 22px;
}

.diff-legend {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;

  &::before {
    content: '';
    display: inline-block;
    width: 12px;
    height: 12px;
    border-radius: 2px;
  }

  &.legend-removed::before { background: #fff1f0; border: 1px solid #ffa39e; }
  &.legend-added::before { background: #f6ffed; border: 1px solid #b7eb8f; }
  &.legend-unchanged::before { background: #fff; border: 1px solid #e8e8e8; }
}
</style>
