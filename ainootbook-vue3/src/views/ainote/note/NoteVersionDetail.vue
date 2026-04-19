<template>
  <div class="version-detail">
    <div v-if="versionRecord" class="detail-content">
        <div class="detail-header">
          <h3>版本 v{{ versionRecord.version }}</h3>
          <span class="detail-time-row">{{ displayTime }}</span>
        </div>

        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="content" tab="笔记内容">
            <div class="content-actions">
              <a-space>
                <a-button size="small" @click="handleOpenDiff">与当前版本对比</a-button>
                <a-popconfirm
                  title="确认回滚到此版本？回滚后将生成新版本记录"
                  @confirm="handleRollback"
                >
                  <a-button type="primary" size="small" :loading="loading">回滚到此版本</a-button>
                </a-popconfirm>
              </a-space>
            </div>
            <a-spin :spinning="loading">
              <MarkdownViewer v-if="fullContent" :value="fullContent" />
              <a-empty v-else-if="!loading" description="暂无内容" />
            </a-spin>
          </a-tab-pane>
          <a-tab-pane key="summary" tab="AI 摘要">
            <div v-if="displaySummary" class="summary-content">
              {{ displaySummary }}
            </div>
            <a-empty v-else description="暂无摘要" />
          </a-tab-pane>
          <a-tab-pane key="keywords" tab="关键词">
            <div v-if="keywords.length > 0" class="keywords-content">
              <a-tag v-for="kw in keywords" :key="kw" color="blue">
                {{ kw }}
              </a-tag>
            </div>
            <a-empty v-else description="暂无关键词" />
          </a-tab-pane>
        </a-tabs>

      </div>

    <NoteVersionDiff @register="registerDiffModal" />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
import { getNoteVersionDetail, rollbackNote } from '/@/api/ainote/note.api';
import type { NoteVersionRecord } from '/@/api/ainote/note.api';
import { useMessage } from '/@/hooks/web/useMessage';
import { MarkdownViewer } from '/@/components/Markdown';
import { useModal } from '/@/components/Modal';
import NoteVersionDiff from './NoteVersionDiff.vue';

const props = defineProps<{
  versionRecord: NoteVersionRecord;
  noteId: string;
  currentContent?: string;
}>();

const emit = defineEmits<{
  (e: 'rollback'): void;
}>();

const { createMessage } = useMessage();
const [registerDiffModal, { openModal: openDiffModal }] = useModal();

const loading = ref(false);
const fullContent = ref<string>('');
const activeTab = ref('content');

const displaySummary = computed(() =>
  props.versionRecord.aiSummary || props.versionRecord.summary || ''
);
const displayTime = computed(() =>
  props.versionRecord.createTime || props.versionRecord.createdAt || ''
);

const keywords = computed(() => {
  const kw = props.versionRecord.keywords;
  if (!kw) return [];
  try {
    const parsed = JSON.parse(kw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return kw.split(',').map(k => k.trim()).filter(Boolean);
  }
});

// 切换版本时加载完整内容
watch(() => props.versionRecord.id, async (id) => {
  if (!id) return;
  loading.value = true;
  fullContent.value = '';
  try {
    const detail = await getNoteVersionDetail(id);
    fullContent.value = detail.noteContent || '';
  } catch {
    createMessage.error('加载版本内容失败');
  } finally {
    loading.value = false;
  }
}, { immediate: true });

function handleOpenDiff() {
  openDiffModal(true, {
    versionRecord: props.versionRecord,
    currentContent: props.currentContent || '',
  });
}

async function handleRollback() {  loading.value = true;
  try {
    await rollbackNote({
      noteId: props.noteId,
      targetVersion: props.versionRecord.version,
    });
    emit('rollback');
  } catch (error) {
    createMessage.error('版本回滚失败');
    console.error(error);
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="less" scoped>
.version-detail {
  padding: 16px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;

  h3 {
    margin: 0;
    font-size: 16px;
  }
}

.detail-time-row {
  font-size: 12px;
  color: #999;
}

.content-actions {
  margin-bottom: 12px;
}

.markdown-preview {
  min-height: 200px;
}

.summary-content {
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
  line-height: 1.8;
}

.keywords-content {
  padding: 16px;

  .ant-tag {
    margin: 4px;
  }
}

.detail-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  text-align: right;
}
</style>