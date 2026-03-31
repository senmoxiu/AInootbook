import { http, uploadFile } from '@/utils/request'

export interface Material {
  id: string
  noteId?: string
  fileName: string
  fileType: string
  fileSize: number
  filePath: string
  fileUrl?: string
  createTime?: string
}

export interface MaterialListParams {
  pageNo?: number
  pageSize?: number
  noteId?: string
  fileType?: string
  [key: string]: unknown
}

export interface MaterialListResult {
  records: Material[]
  total: number
}

export interface ChunkedUploadInitResult {
  uploadId: string
  chunkSize: number
  totalChunks: number
}

export interface ChunkedUploadProgressResult {
  uploadedChunks: number[]
  totalChunks: number
}

const BASE_URL = '/ainote/material'

export const materialApi = {
  getMaterialList: (params?: MaterialListParams) =>
    http<MaterialListResult>({
      url: `${BASE_URL}/list`,
      method: 'GET',
      query: params,
      cache: true
    }),

  getMaterialDetail: (id: string) =>
    http<Material>({
      url: `${BASE_URL}/queryById`,
      method: 'GET',
      query: { id },
      cache: true
    }),

  deleteMaterial: (id: string) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/delete`,
      method: 'DELETE',
      query: { id }
    }),

  // 普通文件上传（图片、文档、音频）
  uploadMaterial: (filePath: string, noteId?: string, onProgress?: (progress: number) => void) =>
    uploadFile<{ success: boolean; message: string; result: Material }>({
      url: `${BASE_URL}/upload`,
      filePath,
      name: 'file',
      formData: noteId ? { noteId } : undefined,
      onProgressUpdate: onProgress ? (res) => onProgress(res.progress) : undefined
    }),

  // 视频分片上传 - 初始化
  initChunkedUpload: (data: { fileName: string; fileSize: number; fileMd5: string; noteId?: string }) =>
    http<{ success: boolean; result: ChunkedUploadInitResult }>({
      url: `${BASE_URL}/initChunkedUpload`,
      method: 'POST',
      data
    }),

  // 视频分片上传 - 上传单片
  uploadChunk: (data: {
    uploadId: string
    chunkIndex: number
    chunkMd5: string
    file: string // base64 或文件路径
  }) =>
    http<{ success: boolean; message: string }>({
      url: `${BASE_URL}/uploadChunk`,
      method: 'POST',
      data
    }),

  // 视频分片上传 - 查询进度
  queryUploadProgress: (uploadId: string) =>
    http<{ success: boolean; result: ChunkedUploadProgressResult }>({
      url: `${BASE_URL}/queryUploadProgress`,
      method: 'GET',
      query: { uploadId }
    }),

  // 视频分片上传 - 合并分片
  finalizeUpload: (data: { uploadId: string; fileMd5: string }) =>
    http<{ success: boolean; message: string; result: Material }>({
      url: `${BASE_URL}/finalizeUpload`,
      method: 'POST',
      data
    }),
}
