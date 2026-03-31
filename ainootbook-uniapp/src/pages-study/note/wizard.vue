<template>
  <view class="note-wizard-page">
    <view class="wizard-header">
      <wd-steps :active="store.step - 1" align-center>
        <wd-step title="选择" />
        <wd-step title="上传" />
        <wd-step title="分析" />
        <wd-step title="整合" />
        <wd-step title="预览" />
      </wd-steps>
    </view>

    <scroll-view scroll-y class="wizard-content">
      <!-- Step 1: 选择课程与章节 -->
      <view v-show="store.step === 1" class="step-pane">
        <view class="card">
          <view class="card-title">选择课程</view>
          <wd-picker
            :modelValue="store.wizardData.courseId"
            :columns="courseOptions"
            label-key="courseName"
            value-key="id"
            @confirm="onCourseConfirm"
            placeholder="请选择课程"
            align-right
          />
        </view>
        <view class="card mt-4" v-if="store.wizardData.courseId && chapterTree.length > 0">
          <view class="card-title">选择章节</view>
          <DaTree
            :data="chapterTree"
            labelField="chapterName"
            valueField="id"
            defaultExpandAll
            :defaultCheckedKeys="store.wizardData.chapterId ? [store.wizardData.chapterId] : []"
            @change="onChapterChange"
          />
        </view>
      </view>

      <!-- Step 2: 上传素材 -->
      <view v-show="store.step === 2" class="step-pane">
        <view class="card">
          <view class="card-title">上传参考素材</view>
          <text class="desc">支持图片、文档、音频、视频等格式</text>
          <MaterialUpload
            :noteId="store.wizardData.noteId"
            multiple
            @upload-success="onUploadSuccess"
            @delete="onMaterialDelete"
          />
        </view>
      </view>

      <!-- Step 3: AI 分析进度 -->
      <view v-show="store.step === 3" class="step-pane progress-pane">
        <AiProgressTracker
          v-if="store.step === 3"
          :noteId="store.wizardData.noteId"
          @complete="onAiComplete"
          @failed="onAiFailed"
          @cancelled="onAiCancelled"
        />
      </view>

      <!-- Step 4: AI 整合内容 -->
      <view v-show="store.step === 4" class="step-pane">
        <view class="card">
          <view class="card-title">AI 生成内容摘要</view>
          <scroll-view scroll-y class="content-preview">
            <text class="content-text">{{ store.wizardData.generatedContent || '内容生成中...' }}</text>
          </scroll-view>
        </view>
        <view class="card mt-4">
          <view class="card-title">补充说明（可选）</view>
          <wd-textarea
            v-model="store.wizardData.additionalContent"
            placeholder="输入您希望 AI 补充的内容方向，如：详细说明概念、增加示例等..."
            auto-height
          />
          <wd-button @click="regenerateContent" plain type="warning" block class="mt-4">
            根据补充说明重新生成
          </wd-button>
        </view>
      </view>

      <!-- Step 5: 预览并保存 -->
      <view v-show="store.step === 5" class="step-pane">
        <view class="card">
          <view class="card-title">笔记标题</view>
          <wd-input
            v-model="store.wizardData.title"
            placeholder="请输入笔记标题"
            clearable
          />
        </view>
        <view class="card mt-4">
          <view class="card-title">最终预览</view>
          <view class="markdown-preview">
            <mp-html :content="store.wizardData.generatedContent || ''" />
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作按钮 -->
    <view class="wizard-footer">
      <wd-button v-if="store.step > 1 && store.step !== 3" @click="prevStep" plain class="btn-action">上一步</wd-button>
      <wd-button v-if="store.step < 5 && store.step !== 3" @click="nextStep" type="primary" class="btn-action" :disabled="!canNext">下一步</wd-button>
      <wd-button v-if="store.step === 5" @click="saveNote" type="success" class="btn-action">保存笔记</wd-button>
      <wd-button @click="cancelWizard" type="info" plain class="btn-action">取消</wd-button>
    </view>

    <wd-toast id="toast" />
    <wd-message-box id="messageBox" />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useMessage, useToast } from 'wot-design-uni'
import { useNoteWizardStore } from '@/store/noteWizard'
import { courseApi, type Course, type CourseChapter } from '@/api/course'
import { noteApi } from '@/api/note'
import type { Material } from '@/api/material'
import MaterialUpload from '@/components/MaterialUpload.vue'
import DaTree from '@/uni_modules/da-tree/index.vue'
import AiProgressTracker from '@/components/AiProgressTracker.vue'

const store = useNoteWizardStore()
const message = useMessage()
const toast = useToast()

const courseOptions = ref<Course[]>([])
const chapterTree = ref<CourseChapter[]>([])

const canNext = computed(() => {
  if (store.step === 1) {
    return !!(store.wizardData.courseId && store.wizardData.chapterId)
  }
  return true
})

onMounted(() => {
  checkDraft()
})

onUnmounted(() => {})

const checkDraft = () => {
  const hasDraft = Object.keys(store.wizardData).length > 0 && store.step > 1
  if (hasDraft) {
    message.confirm({
      title: '发现草稿',
      msg: '您有未完成的笔记创建记录，是否继续？',
      confirmButtonText: '继续',
      cancelButtonText: '重新开始'
    }).then(() => {
      fetchCourses()
      if (store.wizardData.courseId) {
        fetchChapters(store.wizardData.courseId)
      }
    }).catch(() => {
      store.clearWizardData()
      fetchCourses()
    })
  } else {
    store.clearWizardData()
    fetchCourses()
  }
}

const fetchCourses = async () => {
  try {
    const res = await courseApi.getCourseList({ pageSize: 100 })
    courseOptions.value = (res as any).result?.records || (res as any).records || []
  } catch (err) {
    console.error(err)
    toast.error('获取课程失败')
  }
}

const fetchChapters = async (courseId: string) => {
  try {
    const res = await courseApi.getCourseDetail(courseId)
    const detail = (res as any).result || (res as any).data || res
    chapterTree.value = detail.chapterTree || detail.chapters || detail.chapterList || []
  } catch (err) {
    console.error(err)
    toast.error('获取章节失败')
  }
}

const onCourseConfirm = async (e: any) => {
  const { value, selectedItems } = e
  const selectedItem = selectedItems?.[0] || selectedItems
  store.setWizardData({
    courseId: value,
    courseName: selectedItem?.courseName || '',
    chapterId: '',
    chapterName: ''
  })
  chapterTree.value = []
  if (value) {
    uni.showLoading({ title: '加载章节中' })
    await fetchChapters(value)
    uni.hideLoading()
  }
}

const onChapterChange = (allCheckedKeys: any, currentItem: any) => {
  const item = Array.isArray(currentItem) ? currentItem[0] : currentItem
  if (item) {
    store.setWizardData({
      chapterId: item.id,
      chapterName: item.chapterName || item.name
    })
  }
}

const onUploadSuccess = (material: Material) => {
  const ids = store.wizardData.materialIds || []
  if (!ids.includes(material.id)) {
    store.setWizardData({ materialIds: [...ids, material.id] })
  }
}

const onMaterialDelete = (id: string) => {
  const ids = store.wizardData.materialIds || []
  store.setWizardData({ materialIds: ids.filter((i: string) => i !== id) })
}

const prevStep = () => {
  if (store.step > 1) {
    store.setStep(store.step - 1)
  }
}

const nextStep = async () => {
  if (store.step === 1) {
    if (!store.wizardData.courseId) return toast.warning('请选择课程')
    if (!store.wizardData.chapterId) return toast.warning('请选择章节')

    uni.showLoading({ title: '准备中...' })
    try {
      if (!store.wizardData.noteId) {
        const res = await noteApi.addNote({
          courseId: store.wizardData.courseId,
          courseName: store.wizardData.courseName,
          chapterId: store.wizardData.chapterId,
          chapterName: store.wizardData.chapterName,
          noteTitle: '新建笔记'
        })
        if (res.success && res.result) {
          const noteId = typeof res.result === 'string' ? res.result : res.result.id
          store.setWizardData({
            noteId: noteId,
            currentVersion: 1,
            title: '新建笔记'
          })
          store.setStep(2)
        } else {
          toast.error(res.message || '创建草稿失败')
        }
      } else {
        store.setStep(2)
      }
    } catch (err: any) {
      toast.error(err.message || '系统异常')
    } finally {
      uni.hideLoading()
    }
  } else if (store.step === 2) {
    const ids = store.wizardData.materialIds || []
    if (ids.length === 0) {
      message.confirm({
        title: '提示',
        msg: '尚未上传任何参考素材，直接生成可能效果不佳，是否继续？'
      }).then(() => {
        startAiAnalysis()
      }).catch(() => {})
    } else {
      startAiAnalysis()
    }
  } else if (store.step === 4) {
    if (!store.wizardData.generatedContent) return toast.warning('未获取到内容')
    store.setStep(5)
  }
}

const startAiAnalysis = async () => {
  try {
    uni.showLoading({ title: '启动 AI 分析...' })
    await noteApi.triggerGeneration(store.wizardData.noteId)
    store.setStep(3)
  } catch (err: any) {
    toast.error(err.message || '触发生成失败')
  } finally {
    uni.hideLoading()
  }
}

const onAiComplete = async () => {
  try {
    uni.showLoading({ title: '正在获取内容...' })
    const detail = await noteApi.getNoteDetail(store.wizardData.noteId)
    const content = (detail as any).result?.noteContent || (detail as any).noteContent || '生成内容为空'
    const version = (detail as any).result?.currentVersion || (detail as any).currentVersion || store.wizardData.currentVersion
    store.setWizardData({
      generatedContent: content,
      currentVersion: version
    })
    store.setStep(4)
  } catch (err: any) {
    toast.error('获取笔记内容失败')
    store.setStep(2)
  } finally {
    uni.hideLoading()
  }
}

const onAiFailed = (error: Error) => {
  toast.error(error.message || '生成失败')
  store.setStep(2)
}

const onAiCancelled = () => {
  toast.info('任务已取消')
  store.setStep(2)
}

const regenerateContent = async () => {
  if (!store.wizardData.additionalContent) {
    return toast.warning('请输入补充说明')
  }
  uni.showLoading({ title: '重新生成中...', mask: true })
  try {
    const res = await noteApi.regenerateNote({
      noteId: store.wizardData.noteId,
      baseVersion: store.wizardData.currentVersion || 1,
      additionalContent: store.wizardData.additionalContent
    })
    if (res && res.noteContent) {
      store.setWizardData({
        generatedContent: res.noteContent,
        currentVersion: res.version || store.wizardData.currentVersion
      })
      toast.success('重新生成成功')
    } else if (res && (res as any).result?.noteContent) {
      store.setWizardData({
        generatedContent: (res as any).result.noteContent,
        currentVersion: (res as any).result.version || store.wizardData.currentVersion
      })
      toast.success('重新生成成功')
    } else {
      toast.error('重新生成失败')
    }
  } catch (err: any) {
    toast.error(err.message || '重新生成失败')
  } finally {
    uni.hideLoading()
  }
}

const saveNote = async () => {
  if (!store.wizardData.title) return toast.warning('请输入笔记标题')
  uni.showLoading({ title: '保存中...' })
  try {
    const res = await noteApi.editNote({
      id: store.wizardData.noteId,
      noteTitle: store.wizardData.title,
      noteContent: store.wizardData.generatedContent,
      noteStatus: 'COMPLETED'
    })
    if (res.success) {
      toast.success('保存成功')
      store.clearWizardData()
      setTimeout(() => {
        uni.navigateBack()
      }, 1500)
    } else {
      toast.error(res.message || '保存失败')
    }
  } catch (err: any) {
    toast.error(err.message || '系统异常')
  } finally {
    uni.hideLoading()
  }
}

const cancelWizard = () => {
  message.confirm({
    title: '确认取消',
    msg: '取消后将清空当前草稿，是否确认？'
  }).then(async () => {
    if (store.wizardData.noteId) {
      await noteApi.deleteNote(store.wizardData.noteId)
    }
    store.clearWizardData()
    uni.navigateBack()
  }).catch(() => {})
}
</script>

<style scoped lang="scss">
.note-wizard-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f5;

  .wizard-header {
    padding: 30rpx 20rpx;
    background-color: #fff;
    border-bottom: 1rpx solid #eee;
  }

  .wizard-content {
    flex: 1;
    overflow-y: auto;
    padding: 20rpx;
  }

  .step-pane {
    animation: fadeIn 0.3s ease-in-out;
  }

  .card {
    background-color: #fff;
    border-radius: 12rpx;
    padding: 30rpx;
    
    .card-title {
      font-size: 32rpx;
      font-weight: bold;
      color: #333;
      margin-bottom: 20rpx;
      display: flex;
      align-items: center;
      
      &::before {
        content: '';
        display: inline-block;
        width: 8rpx;
        height: 32rpx;
        background-color: #39b54a;
        border-radius: 4rpx;
        margin-right: 12rpx;
      }
    }
    
    .desc {
      font-size: 24rpx;
      color: #999;
      margin-bottom: 20rpx;
      display: block;
    }
  }

  .progress-pane {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 100rpx;

    .progress-circle {
      width: 80%;
      margin-bottom: 40rpx;
    }

    .progress-text {
      font-size: 32rpx;
      color: #333;
      margin-bottom: 16rpx;
    }

    .progress-sub {
      font-size: 28rpx;
      color: #999;
    }
  }

  .content-preview {
    height: 400rpx;
    background-color: #f8f8f8;
    border-radius: 8rpx;
    padding: 20rpx;
    
    .content-text {
      font-size: 28rpx;
      color: #333;
      line-height: 1.6;
      white-space: pre-wrap;
    }
  }

  .markdown-preview {
    min-height: 300rpx;
    padding: 20rpx;
    background-color: #f8f8f8;
    border-radius: 8rpx;
  }

  .wizard-footer {
    display: flex;
    justify-content: space-around;
    padding: 20rpx 30rpx 40rpx;
    background-color: #fff;
    box-shadow: 0 -2rpx 10rpx rgba(0,0,0,0.05);

    .btn-action {
      flex: 1;
      margin: 0 10rpx;
    }
  }

  .mt-4 {
    margin-top: 30rpx;
  }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10rpx); }
  to { opacity: 1; transform: translateY(0); }
}
</style>