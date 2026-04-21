<template>
  <div>
    <!-- idle：无任务，直接触发生成 -->
    <div v-if="status === 'idle'">
      <a-button type="primary" :loading="starting" @click="handleStart">开始生成</a-button>
    </div>

    <!-- processing / completed / failed：分步进度条 -->
    <div v-else>
      <!-- 有分步数据：每步独立进度条 -->
      <div v-if="steps.length" class="space-y-3 mb-4">
        <div v-for="step in steps" :key="step.key" class="flex items-center gap-3">
          <span class="w-20 text-sm shrink-0 text-right text-gray-600">{{ step.label }}</span>
          <a-progress
            class="flex-1"
            :percent="step.progress"
            :status="stepProgressStatus(step.status)"
            size="small"
          />
          <span class="w-14 text-xs shrink-0" :class="stepTextClass(step.status)">
            {{ stepLabel(step.status) }}
          </span>
        </div>
      </div>

      <!-- 无分步时降级为整体进度条 -->
      <a-progress v-else-if="status === 'processing'" :percent="progress" class="mb-4" />

      <!-- 底部操作区 -->
      <div class="flex items-center gap-2 mt-2">
        <a-button v-if="status === 'processing'" danger @click="handleCancel">取消生成</a-button>
        <a-button v-if="status === 'completed'" type="primary" @click="emit('openRegenerate')">重新生成</a-button>
        <a-button v-if="status === 'failed'" @click="emit('openRegenerate')">重新生成</a-button>
        <span v-if="status === 'completed'" class="text-green-600 text-sm">生成完成</span>
        <span v-if="status === 'failed'" class="text-red-500 text-sm">{{ errorMsg }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { ref, onMounted, onUnmounted, watch } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getGenerationProgress, cancelGeneration, triggerGeneration } from '/@/api/ainote/note.api';
  import type { NoteProgressStep } from '/@/api/ainote/note.api';

  const props = defineProps({
    noteId: { type: String, required: true },
    knowledgeId: { type: String, required: true },
  });

  const emit = defineEmits(['completed', 'openRegenerate']);
  const { createMessage } = useMessage();

  const status = ref<'idle' | 'processing' | 'completed' | 'failed'>('idle');
  const progress = ref<number>(0);
  const errorMsg = ref<string>('');
  const steps = ref<NoteProgressStep[]>([]);
  const starting = ref(false);
  let timer: ReturnType<typeof setTimeout> | null = null;

  // a-progress 的 status 属性
  function stepProgressStatus(s: string) {
    if (s === 'failed') return 'exception';
    if (s === 'completed') return 'success';
    if (s === 'processing') return 'active';
    return 'normal';
  }

  function stepTextClass(s: string) {
    if (s === 'failed') return 'text-red-500';
    if (s === 'completed') return 'text-green-600';
    if (s === 'processing') return 'text-blue-500';
    return 'text-gray-400';
  }

  function stepLabel(s: string) {
    const map: Record<string, string> = {
      pending: '等待中',
      processing: '处理中',
      completed: '已完成',
      failed: '失败',
      skipped: '已跳过',
    };
    return map[s] ?? s;
  }
  function stopPolling() {
    if (timer) { clearTimeout(timer); timer = null; }
  }

  function startPolling() {
    stopPolling();
    let consecutiveErrors = 0;
    const MAX_ERRORS = 5; // 连续 5 次网络错误才判定失败

    async function poll() {
      try {
        const res = await getGenerationProgress({ noteId: props.noteId, knowledgeId: props.knowledgeId });
        consecutiveErrors = 0; // 成功则重置
        progress.value = res.progress ?? 0;
        if (res.steps?.length) steps.value = res.steps;
        if (res.status === 'completed') {
          status.value = 'completed';
          return;
        } else if (res.status === 'failed') {
          status.value = 'failed';
          errorMsg.value = res.errorMsg || '生成过程发生错误';
          return;
        }
      } catch (error: unknown) {
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_ERRORS) {
          status.value = 'failed';
          errorMsg.value = '网络连接异常，请刷新页面重试';
          return;
        }
        // 网络抖动/超时：静默重试，间隔拉长
        timer = setTimeout(poll, 4000);
        return;
      }
      timer = setTimeout(poll, 2000);
    }
    timer = setTimeout(poll, 2000);
  }

  function startProgress() {
    status.value = 'processing';
    progress.value = 0;
    errorMsg.value = '';
    steps.value = [];
    startPolling();
  }

  async function handleStart() {
    try {
      starting.value = true;
      await triggerGeneration({ noteId: props.noteId, knowledgeId: props.knowledgeId });
      startProgress();
    } catch (error: unknown) {
      createMessage.error((error as Error).message || '启动生成失败');
    } finally {
      starting.value = false;
    }
  }

  async function handleCancel() {
    try {
      await cancelGeneration({ noteId: props.noteId });
      stopPolling();
      status.value = 'idle';
      progress.value = 0;
      steps.value = [];
    } catch (error: unknown) {
      createMessage.error((error as Error).message || '取消生成失败');
    }
  }

  watch(status, (s) => {
    if (s === 'completed') {
      setTimeout(() => emit('completed'), 800);
    }
  });

  // 挂载时查一次进度，恢复正确状态（避免刷新后显示「开始生成」）
  onMounted(async () => {
    try {
      const res = await getGenerationProgress({ noteId: props.noteId, knowledgeId: props.knowledgeId });
      if (res.steps?.length) steps.value = res.steps;
      progress.value = res.progress ?? 0;
      if (res.status === 'completed') {
        status.value = 'completed';
      } else if (res.status === 'processing') {
        status.value = 'processing';
        startPolling();
      } else if (res.status === 'failed') {
        status.value = 'failed';
        errorMsg.value = res.errorMsg || '生成失败';
      }
      // idle 保持默认，显示「开始生成」
    } catch {
      // 查询失败静默处理，保持 idle
    }
  });

  onUnmounted(() => stopPolling());
  defineExpose({ startProgress });
</script>
