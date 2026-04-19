<template>
  <div class="regenerate-progress">
    <a-progress :percent="progress" :status="progressStatus" />
    <div class="progress-text">{{ statusText }}</div>
  </div>
</template>

<script lang="ts" setup>
  import { ref, computed, onUnmounted } from 'vue';
  import { getGenerationProgress } from '/@/api/ainote/note.api';

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

  const emit = defineEmits(['completed', 'failed']);

  const progress = ref<number>(0);
  const status = ref<'processing' | 'completed' | 'failed'>('processing');
  let timer: number | null = null;

  const progressStatus = computed(() => {
    if (status.value === 'completed') return 'success';
    if (status.value === 'failed') return 'exception';
    return 'active';
  });

  const statusText = computed(() => {
    if (status.value === 'completed') return 'AI 重新生成完成';
    if (status.value === 'failed') return 'AI 重新生成失败';
    return `AI 正在重新生成... ${progress.value}%`;
  });

  startPolling();

  async function startPolling() {
    const maxRetries = 150;
    let retries = 0;

    while (retries < maxRetries) {
      try {
        const res = await getGenerationProgress({
          noteId: props.noteId,
          knowledgeId: props.knowledgeId,
        });

        progress.value = res.progress ?? 0;

        if (res.status === 'completed') {
          status.value = 'completed';
          emit('completed');
          return;
        }

        if (res.status === 'failed') {
          status.value = 'failed';
          emit('failed', res.errorMsg || 'AI 重新生成失败');
          return;
        }
      } catch (error: unknown) {
        status.value = 'failed';
        emit('failed', (error as Error).message || '获取进度失败');
        return;
      }

      await wait(2000);
      retries++;
    }

    throw new Error('AI 重新生成超时，请稍后刷新查看结果');
  }

  function wait(ms: number) {
    return new Promise<void>((resolve) => {
      timer = window.setTimeout(() => {
        timer = null;
        resolve();
      }, ms);
    });
  }

  function stopPolling() {
    if (timer) {
      window.clearTimeout(timer);
      timer = null;
    }
  }

  onUnmounted(() => {
    stopPolling();
  });
</script>

<style lang="less" scoped>
  .regenerate-progress {
    padding: 24px 8px 8px;
  }

  .progress-text {
    margin-top: 12px;
    color: #666;
  }
</style>
