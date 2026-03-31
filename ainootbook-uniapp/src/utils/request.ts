import qs from 'qs'
import { useUserStore } from '@/store'
import { getEnvBaseUrl } from '@/utils'
import { platform } from '@/utils/platform'

export type CustomRequestOptions = UniApp.RequestOptions & {
  query?: Record<string, unknown>
  hideErrorToast?: boolean
  retryCount?: number
  retryDelay?: number
  cancelToken?: { abort?: () => void }
  cache?: boolean
}

// 5.10 GET 请求缓存（5 分钟）
const cacheMap = new Map<string, { data: unknown; expiry: number }>()
const CACHE_TTL = 5 * 60 * 1000

// 清空缓存（用于登出/401/租户切换）
export function clearRequestCache() {
  cacheMap.clear()
}

function handleUrlAndQuery(options: CustomRequestOptions) {
  const baseUrl = getEnvBaseUrl()

  if (options.query) {
    const queryStr = qs.stringify(options.query)
    if (queryStr) {
      if (options.url.includes('?')) {
        options.url += `&${queryStr}`
      } else {
        options.url += `?${queryStr}`
      }
    }
    // 删除 query 字段，避免与全局拦截器冲突
    delete options.query
  }

  if (!options.url.startsWith('http')) {
    // #ifdef H5
    const proxy = import.meta.env.VITE_APP_PROXY
    if (proxy && JSON.parse(proxy) && import.meta.env.MODE === 'development') {
      options.url = (import.meta.env.VITE_APP_PROXY_PREFIX || '') + options.url
    } else {
      options.url = baseUrl + options.url
    }
    // #endif
    // #ifndef H5
    options.url = baseUrl + options.url
    // #endif
  }
}

// 5.5 实现请求拦截器
function applyRequestInterceptors(options: CustomRequestOptions) {
  handleUrlAndQuery(options)

  // 5.8 默认超时 10s
  options.timeout = options.timeout || 10000

  options.header = {
    platform,
    ...options.header,
  }

  const userStore = useUserStore()
  const userInfo = userStore.userInfo as IUserInfo | undefined
  if (userInfo?.token) {
    options.header['X-Access-Token'] = userInfo.token
  }
  if (userInfo?.tenantId) {
    options.header['X-Tenant-Id'] = String(userInfo.tenantId)
  }

  return options
}

/**
 * 5.4 统一请求封装
 * @param options 请求参数
 * @returns 返回 Promise 对象
 */
export const http = <T = unknown>(options: CustomRequestOptions): Promise<T> => {
  const reqOpts = applyRequestInterceptors({ ...options })

  // 缓存 key 绑定用户上下文
  const userStore = useUserStore()
  const userInfo = userStore.userInfo as IUserInfo | undefined
  const userContext = userInfo?.token ? `${userInfo.token}_${userInfo.tenantId || ''}` : 'anonymous'
  const cacheKey = `${userContext}_${reqOpts.method || 'GET'}_${reqOpts.url}_${JSON.stringify(reqOpts.data || {})}`

  const isGet = !reqOpts.method || reqOpts.method.toUpperCase() === 'GET'
  if (isGet && reqOpts.cache) {
    const cached = cacheMap.get(cacheKey)
    if (cached && Date.now() < cached.expiry) {
      return Promise.resolve(cached.data as T)
    }
  }

  const maxRetries = reqOpts.retryCount ?? 3
  const initialDelay = reqOpts.retryDelay ?? 1000

  const attempt = (retryIndex: number): Promise<T> => {
    return new Promise<T>((resolve, reject) => {
      // 5.9 请求取消机制
      const task = uni.request({
        ...reqOpts,
        dataType: 'json',
        // #ifndef MP-WEIXIN
        // 移除无效的 responseType，dataType: 'json' 已足够
        // #endif
        success(res) {
          // 5.6 响应拦截器
          if (res.statusCode >= 200 && res.statusCode < 300) {
            if (isGet && reqOpts.cache) {
              cacheMap.set(cacheKey, { data: res.data, expiry: Date.now() + CACHE_TTL })
            }
            resolve(res.data as T)
          } else if (res.statusCode === 401) {
            clearRequestCache()
            const userStore = useUserStore()
            userStore.clearUserInfo()
            uni.navigateTo({ url: '/pages/login/login' })
            reject(new Error('未授权，请重新登录'))
          } else if (res.statusCode === 403) {
            if (!reqOpts.hideErrorToast) {
              uni.showToast({ icon: 'none', title: '无权访问' })
            }
            reject(new Error('无权访问'))
          } else if (res.statusCode === 404) {
            if (!reqOpts.hideErrorToast) {
              uni.showToast({ icon: 'none', title: '接口不存在' })
            }
            reject(new Error('接口不存在'))
          } else if (res.statusCode >= 500) {
            // 5.7 5xx 自动重试
            if (retryIndex < maxRetries) {
              setTimeout(() => {
                resolve(attempt(retryIndex + 1))
              }, initialDelay * Math.pow(2, retryIndex))
            } else {
              if (!reqOpts.hideErrorToast) {
                uni.showToast({ icon: 'none', title: '服务器错误' })
              }
              reject(new Error('服务器错误'))
            }
          } else {
            // 统一错误处理：始终返回 Error 对象
            const errorMsg = (res.data as IResData<unknown>)?.message || '请求错误'
            if (!reqOpts.hideErrorToast) {
              uni.showToast({ icon: 'none', title: errorMsg })
            }
            reject(new Error(errorMsg))
          }
        },
        fail(err) {
          // 5.7 超时自动重试
          const isTimeout = err.errMsg && err.errMsg.includes('timeout')
          if (isTimeout && retryIndex < maxRetries) {
            setTimeout(() => {
              resolve(attempt(retryIndex + 1))
            }, initialDelay * Math.pow(2, retryIndex))
            return
          }
          const errorMsg = isTimeout ? '请求超时' : '网络错误，换个网络试试'
          if (!reqOpts.hideErrorToast) {
            uni.showToast({ icon: 'none', title: errorMsg })
          }
          reject(new Error(errorMsg))
        },
      })

      if (reqOpts.cancelToken) {
        reqOpts.cancelToken.abort = () => {
          task.abort()
        }
      }
    })
  }

  return attempt(0)
}

export interface CustomUploadOptions extends Omit<UniApp.UploadFileOption, 'url'> {
  url: string
  query?: Record<string, unknown>
  hideErrorToast?: boolean
  onProgressUpdate?: (res: UniApp.OnProgressUpdateResult) => void
  cancelToken?: { abort?: () => void }
}

/**
 * 5.11 文件上传封装
 * @param options 上传参数
 * @returns 返回 Promise 对象
 */
export const uploadFile = <T = unknown>(options: CustomUploadOptions): Promise<T> => {
  const reqOpts = { ...options }
  handleUrlAndQuery(reqOpts as CustomRequestOptions)

  // 添加认证头
  const userStore = useUserStore()
  const userInfo = userStore.userInfo as IUserInfo | undefined
  reqOpts.header = {
    platform,
    ...reqOpts.header,
  }
  if (userInfo?.token) {
    reqOpts.header['X-Access-Token'] = userInfo.token
  }
  if (userInfo?.tenantId) {
    reqOpts.header['X-Tenant-Id'] = String(userInfo.tenantId)
  }

  return new Promise((resolve, reject) => {
    const task = uni.uploadFile({
      ...reqOpts,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          let data: unknown = res.data
          try {
            data = JSON.parse(res.data)
          } catch (e) {
            // 解析失败，使用原始数据
          }
          resolve(data as T)
        } else if (res.statusCode === 401) {
          clearRequestCache()
          const userStore = useUserStore()
          userStore.clearUserInfo()
          uni.navigateTo({ url: '/pages/login/login' })
          reject(new Error('未授权，请重新登录'))
        } else {
          const errorMsg = '上传失败'
          if (!reqOpts.hideErrorToast) {
            uni.showToast({ icon: 'none', title: errorMsg })
          }
          reject(new Error(errorMsg))
        }
      },
      fail: (err) => {
        const errorMsg = '上传失败，请检查网络'
        if (!reqOpts.hideErrorToast) {
          uni.showToast({ icon: 'none', title: errorMsg })
        }
        reject(new Error(errorMsg))
      },
    })

    if (options.onProgressUpdate) {
      task.onProgressUpdate(options.onProgressUpdate)
    }

    if (options.cancelToken) {
      options.cancelToken.abort = () => task.abort()
    }
  })
}

export interface CustomDownloadOptions extends Omit<UniApp.DownloadFileOption, 'url'> {
  url: string
  query?: Record<string, unknown>
  hideErrorToast?: boolean
  onProgressUpdate?: (res: UniApp.OnProgressDownloadResult) => void
  cancelToken?: { abort?: () => void }
}

/**
 * 5.12 文件下载封装
 * @param options 下载参数
 * @returns 返回 Promise 对象
 */
export const downloadFile = (options: CustomDownloadOptions): Promise<UniApp.DownloadSuccessData> => {
  const reqOpts = { ...options }
  handleUrlAndQuery(reqOpts as unknown as CustomRequestOptions)

  // 添加认证头
  const userStore = useUserStore()
  const userInfo = userStore.userInfo as IUserInfo | undefined
  reqOpts.header = {
    platform,
    ...reqOpts.header,
  }
  if (userInfo?.token) {
    reqOpts.header['X-Access-Token'] = userInfo.token
  }
  if (userInfo?.tenantId) {
    reqOpts.header['X-Tenant-Id'] = String(userInfo.tenantId)
  }

  return new Promise((resolve, reject) => {
    const task = uni.downloadFile({
      ...reqOpts,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res)
        } else if (res.statusCode === 401) {
          clearRequestCache()
          const userStore = useUserStore()
          userStore.clearUserInfo()
          uni.navigateTo({ url: '/pages/login/login' })
          reject(new Error('未授权，请重新登录'))
        } else {
          const errorMsg = '下载失败'
          if (!reqOpts.hideErrorToast) {
            uni.showToast({ icon: 'none', title: errorMsg })
          }
          reject(new Error(errorMsg))
        }
      },
      fail: (err) => {
        const errorMsg = '下载失败，请检查网络'
        if (!reqOpts.hideErrorToast) {
          uni.showToast({ icon: 'none', title: errorMsg })
        }
        reject(new Error(errorMsg))
      },
    })

    if (options.onProgressUpdate) {
      task.onProgressUpdate(options.onProgressUpdate)
    }

    if (options.cancelToken) {
      options.cancelToken.abort = () => task.abort()
    }
  })
}

/*
 * openapi-ts-request 工具的 request 跨客户端适配方法
 */
export default function request<T = unknown>(
  url: string,
  options: Omit<CustomRequestOptions, 'url'> & {
    params?: Record<string, unknown>
    headers?: Record<string, unknown>
  },
) {
  const requestOptions: CustomRequestOptions = {
    url,
    ...options,
  }

  if (options.params) {
    requestOptions.query = options.params
    delete (requestOptions as any).params
  }

  if (options.headers) {
    requestOptions.header = options.headers
    delete (requestOptions as any).headers
  }

  return http<T>(requestOptions)
}
