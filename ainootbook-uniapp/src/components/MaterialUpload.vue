<template>
  <view class="material-upload">
    <wd-toast id="toast" />

    <!-- 上传按钮组 -->
    <view class="upload-actions">
      <wd-button
        v-if="accept.includes('image')"
        size="small" type="primary" plain
        @click="handleChooseImage">上传图片</wd-button>
      <wd-button
        v-if="accept.includes('document')"
        size="small" type="primary" plain
        @click="handleChooseDocument">上传文档</wd-button>
      <wd-button
        v-if="accept.includes('audio')"
        size="small" type="primary" plain
        @click="handleChooseAudio">上传音频</wd-button>
      <wd-button
        v-if="accept.includes('video')"
        size="small" type="primary" plain
        @click="handleChooseVideo">上传视频</wd-button>
    </view>

    <!-- 正在上传及已上传列表 -->
    <view class="upload-list" v-if="uploadTasks.length > 0">
      <view v-for="task in uploadTasks" :key="task.id" class="upload-item">
        <view class="item-info">
          <view class="item-name-wrap">
            <text class="item-name">{{ task.name }}</text>
            <text class="item-status" :class="task.status">
              {{ getStatusText(task.status) }}
            </text>
          </view>
          <text class="item-size">{{ formatSize(task.size) }}</text>
        </view>

        <wd-progress
          v-if="task.status === 'uploading'"
          :percentage="task.progress"
          :status="task.progress === 100 ? 'success' : undefined" />

        <text v-if="task.status === 'uploading' && task.type === 'video'" class="chunk-info">
          已上传 {{ task.uploadedChunks || 0 }} / {{ task.totalChunks || 0 }} 分片
        </text>

        <view class="item-actions">
          <wd-button
            v-if="task.status === 'uploading'"
            size="small" type="warning" plain
            @click="cancelUpload(task.id)">取消</wd-button>
          <wd-button
            v-if="['success', 'error'].includes(task.status)"
            size="small" type="error" plain
            @click="handleDelete(task)">删除</wd-button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, defineProps, defineEmits } from 'vue'
import { useToast } from 'wot-design-uni'
import md5 from 'md5'
import { materialApi, type Material } from '@/api/material'
import { chooseFile, chooseImage, chooseVideo, FILE_SIZE_LIMITS } from '@/utils/filePicker'

const props = defineProps({
  accept: {
    type: Array as () => string[],
    default: () => ['image', 'document', 'audio', 'video']
  },
  maxSize: {
    type: Number,
    default: 500 * 1024 * 1024
  },
  multiple: {
    type: Boolean,
    default: false
  },
  noteId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits<{
  (e: 'upload-success', material: Material): void
  (e: 'upload-error', error: any): void
  (e: 'delete', id: string): void
}>()

const toast = useToast()

interface UploadTask {
  id: string
  name: string
  size: number
  type: string
  status: 'uploading' | 'success' | 'error' | 'canceled'
  progress: number
  totalChunks?: number
  uploadedChunks?: number
  materialId?: string
  tempFilePath: string
  fileObj?: File
  uploadId?: string
  uploadTask?: UniApp.UploadTask
}

const uploadTasks = ref<UploadTask[]>([])

const formatSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

const getStatusText = (status: UploadTask['status']) => {
  const map: Record<string, string> = {
    uploading: '上传中...',
    success: '已完成',
    error: '上传失败',
    canceled: '已取消'
  }
  return map[status] || status
}

const validateSize = (size: number, max: number) => {
  if (size > max) {
    toast.error(`文件大小超出限制，最大允许 ${formatSize(max)}`)
    return false
  }
  return true
}

// 选择图片
const handleChooseImage = async () => {
  try {
    const files = await chooseImage({ count: props.multiple ? 9 : 1 })
    files.forEach((file: any) => {
      if (validateSize(file.size, FILE_SIZE_LIMITS.IMAGE)) {
        startUpload(file.path, file.name || `image_${Date.now()}.png`, file.size, 'image', file)
      }
    })
  } catch (error: any) {
    console.error('选择图片失败:', error)
  }
}

// 选择文档
const handleChooseDocument = async () => {
  try {
    const files = await chooseFile({
      count: props.multiple ? 9 : 1,
      type: 'file',
      extension: ['.pdf', '.docx', '.txt']
    })
    files.forEach((file: any) => {
      if (validateSize(file.size, FILE_SIZE_LIMITS.DOCUMENT)) {
        startUpload(file.path, file.name || `document_${Date.now()}`, file.size, 'document', file)
      }
    })
  } catch (error: any) {
    console.error('选择文档失败:', error)
  }
}

// 选择音频
const handleChooseAudio = async () => {
  try {
    const files = await chooseFile({
      count: props.multiple ? 9 : 1,
      type: 'file'
    })
    files.forEach((file: any) => {
      if (validateSize(file.size, FILE_SIZE_LIMITS.AUDIO)) {
        startUpload(file.path, file.name || `audio_${Date.now()}`, file.size, 'audio', file)
      }
    })
  } catch (error: any) {
    console.error('选择音频失败:', error)
  }
}

// 选择视频
const handleChooseVideo = async () => {
  try {
    const files = await chooseVideo({
      sourceType: ['album', 'camera']
    })
    if (files && files.length > 0) {
      const file = files[0] as any
      const name = file.name || `video_${Date.now()}.mp4`
      if (validateSize(file.size, FILE_SIZE_LIMITS.VIDEO)) {
        startChunkedUpload(file.path, name, file.size, file)
      }
    }
  } catch (error: any) {
    console.error('选择视频失败:', error)
  }
}

// 普通上传
const startUpload = async (filePath: string, fileName: string, size: number, type: string, fileObj?: any) => {
  const task: UploadTask = {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 5),
    name: fileName,
    size,
    type,
    status: 'uploading',
    progress: 0,
    tempFilePath: filePath,
    fileObj
  }
  uploadTasks.value.push(task)

  try {
    const res = await materialApi.uploadMaterial(filePath, props.noteId, (progress) => {
      if (task.status === 'uploading') task.progress = progress
    })

    if (res.success) {
      task.status = 'success'
      task.progress = 100
      task.materialId = res.result.id
      emit('upload-success', res.result)
      toast.success('上传成功')
    } else {
      throw new Error(res.message)
    }
  } catch (err: any) {
    if (task.status !== 'canceled') {
      task.status = 'error'
      emit('upload-error', err)
      toast.error(err.message || '上传失败')
    }
  }
}

// 视频分片上传
const startChunkedUpload = async (filePath: string, fileName: string, size: number, fileObj?: File) => {
  const task: UploadTask = {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 5),
    name: fileName,
    size,
    type: 'video',
    status: 'uploading',
    progress: 0,
    tempFilePath: filePath,
    fileObj
  }
  uploadTasks.value.push(task)

  try {
    // 计算文件 MD5（分块计算避免 OOM）
    const fileMd5 = await calculateFileMd5(filePath, size, fileObj)

    const initRes = await materialApi.initChunkedUpload({
      fileName,
      fileSize: size,
      fileMd5,
      noteId: props.noteId || undefined
    })

    if (!initRes.success) throw new Error('初始化上传失败')

    const { uploadId, chunkSize, totalChunks } = initRes.result
    task.uploadId = uploadId
    task.totalChunks = totalChunks
    task.uploadedChunks = 0

    // 断点续传查询
    const progressRes = await materialApi.queryUploadProgress(uploadId)
    const uploadedIndexes = progressRes.success ? progressRes.result.uploadedChunks : []
    task.uploadedChunks = uploadedIndexes.length
    task.progress = Math.floor((task.uploadedChunks / totalChunks) * 100)

    for (let i = 0; i < totalChunks; i++) {
      if (task.status !== 'uploading') return
      if (uploadedIndexes.includes(i)) continue

      const chunkBase64 = await getFileChunk(filePath, i, chunkSize, fileObj)
      const chunkMd5 = md5(chunkBase64)

      await materialApi.uploadChunk({
        uploadId,
        chunkIndex: i,
        chunkMd5,
        file: chunkBase64
      })

      task.uploadedChunks++
      task.progress = Math.floor((task.uploadedChunks / totalChunks) * 100)
    }

    if (task.status !== 'uploading') return

    const finalizeRes = await materialApi.finalizeUpload({ uploadId, fileMd5 })
    if (finalizeRes.success) {
      task.status = 'success'
      task.materialId = finalizeRes.result.id
      emit('upload-success', finalizeRes.result)
      toast.success('上传成功')
    } else {
      throw new Error(finalizeRes.message)
    }
  } catch (err: any) {
    if (task.status === 'uploading') {
      task.status = 'error'
      emit('upload-error', err)
      toast.error(err.message || '分片上传失败')
    }
  }
}

// 计算文件 MD5（分块计算避免 OOM）
const calculateFileMd5 = async (tempFilePath: string, fileSize: number, fileObj?: File): Promise<string> => {
  const chunkSize = 2 * 1024 * 1024 // 2MB per chunk
  const chunks = Math.ceil(fileSize / chunkSize)
  let md5Hash = ''

  // #ifdef H5
  if (fileObj && fileObj.slice) {
    const SparkMD5 = (await import('spark-md5')).default
    const spark = new SparkMD5.ArrayBuffer()

    for (let i = 0; i < chunks; i++) {
      const chunk = fileObj.slice(i * chunkSize, (i + 1) * chunkSize)
      const arrayBuffer = await chunk.arrayBuffer()
      spark.append(arrayBuffer)
    }

    md5Hash = spark.end()
  }
  // #endif

  // #ifndef H5
  // 小程序/APP 平台：简化为文件名+大小的 MD5（避免大文件读取 OOM）
  md5Hash = md5(`${tempFilePath}_${fileSize}`)
  // #endif

  return md5Hash
}

// 获取文件分片
const getFileChunk = async (tempFilePath: string, chunkIndex: number, chunkSize: number, fileObj?: File): Promise<string> => {
  // #ifdef H5
  if (fileObj && fileObj.slice) {
    const chunk = fileObj.slice(chunkIndex * chunkSize, (chunkIndex + 1) * chunkSize)
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const result = reader.result as string
        const base64 = result.includes(',') ? result.split(',')[1] : result
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(chunk)
    })
  }
  // #endif

  // #ifndef H5
  return await new Promise<string>((resolve, reject) => {
    uni.getFileSystemManager().readFile({
      filePath: tempFilePath,
      position: chunkIndex * chunkSize,
      length: chunkSize,
      encoding: 'base64',
      success: (res) => resolve(res.data as string),
      fail: (err) => reject(err)
    })
  })
  // #endif

  return ''
}

const cancelUpload = (taskId: string) => {
  const task = uploadTasks.value.find(t => t.id === taskId)
  if (task && task.status === 'uploading') {
    task.status = 'canceled'
    if (task.uploadTask) {
      task.uploadTask.abort()
    }
    toast.warning('已取消上传')
  }
}

const handleDelete = async (task: UploadTask) => {
  if (task.materialId) {
    try {
      const res = await materialApi.deleteMaterial(task.materialId)
      if (res.success) {
        emit('delete', task.materialId)
        uploadTasks.value = uploadTasks.value.filter(t => t.id !== task.id)
        toast.success('删除成功')
      } else {
        toast.error(res.message || '删除失败')
      }
    } catch (error) {
      toast.error('删除失败')
    }
  } else {
    uploadTasks.value = uploadTasks.value.filter(t => t.id !== task.id)
  }
}
</script>

<style scoped lang="scss">
.material-upload {
  padding: 16px;

  .upload-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
  }

  .upload-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .upload-item {
    background-color: #f8f8f8;
    border-radius: 8px;
    padding: 12px;

    .item-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .item-name-wrap {
        display: flex;
        align-items: center;
        flex: 1;
        overflow: hidden;

        .item-name {
          font-size: 14px;
          color: #333;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          margin-right: 8px;
        }

        .item-status {
          font-size: 12px;
          padding: 2px 6px;
          border-radius: 4px;
          white-space: nowrap;

          &.uploading { color: #1989fa; background: #e8f3fe; }
          &.success { color: #07c160; background: #e6f9f0; }
          &.error { color: #ee0a24; background: #fdebee; }
          &.canceled { color: #969799; background: #f2f3f5; }
        }
      }

      .item-size {
        font-size: 12px;
        color: #999;
        margin-left: 12px;
      }
    }

    .chunk-info {
      font-size: 12px;
      color: #666;
      margin-top: 6px;
      display: block;
    }

    .item-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 12px;
    }
  }
}
</style>
