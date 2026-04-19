<template>
  <BasicModal
    v-bind="$attrs"
    title="AI 重新生成"
    :width="600"
    @register="registerModal"
    @ok="handleSubmit"
    :confirmLoading="submitting"
    :maskClosable="!isGenerating"
  >
    <div v-if="!isGenerating" class="regenerate-form">
      <a-form :model="formData" layout="vertical">
        <a-form-item label="基准版本（可选）">
          <a-select
            v-model:value="formData.baseVersion"
            placeholder="默认使用当前版本"
            allowClear
          >
            <a-select-option
              v-for="v in versionOptions"
              :key="v.version"
              :value="v.version"
            >
              v{{ v.version }} - {{ v.createTime }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="补充说明（可选）">
          <a-textarea
            v-model:value="formData.additionalContent"
            :rows="4"
            placeholder="例如：请重点关注第三章的内容，提取更多技术关键词"
          />
        </a-form-item>
      </a-form>
    </div>

    <div v-else class="regenerate-progress">
      <a-progress :percent="progress" status="active" />
      <div class="progress-status">{{ statusText }}</div>
      <div v-if="estimatedTime > 0" class="progress-time">
        预计剩余时间：约 {{ estimatedTime }} 秒
      </div>
      <a-button type="link" danger @click="handleCancelGeneration">
        取消生成
      </a-button>
    </div>
  </BasicModal>
</template>

<script lang="ts" setup>
import { ref, computed, onUnmounted } from 'vue';
import { BasicModal, useModalInner } from '/@/components/Modal';
import { useMessage } from '/@/hooks/web/useMessage';
import {
  regenerateNote,
  getGenerationProgress,
  cancelGeneration,
  getNoteVersions,
} from '/@/api/ainote/note.api';
import type { NoteVersionRecord, RegenerateParams } from '/@/api/ainote/note.api';

const emit = defineEmits(['register', 'success']);
const { createMessage } = useMessage();

const noteId = ref<string>('');
const submitting = ref(false);
const isGenerating = ref(false);
const progress = ref(0);
const statusText = ref('');
const progressTimer = ref<number>();
const versionOptions = ref<NoteVersionRecord[]>([]);

const formData = ref<RegenerateParams>({
  noteId: '',
  baseVersion: undefined,
  additionalContent: '',
});

const estimatedTime = computed(() => {
  if (progress.value === 0) return 60;
  const remaining = 100 - progress.value;
  return Math.ceil(remaining / 2);
});

const [registerModal, { closeModal }] = useModalInner(async (data) => {
  noteId.value = data.noteId;
  formData.value.noteId = data.noteId;
  formData.value.baseVersion = undefined;
  formData.value.additionalContent = '';
  isGenerating.value = false;
  progress.value = 0;

  await loadVersionOptions();
});

async function loadVersionOptions() {
  try {
    const res = await getNoteVersions({
      noteId: noteId.value,
      pageNo: 1,
      pageSize: 10,
    });
    versionOptions.value = res.records;
  } catch (error) {
    console.error('加载版本列表失败', error);
  }
}

async function handleSubmit() {
  submitting.value = true;
  try {
    await regenerateNote(formData.value);
    isGenerating.value = true;
    startProgressPolling();
  } catch (error) {
    createMessage.error('触发生成失败');
    console.error(error);
  } finally {
    submitting.value = false;
  }
}

function startProgressPolling() {
  progressTimer.value = window.setInterval(async () => {
    try {
      const result = await getGenerationProgress({ noteId: noteId.value });
      progress.value = result.progress;
      statusText.value = getStatusText(result.status);

      if (result.status === 'completed') {
        stopProgressPolling();
        createMessage.success('AI 生成完成');
        closeModal();
        emit('success');
      } else if (result.status === 'failed') {
        stopProgressPolling();
        createMessage.error(result.errorMsg || 'AI 生成失败');
        isGenerating.value = false;
      }
    } catch (error) {
      stopProgressPolling();
      createMessage.error('查询进度失败');
      isGenerating.value = false;
    }
  }, 2000);
}

function stopProgressPolling() {
  if (progressTimer.value) {
    clearInterval(progressTimer.value);
    progressTimer.value = undefined;
  }
}

async function handleCancelGeneration() {
  try {
    await cancelGeneration({ noteId: noteId.value });
    stopProgressPolling();
    isGenerating.value = false;
    createMessage.info('已取消生成');
  } catch (error) {
    createMessage.error('取消失败');
  }
}

function getStatusText(status: string): string {
  const map: Record<string, string> = {
    idle: '准备中...',
    processing: '正在生成...',
    completed: '生成完成',
    failed: '生成失败',
  };
  return map[status] || '未知状态';
}

onUnmounted(() => {
  stopProgressPolling();
});
</script>

<style lang="less" scoped>
.regenerate-form {
  padding: 16px 0;
}

.regenerate-progress {
  padding: 32px 16px;
  text-align: center;
}

.progress-status {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
}

.progress-time {
  margin-top: 8px;
  font-size: 14px;
  color: #999;
}
</style>