<template>
  <a-card>
    <div v-if="status === 'idle'">
      <a-button type="primary" @click="handleStart">开始生成</a-button>
    </div>
    <div v-else-if="status === 'processing'">
      <a-progress :percent="progress" />
      <a-button danger @click="handleCancel" style="margin-top: 10px">取消生成</a-button>
    </div>
    <div v-else-if="status === 'completed'">
      <a-result status="success" title="生成完成" />
    </div>
    <div v-else-if="status === 'failed'">
      <a-result status="error" :title="errorMsg">
        <template #extra>
          <a-button @click="handleStart">重新生成</a-button>
        </template>
      </a-result>
    </div>
  </a-card>
</template>

<script lang="ts" setup>
  import { ref, onUnmounted, watch } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    getGenerationProgress,
    triggerGeneration,
    cancelGeneration,
  } from '/@/api/ainote/note.api';

  const props = defineProps({
    noteId: {
      type: String,
      required: true,
    },
    knowledgeId: {
      type: String,
      required: true,
    },
  });

  const emit = defineEmits(['completed']);
  const { createMessage } = useMessage();

  const status = ref<'idle' | 'processing' | 'completed' | 'failed'>('idle');
  const progress = ref<number>(0);
  const errorMsg = ref<string>('');
  let timer: ReturnType<typeof setTimeout> | null = null;

  function stopPolling() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }

  function startPolling() {
    stopPolling();
    async function poll() {
      try {
        const res = await getGenerationProgress({
          noteId: props.noteId,
          knowledgeId: props.knowledgeId,
        });
        progress.value = res.progress ?? 0;
        if (res.status === 'completed') {
          status.value = 'completed';
          return;
        } else if (res.status === 'failed') {
          status.value = 'failed';
          errorMsg.value = res.errorMsg || '生成过程发生错误';
          return;
        }
      } catch (error: unknown) {
        status.value = 'failed';
        errorMsg.value = (error as Error).message || '获取进度失败';
        return;
      }
      // IN-02: 上一个请求完成后再调度下一次，防止堆积
      timer = setTimeout(poll, 2000);
    }
    timer = setTimeout(poll, 2000);
  }

  async function handleStart() {
    try {
      status.value = 'processing';
      progress.value = 0;
      errorMsg.value = '';
      await triggerGeneration({
        noteId: props.noteId,
        knowledgeId: props.knowledgeId,
      });
      startPolling();
    } catch (error: unknown) {
      status.value = 'failed';
      errorMsg.value = (error as Error).message || '启动生成失败';
      createMessage.error(errorMsg.value);
    }
  }

  async function handleCancel() {
    try {
      await cancelGeneration({ noteId: props.noteId });
      stopPolling();
      status.value = 'idle';
      progress.value = 0;
    } catch (error: unknown) {
      createMessage.error((error as Error).message || '取消生成失败');
    }
  }

  watch(status, (newStatus) => {
    if (newStatus === 'completed') {
      emit('completed');
    }
  });

  onUnmounted(() => {
    stopPolling();
  });
</script>
