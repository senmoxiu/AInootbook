<template>
  <view class="ai-progress-tracker">
    <view class="tracker-header">
      <text class="status-hint">{{ currentHint }}</text>
    </view>

    <view class="progress-section">
      <wd-progress :percentage="progress" />
      <text class="percentage-text">{{ progress }}%</text>
    </view>

    <view class="steps-section">
      <wd-steps :active="currentStep" align-center>
        <wd-step title="提取文本" :status="getStepStatus(0)" />
        <wd-step title="分析结构" :status="getStepStatus(1)" />
        <wd-step title="生成笔记" :status="getStepStatus(2)" />
      </wd-steps>
    </view>

    <view v-if="status === 'failed'" class="error-section">
      <text class="error-text">任务失败，请检查网络后重试</text>
      <wd-button size="small" type="primary" @click="handleRetry">重试任务</wd-button>
    </view>

    <view class="actions-section" v-if="status === 'processing' || status === 'idle'">
      <wd-button size="small" plain type="info" @click="handleCancel">取消任务</wd-button>
    </view>

    <wd-message-box id="cancel-confirm" />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useMessage } from 'wot-design-uni'
import { useAiProgressStore } from '@/store/aiProgress'

const props = defineProps<{
  noteId: string
}>()

const emit = defineEmits<{
  (e: 'update:progress', progress: number): void
  (e: 'complete'): void
  (e: 'failed', error: Error): void
  (e: 'cancelled'): void
}>()

const store = useAiProgressStore()
const message = useMessage()

const progress = ref(0)
const status = ref('idle')
const currentStep = ref(0)

const hints = ['正在分析素材...', '正在提取关键信息...', '正在生成笔记...']
const currentHintIndex = ref(0)
const currentHint = computed(() => hints[currentHintIndex.value])

let hintInterval: number | null = null
let pollTimer: number | null = null
let noChangeCount = 0
let isBackground = false
let startTime = 0
let networkRetryCount = 0

const MAX_TIMEOUT = 10 * 60 * 1000

const updateProgressState = (newProgress: number, newStatus: string) => {
  if (newProgress >= progress.value) {
    if (newProgress > progress.value) {
      noChangeCount = 0
    }
    progress.value = newProgress
    status.value = newStatus

    if (progress.value < 33) {
      currentStep.value = 0
    } else if (progress.value < 66) {
      currentStep.value = 1
    } else {
      currentStep.value = 2
    }

    store.saveTask(props.noteId, { progress: progress.value, status: status.value, step: currentStep.value })
    emit('update:progress', progress.value)
  }
}

const getStepStatus = (stepIndex: number) => {
  if (status.value === 'failed') {
    return stepIndex === currentStep.value ? 'error' : 'wait'
  }
  if (stepIndex < currentStep.value || status.value === 'completed') {
    return 'finished'
  }
  if (stepIndex === currentStep.value) {
    return 'process'
  }
  return 'wait'
}

const getPollInterval = () => {
  if (isBackground) return 10000
  if (noChangeCount >= 9) return 10000
  if (noChangeCount >= 6) return 8000
  if (noChangeCount >= 3) return 4000
  return 2000
}

const fetchProgress = () => {
  if (status.value === 'completed' || status.value === 'failed') {
    return
  }

  if (Date.now() - startTime > MAX_TIMEOUT) {
    status.value = 'failed'
    emit('failed', new Error('Task timeout'))
    return
  }

  uni.request({
    url: `/ainote/note/progress?noteId=${props.noteId}`,
    method: 'GET',
    success: (res: UniApp.RequestSuccessCallbackResult) => {
      networkRetryCount = 0
      if (res.statusCode >= 200 && res.statusCode < 300) {
        const responseData = res.data as Record<string, any> || {}
        const data = responseData.result || responseData.data || responseData
        const newProgress = data.progress || 0
        const newStatus = data.status || 'processing'

        if (newProgress === progress.value) {
          noChangeCount++
        }

        updateProgressState(newProgress, newStatus)

        if (newStatus === 'completed' || newProgress >= 100) {
          status.value = 'completed'
          progress.value = 100
          emit('complete')
          store.removeTask(props.noteId)
        } else if (newStatus === 'failed') {
          status.value = 'failed'
          emit('failed', new Error('Task failed from server'))
        } else {
          scheduleNextPoll()
        }
      } else if (res.statusCode >= 400 && res.statusCode < 500) {
        status.value = 'failed'
        emit('failed', new Error(`Client error: ${res.statusCode}`))
      } else if (res.statusCode >= 500) {
        handleNetworkError()
      }
    },
    fail: () => {
      handleNetworkError()
    }
  })
}

const handleNetworkError = () => {
  if (networkRetryCount < 3) {
    const backoff = Math.pow(2, networkRetryCount) * 2000
    networkRetryCount++
    pollTimer = setTimeout(fetchProgress, backoff) as unknown as number
  } else {
    status.value = 'failed'
    emit('failed', new Error('Network error max retries reached'))
  }
}

const scheduleNextPoll = () => {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = setTimeout(fetchProgress, getPollInterval()) as unknown as number
}

const startPolling = () => {
  if (!startTime) startTime = Date.now()
  fetchProgress()
}

const stopPolling = () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const handleCancel = () => {
  message.confirm({
    title: '确认取消',
    msg: '是否确认取消当前 AI 生成任务？',
    confirmButtonText: '确定',
    cancelButtonText: '暂不取消'
  }).then(() => {
    stopPolling()
    uni.request({
      url: '/ainote/note/cancelGeneration',
      method: 'POST',
      data: { noteId: props.noteId },
      success: (res: UniApp.RequestSuccessCallbackResult) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          status.value = 'failed'
          store.removeTask(props.noteId)
          emit('cancelled')
          uni.navigateBack()
        } else {
          uni.showToast({ title: '取消请求失败', icon: 'none' })
          scheduleNextPoll()
        }
      },
      fail: () => {
        uni.showToast({ title: '取消请求失败', icon: 'none' })
        scheduleNextPoll()
      }
    })
  }).catch(() => {})
}

const handleRetry = () => {
  status.value = 'processing'
  progress.value = 0
  noChangeCount = 0
  networkRetryCount = 0
  startTime = Date.now()
  startPolling()
}

onMounted(() => {
  const cached = store.getTask(props.noteId)
  if (cached) {
    progress.value = cached.progress
    status.value = cached.status
    currentStep.value = cached.step
  }

  hintInterval = setInterval(() => {
    currentHintIndex.value = (currentHintIndex.value + 1) % hints.length
  }, 5000) as unknown as number

  startPolling()
})

onUnmounted(() => {
  stopPolling()
  if (hintInterval) clearInterval(hintInterval)
})

uni.onAppShow(() => {
  isBackground = false
  if (status.value === 'processing' || status.value === 'idle') {
    noChangeCount = 0
    stopPolling()
    fetchProgress()
  }
})

uni.onAppHide(() => {
  isBackground = true
})
</script>

<style lang="scss" scoped>
.ai-progress-tracker {
  padding: 24px;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);

  .tracker-header {
    text-align: center;
    margin-bottom: 24px;

    .status-hint {
      font-size: 16px;
      color: #323233;
      font-weight: 500;
      animation: pulse 2s infinite ease-in-out;
    }
  }

  .progress-section {
    display: flex;
    align-items: center;
    margin-bottom: 32px;

    :deep(.wd-progress) {
      flex: 1;
    }

    .percentage-text {
      margin-left: 16px;
      font-size: 14px;
      color: #666666;
      font-weight: bold;
      min-width: 44px;
      text-align: right;
    }
  }

  .steps-section {
    margin-bottom: 32px;
  }

  .error-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    margin-top: 24px;

    .error-text {
      color: #ee0a24;
      font-size: 14px;
    }
  }

  .actions-section {
    display: flex;
    justify-content: center;
    margin-top: 24px;
  }
}

@keyframes pulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}
</style>
