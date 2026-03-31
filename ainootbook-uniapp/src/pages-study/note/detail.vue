<template>
  <view class="note-detail-page">
      <scroll-view scroll-y class="detail-scroll">
        <view class="note-header">
          <view class="title-section">
            <text class="title">{{ currentNote?.noteTitle || '加载中...' }}</text>
            <view class="meta">
              <text class="time">{{ currentNote?.updateTime || currentNote?.createTime || '' }}</text>
              <text class="version" v-if="currentNote?.currentVersion">V{{ currentNote.currentVersion }}</text>
            </view>
          </view>
          
          <!-- 历史版本预览提示 -->
          <view v-if="isPreviewMode" class="preview-banner">
            <wd-icon name="info-circle" size="16px" color="#ff9900" />
            <text class="banner-text">正在预览历史版本 V{{ previewVersion?.version }}</text>
            <wd-button size="small" plain type="warning" @click="exitPreview">退出预览</wd-button>
          </view>
        </view>

        <!-- 素材列表 -->
        <view v-if="materials.length > 0" class="material-section">
          <view class="section-title">关联素材 ({{ materials.length }})</view>
          <view class="material-list">
            <view 
              v-for="item in materials" 
              :key="item.id" 
              class="material-item" 
              @click="previewMaterial(item)"
            >
              <image :src="getMaterialIcon(item.fileType)" class="file-icon" />
              <text class="file-name">{{ item.fileName }}</text>
            </view>
          </view>
        </view>

        <!-- 笔记内容 -->
        <view class="content-section">
          <MarkdownViewer
            v-if="mode === 'preview'"
            :htmlContent="displayContent.rendered"
            :rawContent="displayContent.raw"
          />
          <MarkdownEditorLite
            v-else
            v-model="draftNoteContent"
          />
        </view>
        
        <!-- 占位，防止底部遮挡 -->
        <view class="footer-placeholder"></view>
      </scroll-view>

      <!-- 底部操作栏 -->
      <view class="footer-actions">
        <view class="action-item" @click="handleDelete">
          <wd-icon name="delete" size="20px" />
          <text>删除</text>
        </view>
        <view v-if="mode === 'preview'" class="action-item" @click="handleEdit">
          <wd-icon name="edit" size="20px" />
          <text>编辑</text>
        </view>
        <view v-else class="action-item" @click="handleCancelEdit">
          <wd-icon name="close" size="20px" />
          <text>取消</text>
        </view>
        <view class="action-item" @click="showVersionHistory">
          <wd-icon name="history" size="20px" />
          <text>历史</text>
        </view>
        <view v-if="mode === 'preview'" class="action-item primary" @click="openRegeneratePopup">
          <wd-icon name="refresh" size="20px" color="#fff" />
          <text>重新生成</text>
        </view>
        <view v-else class="action-item primary" @click="handleSaveEdit">
          <wd-icon name="check" size="20px" color="#fff" />
          <text>保存</text>
        </view>
      </view>

      <!-- 版本历史弹窗 -->
      <wd-popup v-model="showVersions" position="bottom" custom-style="height: 60vh; border-radius: 16px 16px 0 0;">
        <view class="version-popup">
          <view class="popup-header">
            <text class="popup-title">版本历史</text>
            <wd-icon name="close" size="20px" @click="showVersions = false" />
          </view>
          <scroll-view scroll-y class="version-list-scroll">
            <view 
              v-for="ver in versions" 
              :key="ver.id" 
              class="version-item"
              :class="{ active: currentNote?.currentVersion === ver.version }"
            >
              <view class="version-info">
                <text class="ver-num">版本 V{{ ver.version }}</text>
                <text class="ver-time">{{ ver.createTime }}</text>
              </view>
              <view class="ver-ops">
                <wd-button size="small" plain @click="previewHistoryVersion(ver)">查看</wd-button>
                <wd-button 
                  v-if="currentNote?.currentVersion !== ver.version" 
                  size="small" 
                  type="primary" 
                  @click="handleRollback(ver)"
                >回滚</wd-button>
              </view>
            </view>
            <view v-if="versions.length === 0" class="empty-tip">暂无历史版本</view>
          </scroll-view>
        </view>
      </wd-popup>

      <!-- 重新生成/补充内容弹窗 -->
      <wd-popup v-model="showRegenerate" position="center" custom-style="width: 90%; border-radius: 12px; padding: 20px;">
        <view class="regenerate-popup">
          <text class="popup-title">补充内容并重新生成</text>
          <text class="popup-desc">您可以输入额外的信息，AI 将结合现有素材和补充内容重新生成笔记。</text>
          <wd-textarea
            v-model="additionalContent"
            placeholder="请输入补充内容..."
            :maxlength="500"
            show-word-limit
            auto-height
            custom-style="margin: 15px 0; border: 1px solid #ebedf0; border-radius: 8px; padding: 10px;"
          />
          <view class="popup-btns">
            <wd-button plain @click="showRegenerate = false" style="flex: 1; margin-right: 10px;">取消</wd-button>
            <wd-button type="primary" @click="confirmRegenerate" :loading="regenerateLoading" style="flex: 1;">确定生成</wd-button>
          </view>
        </view>
      </wd-popup>

      <wd-message-box />
      <wd-toast />
    </view>
  </template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useMessage, useToast } from 'wot-design-uni'
import { noteApi, type Note, type NoteVersion } from '@/api/note'
import { materialApi, type Material } from '@/api/material'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import MarkdownEditorLite from '@/components/MarkdownEditorLite.vue'

const message = useMessage()
const toast = useToast()

const noteId = ref('')
const currentNote = ref<Note | null>(null)
const materials = ref<Material[]>([])
const versions = ref<NoteVersion[]>([])
const mode = ref<'preview' | 'edit'>('preview')
const draftNoteContent = ref('')
const saving = ref(false)

// 预览历史版本相关
const previewVersion = ref<NoteVersion | null>(null)
const isPreviewMode = computed(() => !!previewVersion.value)

const displayContent = computed(() => {
  if (isPreviewMode.value && previewVersion.value) {
    return {
      rendered: previewVersion.value.renderedContent || '',
      raw: previewVersion.value.noteContent || ''
    }
  }
  return {
    rendered: currentNote.value?.renderedContent || '',
    raw: currentNote.value?.noteContent || ''
  }
})

// 版本历史相关
const showVersions = ref(false)

// 重新生成相关
const showRegenerate = ref(false)
const additionalContent = ref('')
const regenerateLoading = ref(false)

onLoad((options) => {
  if (options && options.id) {
    noteId.value = options.id
    loadData()
  } else {
    toast.error('无效的笔记 ID')
    setTimeout(() => uni.navigateBack(), 1500)
  }
})

const loadData = async () => {
  try {
    const [note, materialRes] = await Promise.all([
      noteApi.getNoteDetail(noteId.value),
      materialApi.getMaterialList({ noteId: noteId.value, pageSize: 100 })
    ])
    currentNote.value = note
    materials.value = materialRes.records || []
  } catch (error) {
    toast.error('加载失败')
    console.error(error)
  }
}

// 删除笔记
const handleDelete = () => {
  message.confirm({
    title: '确认删除',
    msg: '删除后将无法找回，是否确定删除该笔记？',
    confirmButtonText: '删除',
  }).then(async () => {
    try {
      const res = await noteApi.deleteNote(noteId.value)
      if (res.success) {
        toast.success('删除成功')
        setTimeout(() => uni.navigateBack(), 1500)
      } else {
        toast.error(res.message || '删除失败')
      }
    } catch (error) {
      toast.error('删除操作失败')
    }
  }).catch(() => {})
}

// 版本历史
const showVersionHistory = async () => {
  try {
    const res = await noteApi.getNoteVersions({ noteId: noteId.value, pageNo: 1, pageSize: 20 })
    if (res.success) {
      versions.value = res.result?.records || []
      showVersions.value = true
    }
  } catch (error) {
    toast.error('获取版本历史失败')
  }
}

const previewHistoryVersion = (ver: NoteVersion) => {
  previewVersion.value = ver
  showVersions.value = false
}

const exitPreview = () => {
  previewVersion.value = null
}

const handleRollback = (ver: NoteVersion) => {
  message.confirm({
    title: '确认回滚',
    msg: `是否确认将笔记回滚到版本 V${ver.version}？`,
  }).then(async () => {
    try {
      const res = await noteApi.rollbackNote({
        noteId: noteId.value,
        targetVersion: ver.version
      })
      if (res.success) {
        toast.success('回滚成功')
        showVersions.value = false
        previewVersion.value = null
        loadData()
      } else {
        toast.error(res.message || '回滚失败')
      }
    } catch (error) {
      toast.error('回滚操作失败')
    }
  }).catch(() => {})
}

// 重新生成
const openRegeneratePopup = () => {
  additionalContent.value = ''
  showRegenerate.value = true
}

const confirmRegenerate = async () => {
  if (regenerateLoading.value) return
  regenerateLoading.value = true
  uni.showLoading({ title: '重新生成中...', mask: true })
  try {
    const res = await noteApi.regenerateNote({
      noteId: noteId.value,
      baseVersion: currentNote.value?.currentVersion || 1,
      additionalContent: additionalContent.value
    })
    // 假设 res 是 { version, noteContent }
    if (res) {
      showRegenerate.value = false
      previewVersion.value = null
      toast.success('重新生成成功')
      await loadData()
    } else {
      toast.error('启动重新生成失败')
    }
  } catch (error: any) {
    toast.error(error.message || '重新生成失败')
  } finally {
    uni.hideLoading()
    regenerateLoading.value = false
  }
}

// 素材预览
const previewMaterial = (item: Material) => {
  const url = item.fileUrl || item.filePath
  if (!url) return

  if (['png', 'jpg', 'jpeg', 'gif'].includes(item.fileType.toLowerCase())) {
    uni.previewImage({
      current: url,
      urls: materials.value
        .filter(m => ['png', 'jpg', 'jpeg', 'gif'].includes(m.fileType.toLowerCase()))
        .map(m => m.fileUrl || m.filePath)
    })
  } else if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(item.fileType.toLowerCase())) {
    uni.openDocument({
      filePath: url,
      success: () => console.log('打开文档成功'),
      fail: (err) => {
        // 如果是 URL，尝试下载后打开
        if (url.startsWith('http')) {
          uni.downloadFile({
            url,
            success: (res) => {
              uni.openDocument({
                filePath: res.tempFilePath,
                fail: () => toast.error('无法打开该类型的文档')
              })
            },
            fail: () => toast.error('文件下载失败')
          })
        } else {
          toast.error('无法打开该文档')
        }
      }
    })
  } else if (['mp4', 'mov', 'webm'].includes(item.fileType.toLowerCase())) {
    // 跳转到视频播放页或弹窗
    uni.navigateTo({
      url: `/pages-study/course/video?url=${encodeURIComponent(url)}&title=${encodeURIComponent(item.fileName)}`
    })
  } else {
    toast.info('该类型素材暂不支持预览')
  }
}

const getMaterialIcon = (type: string) => {
  const t = type.toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif'].includes(t)) return '/static/image.png'
  if (['pdf'].includes(t)) return '/static/pdf.png'
  if (['doc', 'docx'].includes(t)) return '/static/doc.png'
  if (['xls', 'xlsx'].includes(t)) return '/static/excel.png'
  if (['mp4', 'mov', 'webm'].includes(t)) return '/static/video.png'
  if (['mp3', 'wav', 'aac'].includes(t)) return '/static/audio.png'
  return '/static/file.png'
}

function handleEdit() {
  draftNoteContent.value = currentNote.value?.noteContent || ''
  mode.value = 'edit'
}

function handleCancelEdit() {
  mode.value = 'preview'
  draftNoteContent.value = ''
}

async function handleSaveEdit() {
  if (saving.value) return
  saving.value = true
  try {
    const res = await noteApi.editNote({
      id: noteId.value,
      noteContent: draftNoteContent.value,
      baseVersion: currentNote.value?.currentVersion
    })
    if (res.success) {
      toast.success('保存成功')
      mode.value = 'preview'
      await loadData()
    } else {
      toast.error(res.message || '保存失败')
    }
  } catch (error: any) {
    toast.error(error.message || '网络请求失败')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.note-detail-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f7f8fa;
}

.progress-container {
  padding: 40rpx;
  flex: 1;
  display: flex;
  align-items: center;
}

.detail-scroll {
  flex: 1;
  overflow: hidden;
}

.note-header {
  background: #fff;
  padding: 30rpx;
  margin-bottom: 20rpx;

  .title-section {
    .title {
      font-size: 40rpx;
      font-weight: bold;
      color: #323233;
      display: block;
      margin-bottom: 16rpx;
    }

    .meta {
      display: flex;
      align-items: center;
      gap: 20rpx;

      .time {
        font-size: 24rpx;
        color: #969799;
      }

      .version {
        font-size: 20rpx;
        color: #2b5cff;
        background: rgba(43, 92, 255, 0.1);
        padding: 4rpx 12rpx;
        border-radius: 4rpx;
      }
    }
  }

  .preview-banner {
    margin-top: 20rpx;
    background: #fff7e6;
    border: 1px solid #ffd591;
    border-radius: 8rpx;
    padding: 16rpx 24rpx;
    display: flex;
    align-items: center;
    gap: 16rpx;

    .banner-text {
      flex: 1;
      font-size: 26rpx;
      color: #fa8c16;
    }
  }
}

.material-section {
  background: #fff;
  padding: 24rpx 30rpx;
  margin-bottom: 20rpx;

  .section-title {
    font-size: 28rpx;
    font-weight: bold;
    color: #323233;
    margin-bottom: 20rpx;
  }

  .material-list {
    display: flex;
    flex-wrap: wrap;
    gap: 20rpx;

    .material-item {
      width: 150rpx;
      display: flex;
      flex-direction: column;
      align-items: center;

      .file-icon {
        width: 80rpx;
        height: 80rpx;
        margin-bottom: 12rpx;
      }

      .file-name {
        font-size: 22rpx;
        color: #646566;
        text-align: center;
        width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

.content-section {
  background: #fff;
  padding: 20rpx;
  min-height: 400rpx;
}

.footer-placeholder {
  height: 120rpx;
}

.footer-actions {
  background: #fff;
  display: flex;
  align-items: center;
  padding: 20rpx 40rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.05);

  .action-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;
    margin-right: 60rpx;
    color: #646566;

    text {
      font-size: 22rpx;
    }

    &.primary {
      flex: 1;
      margin-right: 0;
      background: #2b5cff;
      color: #fff;
      flex-direction: row;
      justify-content: center;
      height: 80rpx;
      border-radius: 40rpx;
      gap: 12rpx;

      text {
        font-size: 28rpx;
        font-weight: 500;
      }
    }
  }
}

.version-popup {
  display: flex;
  flex-direction: column;
  height: 100%;

  .popup-header {
    padding: 30rpx;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #ebedf0;

    .popup-title {
      font-size: 32rpx;
      font-weight: bold;
    }
  }

  .version-list-scroll {
    flex: 1;
    padding: 0 30rpx;

    .version-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 30rpx 0;
      border-bottom: 1px solid #f2f3f5;

      &.active {
        .ver-num {
          color: #2b5cff;
          &::after {
            content: '(当前)';
            font-size: 22rpx;
            margin-left: 8rpx;
          }
        }
      }

      .version-info {
        .ver-num {
          font-size: 28rpx;
          font-weight: 500;
          color: #323233;
          display: block;
          margin-bottom: 8rpx;
        }
        .ver-time {
          font-size: 24rpx;
          color: #969799;
        }
      }

      .ver-ops {
        display: flex;
        gap: 16rpx;
      }
    }
  }
}

.regenerate-popup {
  .popup-title {
    font-size: 32rpx;
    font-weight: bold;
    color: #323233;
    display: block;
    margin-bottom: 16rpx;
    text-align: center;
  }

  .popup-desc {
    font-size: 26rpx;
    color: #969799;
    line-height: 1.5;
    text-align: center;
  }

  .popup-btns {
    display: flex;
    margin-top: 30rpx;
  }
}
</style>
