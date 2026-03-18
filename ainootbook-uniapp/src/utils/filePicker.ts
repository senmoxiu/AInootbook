/**
 * 跨平台文件选择适配器
 * 处理 H5 和微信小程序的 API 差异
 */

export const FILE_SIZE_LIMITS = {
  IMAGE: 10 * 1024 * 1024,
  AUDIO: 150 * 1024 * 1024,
  DOCUMENT: 50 * 1024 * 1024,
  VIDEO: 500 * 1024 * 1024,
}

export interface FilePickerOptions {
  count?: number
  type?: 'all' | 'video' | 'image' | 'file'
  extension?: string[]
}

export interface PickedFile {
  path: string
  size: number
  name: string
  type?: string
}

export const chooseFile = (options: FilePickerOptions = {}): Promise<PickedFile[]> => {
  return new Promise((resolve, reject) => {
    // #ifdef H5
    uni.chooseFile({
      count: options.count || 1,
      type: options.type || 'all',
      extension: options.extension,
      success: (res: any) => {
        const files = res.tempFiles.map((f: any) => ({
          path: f.path,
          size: f.size,
          name: f.name,
          type: f.type || 'file',
        }))
        resolve(files)
      },
      fail: reject,
    })
    // #endif

    // #ifdef MP-WEIXIN
    wx.chooseMessageFile({
      count: options.count || 1,
      type: options.type || 'all',
      extension: options.extension,
      success: (res: any) => {
        const files = res.tempFiles.map((f: any) => ({
          path: f.path,
          size: f.size,
          name: f.name,
          type: f.type || 'file',
        }))
        resolve(files)
      },
      fail: reject,
    })
    // #endif

    // #ifndef H5 || MP-WEIXIN
    reject(new Error('不支持的平台：当前仅支持 H5 和微信小程序'))
    // #endif
  })
}

export const chooseImage = (options: { count?: number } = {}): Promise<PickedFile[]> => {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: options.count || 1,
      success: (res: any) => {
        const files = res.tempFiles.map((f: any) => ({
          path: f.path,
          size: f.size,
          name: f.name || f.path.substring(f.path.lastIndexOf('/') + 1),
          type: 'image',
        }))
        resolve(files)
      },
      fail: reject,
    })
  })
}

export const chooseVideo = (options: { sourceType?: string[] } = {}): Promise<PickedFile[]> => {
  return new Promise((resolve, reject) => {
    uni.chooseVideo({
      sourceType: options.sourceType || ['album', 'camera'],
      success: (res: any) => {
        const file = {
          path: res.tempFilePath,
          size: res.size,
          name: res.name || res.tempFilePath.substring(res.tempFilePath.lastIndexOf('/') + 1),
          type: 'video',
        }
        resolve([file])
      },
      fail: reject,
    })
  })
}
