import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mockState = vi.hoisted(() => {
  const request = vi.fn()
  const uploadFile = vi.fn()
  const downloadFile = vi.fn()
  const showToast = vi.fn()
  const navigateTo = vi.fn()
  const getStorageSync = vi.fn()
  const setStorageSync = vi.fn()

  return {
    baseUrl: 'https://api.example.com',
    request,
    uploadFile,
    downloadFile,
    showToast,
    navigateTo,
    getStorageSync,
    setStorageSync,
    userStore: {
      userInfo: { token: '', tenantId: 0 } as Partial<IUserInfo>,
      clearUserInfo: vi.fn(),
    },
  }
})

vi.mock('@/store', () => ({
  useUserStore: () => mockState.userStore,
}))

vi.mock('@/utils', () => ({
  getEnvBaseUrl: () => mockState.baseUrl,
}))

vi.mock('@/utils/platform', () => ({
  platform: 'h5',
}))

import request, { clearRequestCache, downloadFile, http, uploadFile } from '@/utils/request'

type RequestTask = { abort: ReturnType<typeof vi.fn> }
type UploadTask = {
  abort: ReturnType<typeof vi.fn>
  onProgressUpdate: ReturnType<typeof vi.fn>
}
type DownloadTask = {
  abort: ReturnType<typeof vi.fn>
  onProgressUpdate: ReturnType<typeof vi.fn>
}

function createRequestTask(): RequestTask {
  return {
    abort: vi.fn(),
  }
}

function createUploadTask(): UploadTask {
  return {
    abort: vi.fn(),
    onProgressUpdate: vi.fn(),
  }
}

function createDownloadTask(): DownloadTask {
  return {
    abort: vi.fn(),
    onProgressUpdate: vi.fn(),
  }
}

function mockRequestSuccessOnce(statusCode: number, data: unknown, task = createRequestTask()) {
  mockState.request.mockImplementationOnce((options: UniApp.RequestOptions) => {
    options.success?.({ statusCode, data } as UniApp.RequestSuccessCallbackResult)
    return task as unknown as UniApp.RequestTask
  })
  return task
}

function mockRequestFailOnce(errMsg: string, task = createRequestTask()) {
  mockState.request.mockImplementationOnce((options: UniApp.RequestOptions) => {
    options.fail?.({ errMsg } as UniApp.GeneralCallbackResult)
    return task as unknown as UniApp.RequestTask
  })
  return task
}

function mockUploadSuccessOnce(statusCode: number, data: string, task = createUploadTask()) {
  mockState.uploadFile.mockImplementationOnce((options: UniApp.UploadFileOption) => {
    options.success?.({ statusCode, data } as UniApp.UploadFileSuccessCallbackResult)
    return task as unknown as UniApp.UploadTask
  })
  return task
}

function mockUploadFailOnce(errMsg: string, task = createUploadTask()) {
  mockState.uploadFile.mockImplementationOnce((options: UniApp.UploadFileOption) => {
    options.fail?.({ errMsg } as UniApp.GeneralCallbackResult)
    return task as unknown as UniApp.UploadTask
  })
  return task
}

function mockDownloadSuccessOnce(
  statusCode: number,
  tempFilePath = '/tmp/file.pdf',
  task = createDownloadTask(),
) {
  mockState.downloadFile.mockImplementationOnce((options: UniApp.DownloadFileOption) => {
    options.success?.({ statusCode, tempFilePath } as UniApp.DownloadSuccessData)
    return task as unknown as UniApp.DownloadTask
  })
  return task
}

function mockDownloadFailOnce(errMsg: string, task = createDownloadTask()) {
  mockState.downloadFile.mockImplementationOnce((options: UniApp.DownloadFileOption) => {
    options.fail?.({ errMsg } as UniApp.GeneralCallbackResult)
    return task as unknown as UniApp.DownloadTask
  })
  return task
}

describe('src/utils/request.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()

    mockState.baseUrl = ''
    mockState.userStore.userInfo = {
      token: 'token-123',
      tenantId: 42,
    }
    mockState.userStore.clearUserInfo = vi.fn(() => {
      mockState.userStore.userInfo = {
        token: '',
        tenantId: 0,
      }
    })

    // @ts-ignore - globalThis.uni is a UniApp global
    globalThis.uni = {
      request: mockState.request,
      uploadFile: mockState.uploadFile,
      downloadFile: mockState.downloadFile,
      showToast: mockState.showToast,
      navigateTo: mockState.navigateTo,
      getStorageSync: mockState.getStorageSync,
      setStorageSync: mockState.setStorageSync,
    } as unknown as typeof uni

    Object.assign(import.meta.env, {
      MODE: 'development',
      VITE_APP_PROXY: 'true',
      VITE_APP_PROXY_PREFIX: 'https://api.example.com',
    })

    clearRequestCache()
  })

  afterEach(() => {
    clearRequestCache()
    vi.useRealTimers()
  })

  it('默认 request 封装会映射 params/headers，并注入认证头、租户头和默认超时', async () => {
    mockRequestSuccessOnce(200, { ok: true })

    const result = await request<{ ok: boolean }>('/notes', {
      headers: { Authorization: 'Bearer demo' },
      params: { keyword: 'ai', page: 1 },
    })

    expect(result).toEqual({ ok: true })
    expect(mockState.request).toHaveBeenCalledTimes(1)

    const options = mockState.request.mock.calls[0][0] as UniApp.RequestOptions
    expect(options.url).toBe('https://api.example.com/notes?keyword=ai&page=1')
    expect(options.timeout).toBe(10000)
    expect(options.header).toEqual(
      expect.objectContaining({
        Authorization: 'Bearer demo',
        platform: 'h5',
        'X-Access-Token': 'token-123',
        'X-Tenant-Id': '42',
      }),
    )
  })

  it('401 响应会清空用户信息并跳转登录页', async () => {
    mockRequestSuccessOnce(401, { message: 'unauthorized' })

    await expect(http({ url: '/secure/profile' })).rejects.toThrow('未授权，请重新登录')

    expect(mockState.userStore.clearUserInfo).toHaveBeenCalledTimes(1)
    expect(mockState.navigateTo).toHaveBeenCalledWith({ url: '/pages/login/login' })
    expect(mockState.showToast).not.toHaveBeenCalled()
  })

  it('403 响应会提示无权访问', async () => {
    mockRequestSuccessOnce(403, {})

    await expect(http({ url: '/forbidden' })).rejects.toThrow('无权访问')

    expect(mockState.showToast).toHaveBeenCalledWith({ icon: 'none', title: '无权访问' })
  })

  it('404 响应在 hideErrorToast=true 时不会弹出提示', async () => {
    mockRequestSuccessOnce(404, {})

    await expect(http({ hideErrorToast: true, url: '/missing' })).rejects.toThrow('接口不存在')

    expect(mockState.showToast).not.toHaveBeenCalled()
  })

  it('非 401/403/404/5xx 错误会透传后端 message', async () => {
    mockRequestSuccessOnce(400, { message: '参数错误' })

    await expect(http({ url: '/bad-request' })).rejects.toThrow('参数错误')

    expect(mockState.showToast).toHaveBeenCalledWith({ icon: 'none', title: '参数错误' })
  })

  it('5xx 错误会按指数退避重试并在成功后返回结果', async () => {
    vi.useFakeTimers()
    mockRequestSuccessOnce(500, {})
    mockRequestSuccessOnce(500, {})
    mockRequestSuccessOnce(200, { retried: true })

    const promise = http<{ retried: boolean }>({ url: '/retry-5xx' })

    expect(mockState.request).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1000)
    expect(mockState.request).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(2000)
    await expect(promise).resolves.toEqual({ retried: true })
    expect(mockState.request).toHaveBeenCalledTimes(3)
    expect(mockState.showToast).not.toHaveBeenCalled()
  })

  it('超时会最多重试 3 次，最终失败时返回请求超时', async () => {
    vi.useFakeTimers()
    mockRequestFailOnce('request:fail timeout')
    mockRequestFailOnce('request:fail timeout')
    mockRequestFailOnce('request:fail timeout')
    mockRequestFailOnce('request:fail timeout')

    const promise = http({ url: '/timeout' })
    const rejection = expect(promise).rejects.toThrow('请求超时')

    expect(mockState.request).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(2000)
    await vi.advanceTimersByTimeAsync(4000)

    await rejection
    expect(mockState.request).toHaveBeenCalledTimes(4)
    expect(mockState.showToast).toHaveBeenCalledWith({ icon: 'none', title: '请求超时' })
  })

  it('非超时网络错误不会重试，并返回统一提示', async () => {
    mockRequestFailOnce('request:fail')

    await expect(http({ url: '/offline' })).rejects.toThrow('网络错误，换个网络试试')

    expect(mockState.request).toHaveBeenCalledTimes(1)
    expect(mockState.showToast).toHaveBeenCalledWith({
      icon: 'none',
      title: '网络错误，换个网络试试',
    })
  })

  it('cancelToken 会绑定到底层 requestTask.abort', () => {
    const task = createRequestTask()
    mockState.request.mockImplementationOnce(() => task as unknown as UniApp.RequestTask)

    const cancelToken: { abort?: () => void } = {}
    void http({ cancelToken, url: '/cancel-me' })

    expect(cancelToken.abort).toBeTypeOf('function')
    cancelToken.abort?.()
    expect(task.abort).toHaveBeenCalledTimes(1)
  })

  it('GET 请求会命中 5 分钟缓存，过期后重新发起请求', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-17T00:00:00.000Z'))

    mockRequestSuccessOnce(200, { value: 'first' })
    await expect(http({ cache: true, url: '/cacheable' })).resolves.toEqual({ value: 'first' })
    expect(mockState.request).toHaveBeenCalledTimes(1)

    await expect(http({ cache: true, url: '/cacheable' })).resolves.toEqual({ value: 'first' })
    expect(mockState.request).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(5 * 60 * 1000 + 1)
    mockRequestSuccessOnce(200, { value: 'second' })

    await expect(http({ cache: true, url: '/cacheable' })).resolves.toEqual({ value: 'second' })
    expect(mockState.request).toHaveBeenCalledTimes(2)
  })

  it('clearRequestCache 会主动清空 GET 缓存', async () => {
    mockRequestSuccessOnce(200, { value: 'cached' })
    await expect(http({ cache: true, url: '/cache-clear' })).resolves.toEqual({ value: 'cached' })

    clearRequestCache()

    mockRequestSuccessOnce(200, { value: 'fresh' })
    await expect(http({ cache: true, url: '/cache-clear' })).resolves.toEqual({ value: 'fresh' })

    expect(mockState.request).toHaveBeenCalledTimes(2)
  })

  it('uploadFile 会注入认证头、拼接 query，并绑定进度与取消回调', async () => {
    const task = mockUploadSuccessOnce(200, JSON.stringify({ uploaded: true }))
    const onProgressUpdate = vi.fn()
    const cancelToken: { abort?: () => void } = {}

    const result = await uploadFile<{ uploaded: boolean }>({
      cancelToken,
      filePath: '/tmp/demo.png',
      name: 'file',
      onProgressUpdate,
      query: { bucket: 'note' },
      url: '/upload',
    })

    expect(result).toEqual({ uploaded: true })

    const options = mockState.uploadFile.mock.calls[0][0] as UniApp.UploadFileOption
    expect(options.url).toBe('https://api.example.com/upload?bucket=note')
    expect(options.header).toEqual(
      expect.objectContaining({
        platform: 'h5',
        'X-Access-Token': 'token-123',
        'X-Tenant-Id': '42',
      }),
    )
    expect(task.onProgressUpdate).toHaveBeenCalledWith(onProgressUpdate)

    expect(cancelToken.abort).toBeTypeOf('function')
    cancelToken.abort?.()
    expect(task.abort).toHaveBeenCalledTimes(1)
  })

  it('uploadFile 在 401 时会执行统一未授权处理', async () => {
    mockUploadSuccessOnce(401, 'unauthorized')

    await expect(
      uploadFile({
        filePath: '/tmp/demo.png',
        name: 'file',
        url: '/upload',
      }),
    ).rejects.toThrow('未授权，请重新登录')

    expect(mockState.userStore.clearUserInfo).toHaveBeenCalledTimes(1)
    expect(mockState.navigateTo).toHaveBeenCalledWith({ url: '/pages/login/login' })
  })

  it('uploadFile 网络失败时会提示用户', async () => {
    mockUploadFailOnce('upload failed')

    await expect(
      uploadFile({
        filePath: '/tmp/demo.png',
        name: 'file',
        url: '/upload',
      }),
    ).rejects.toThrow('上传失败，请检查网络')

    expect(mockState.showToast).toHaveBeenCalledWith({
      icon: 'none',
      title: '上传失败，请检查网络',
    })
  })

  it('downloadFile 会注入认证头，并绑定进度与取消回调', async () => {
    const task = mockDownloadSuccessOnce(200)
    const onProgressUpdate = vi.fn()
    const cancelToken: { abort?: () => void } = {}

    const result = await downloadFile({
      cancelToken,
      onProgressUpdate,
      query: { noteId: 'n-1' },
      url: '/download',
    })

    expect(result).toEqual({ statusCode: 200, tempFilePath: '/tmp/file.pdf' })

    const options = mockState.downloadFile.mock.calls[0][0] as UniApp.DownloadFileOption
    expect(options.url).toBe('https://api.example.com/download?noteId=n-1')
    expect(options.header).toEqual(
      expect.objectContaining({
        platform: 'h5',
        'X-Access-Token': 'token-123',
        'X-Tenant-Id': '42',
      }),
    )
    expect(task.onProgressUpdate).toHaveBeenCalledWith(onProgressUpdate)

    expect(cancelToken.abort).toBeTypeOf('function')
    cancelToken.abort?.()
    expect(task.abort).toHaveBeenCalledTimes(1)
  })

  it('downloadFile 网络失败时会返回统一错误提示', async () => {
    mockDownloadFailOnce('download failed')

    await expect(downloadFile({ url: '/download' })).rejects.toThrow('下载失败，请检查网络')

    expect(mockState.showToast).toHaveBeenCalledWith({
      icon: 'none',
      title: '下载失败，请检查网络',
    })
  })
})
