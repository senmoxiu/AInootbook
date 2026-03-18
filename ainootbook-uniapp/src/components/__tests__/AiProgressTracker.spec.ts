import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AiProgressTracker from '../AiProgressTracker.vue'
import { useAiProgressStore } from '@/store/aiProgress'

// Mock uni API
global.uni = {
  request: vi.fn(),
  onAppShow: vi.fn(),
  onAppHide: vi.fn(),
  showToast: vi.fn(),
  navigateBack: vi.fn()
} as any

describe('AiProgressTracker.vue', () => {
  let wrapper: any
  let store: any
  const noteId = 'test-note-123'

  beforeEach(() => {
    vi.useFakeTimers()
    const pinia = createTestingPinia({ createSpy: vi.fn })
    store = useAiProgressStore()

    wrapper = mount(AiProgressTracker, {
      props: { noteId },
      global: {
        plugins: [pinia],
        stubs: {
          'wd-progress': true,
          'wd-steps': true,
          'wd-step': true,
          'wd-button': true,
          'wd-message-box': true
        }
      }
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('should use 2s interval initially', async () => {
    expect(uni.request).toHaveBeenCalledTimes(1)

    // Mock success response
    const successCallback = (uni.request as any).mock.calls[0][0].success
    successCallback({ statusCode: 200, data: { result: { progress: 10, status: 'processing' } } })

    vi.advanceTimersByTime(1999)
    expect(uni.request).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1)
    expect(uni.request).toHaveBeenCalledTimes(2)
  })

  it('should increase interval to 4s after 3 no-change responses', async () => {
    for (let i = 0; i < 4; i++) { // Initial + 3 no-change
      const lastCall = (uni.request as any).mock.calls[i][0]
      lastCall.success({ statusCode: 200, data: { result: { progress: 10, status: 'processing' } } })
      if (i < 3) vi.advanceTimersByTime(2000)
    }

    // The 4th poll should be scheduled after 4000ms
    vi.advanceTimersByTime(3999)
    expect(uni.request).toHaveBeenCalledTimes(4)
    vi.advanceTimersByTime(1)
    expect(uni.request).toHaveBeenCalledTimes(5)
  })

  it('should increase interval to 8s after another 3 no-change responses (total 6)', async () => {
    // Trigger 6 no-change updates
    for (let i = 0; i < 7; i++) {
      const lastCall = (uni.request as any).mock.calls[i][0]
      lastCall.success({ statusCode: 200, data: { result: { progress: 10, status: 'processing' } } })
      vi.runOnlyPendingTimers()
    }

    const lastCallCount = (uni.request as any).mock.calls.length
    vi.advanceTimersByTime(7999)
    expect(uni.request).toHaveBeenCalledTimes(lastCallCount)
    vi.advanceTimersByTime(1)
    expect(uni.request).toHaveBeenCalledTimes(lastCallCount + 1)
  })

  it('should recovery to 2s interval when progress changes', async () => {
    // 3 no-change calls to reach 4s interval
    for (let i = 0; i < 4; i++) {
      (uni.request as any).mock.calls[i][0].success({
        statusCode: 200, data: { result: { progress: 10, status: 'processing' } }
      })
      vi.runOnlyPendingTimers()
    }

    // Now at 4s interval. Send a changed progress.
    const lastCallIndex = (uni.request as any).mock.calls.length - 1;
    (uni.request as any).mock.calls[lastCallIndex][0].success({
      statusCode: 200, data: { result: { progress: 20, status: 'processing' } }
    })

    vi.advanceTimersByTime(2000)
    expect(uni.request).toHaveBeenCalledTimes(lastCallIndex + 2)
  })

  it('should ignore progress updates that are less than current progress (monotonicity)', async () => {
    // Set progress to 50
    (uni.request as any).mock.calls[0][0].success({
      statusCode: 200, data: { result: { progress: 50, status: 'processing' } }
    })
    expect(wrapper.vm.progress).toBe(50)

    // Try to update with 40
    vi.runOnlyPendingTimers();
    (uni.request as any).mock.calls[1][0].success({
      statusCode: 200, data: { result: { progress: 40, status: 'processing' } }
    })
    expect(wrapper.vm.progress).toBe(50)
  })

  it('should stop polling when status is COMPLETED or FAILED', async () => {
    (uni.request as any).mock.calls[0][0].success({
      statusCode: 200, data: { result: { progress: 100, status: 'completed' } }
    })

    vi.runAllTimers()
    expect(uni.request).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted().complete).toBeTruthy()
  })

  it('should stop polling and emit failed when timeout (10 mins) reached', async () => {
    // First poll success
    (uni.request as any).mock.calls[0][0].success({
      statusCode: 200, data: { result: { progress: 10, status: 'processing' } }
    })

    // Advance 10 minutes
    vi.advanceTimersByTime(10 * 60 * 1000 + 1)

    // Next poll should check time and fail
    vi.runOnlyPendingTimers()
    expect(wrapper.vm.status).toBe('failed')
    expect(wrapper.emitted().failed[0][0].message).toBe('Task timeout')
  })

  it('should retry 3 times with exponential backoff on 5xx errors', async () => {
    // Initial poll fails with 500
    (uni.request as any).mock.calls[0][0].success({ statusCode: 500 })

    // Retry 1: 2s
    vi.advanceTimersByTime(2000)
    expect(uni.request).toHaveBeenCalledTimes(2);
    (uni.request as any).mock.calls[1][0].success({ statusCode: 500 })

    // Retry 2: 4s
    vi.advanceTimersByTime(4000)
    expect(uni.request).toHaveBeenCalledTimes(3);
    (uni.request as any).mock.calls[2][0].success({ statusCode: 500 })

    // Retry 3: 8s
    vi.advanceTimersByTime(8000)
    expect(uni.request).toHaveBeenCalledTimes(4);
    (uni.request as any).mock.calls[3][0].success({ statusCode: 500 })

    expect(wrapper.vm.status).toBe('failed')
    expect(wrapper.emitted().failed).toBeTruthy()
  })

  it('should retry 3 times on network fail (timeout)', async () => {
    (uni.request as any).mock.calls[0][0].fail()

    vi.advanceTimersByTime(2000)
    expect(uni.request).toHaveBeenCalledTimes(2)
  })

  it('should fail immediately without retry on 4xx errors', async () => {
    (uni.request as any).mock.calls[0][0].success({ statusCode: 404 })

    vi.runAllTimers()
    expect(uni.request).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.status).toBe('failed')
  })

  it('should use 10s interval when app is in background', async () => {
    // Trigger app hide
    const onAppHideCallback = (uni.onAppHide as any).mock.calls[0][0]
    onAppHideCallback()

    // Complete first poll
    (uni.request as any).mock.calls[0][0].success({
      statusCode: 200, data: { result: { progress: 10, status: 'processing' } }
    })

    // Next poll should be 10s
    vi.advanceTimersByTime(9999)
    expect(uni.request).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(1)
    expect(uni.request).toHaveBeenCalledTimes(2)
  })

  it('should recovery to 2s and poll immediately when app returns to foreground', async () => {
    // Move to background
    (uni.onAppHide as any).mock.calls[0][0]()

    // Complete first poll
    (uni.request as any).mock.calls[0][0].success({
      statusCode: 200, data: { result: { progress: 10, status: 'processing' } }
    })

    // Wait 5s in background
    vi.advanceTimersByTime(5000)

    // Return to foreground
    const onAppShowCallback = (uni.onAppShow as any).mock.calls[0][0]
    onAppShowCallback()

    // Should poll immediately
    expect(uni.request).toHaveBeenCalledTimes(2)

    // Next poll should be 2s
    const lastCallIndex = (uni.request as any).mock.calls.length - 1;
    (uni.request as any).mock.calls[lastCallIndex][0].success({
      statusCode: 200, data: { result: { progress: 15, status: 'processing' } }
    })

    vi.advanceTimersByTime(2000)
    expect(uni.request).toHaveBeenCalledTimes(3)
  })

  it('should verify monotonicity property: progress percentage P must be non-decreasing', async () => {
    let lastProgress = 0

    for (let i = 0; i < 10; i++) {
      const mockProgress = Math.floor(Math.random() * 100);
      (uni.request as any).mock.calls[i][0].success({
        statusCode: 200, data: { result: { progress: mockProgress, status: 'processing' } }
      })

      if (mockProgress >= lastProgress) {
        expect(wrapper.vm.progress).toBe(mockProgress)
        lastProgress = mockProgress
      } else {
        expect(wrapper.vm.progress).toBe(lastProgress)
      }
      vi.runOnlyPendingTimers()
    }
  })
})
