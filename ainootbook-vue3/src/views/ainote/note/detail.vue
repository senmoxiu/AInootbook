<template>
  <PageWrapper title="笔记详情" @back="router.back()">
    <!-- 基本信息 -->
    <a-card title="基本信息" :bordered="false" class="!mb-4">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="笔记标题">{{ noteData.noteTitle }}</a-descriptions-item>
        <a-descriptions-item label="笔记状态">{{ noteData.noteStatus }}</a-descriptions-item>
        <a-descriptions-item label="是否公开">{{ noteData.isPublic == '1' ? '公开' : '私密' }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ noteData.createBy }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ noteData.createTime }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <!-- AI 摘要与关键词 -->
    <a-card title="AI摘要与关键词" :bordered="false" class="!mb-4">
      <div v-if="noteData.aiSummary" class="mb-2">{{ noteData.aiSummary }}</div>
      <div v-if="noteData.keywords">
        <a-tag v-for="kw in keywordList" :key="kw" color="blue">{{ kw }}</a-tag>
      </div>
      <a-empty v-if="!noteData.aiSummary && !noteData.keywords" description="暂无AI摘要" />
    </a-card>

    <!-- 笔记内容 -->
    <a-card title="笔记内容" :bordered="false" class="!mb-4">
      <template #extra>
        <a-button v-if="mode === 'preview'" type="primary" @click="handleEdit">编辑</a-button>
        <a-space v-else>
          <a-button @click="handleCancel" :disabled="saving">取消</a-button>
          <a-button type="primary" @click="handleSave" :loading="saving">保存</a-button>
        </a-space>
      </template>
      <MarkdownViewer v-if="mode === 'preview' && noteData.noteContent" :value="sanitizedContent" />
      <JMarkdownEditor v-else-if="mode === 'edit'" v-model:value="draftNoteContent" />
      <a-empty v-else description="暂无内容" />
    </a-card>

    <!-- 素材列表 -->
    <a-card title="素材列表" :bordered="false" class="!mb-4">
      <template #extra>
        <a-upload
          :customRequest="handleUpload"
          :showUploadList="false"
          :beforeUpload="beforeUpload"
          :multiple="true"
          accept=".mp3,.wav,.m4a,.pdf,.docx,.pptx,.xlsx,.md,.txt,.jpg,.jpeg,.png,.mp4,.avi,.mov,.mkv,.webm,.m4v"
        >
          <a-button type="primary" :loading="uploading">上传素材</a-button>
        </a-upload>
      </template>
      <a-table :dataSource="materialList" :columns="materialColumns" :pagination="false" rowKey="id" size="small">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileType'">
            <a-space>
              <SoundOutlined v-if="isAudio(record.fileType)" />
              <PictureOutlined v-else-if="isImage(record.fileType)" />
              <VideoCameraOutlined v-else-if="isVideo(record.fileType)" />
              <FileTextOutlined v-else />
              {{ record.fileType }}
            </a-space>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="isImage(record.fileType)" type="link" size="small" @click="handlePreview(record)"> 预览 </a-button>
              <a-button type="link" size="small" @click="handleDownload(record)">下载</a-button>
              <a-popconfirm title="确定要删除吗？" @confirm="handleDeleteMaterial(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- AI 生成 -->
    <a-card title="AI生成" :bordered="false" class="!mb-4">
      <AiProcessProgress :noteId="noteId" knowledgeId="" @completed="loadNoteData" />
    </a-card>

    <!-- 分享 -->
    <a-card title="分享" :bordered="false" class="!mb-4">
      <a-button type="primary" @click="handleShare" :loading="shareLoading">创建分享</a-button>
      <div v-if="shareResult.shareCode" class="mt-3">
        <a-alert type="success" show-icon>
          <template #message>
            分享码：<a-typography-text copyable>{{ shareResult.shareCode }}</a-typography-text>
          </template>
        </a-alert>
      </div>
    </a-card>

    <!-- 图片预览 -->
    <a-modal v-model:open="previewVisible" title="图片预览" :footer="null" :width="800">
      <img :src="previewUrl" style="width: 100%" alt="素材预览" />
    </a-modal>
  </PageWrapper>
</template>

<script lang="ts" setup>
  import { ref, computed, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { PageWrapper } from '/@/components/Page';
  import { getNoteById, shareNote, editNote } from '/@/api/ainote/note.api';
  import { getMaterialList, uploadMaterial, getPresignedUrl, deleteMaterial } from '/@/api/ainote/material.api';
  import AiProcessProgress from './components/AiProcessProgress.vue';
  import { MarkdownViewer } from '/@/components/Markdown';
  import { SoundOutlined, PictureOutlined, VideoCameraOutlined, FileTextOutlined } from '@ant-design/icons-vue';
  import xss from 'xss';

  const route = useRoute();
  const router = useRouter();
  const { createMessage } = useMessage();

  const noteId = ref<string>((route.params.id as string) || '');
  const noteData = ref<Record<string, string | number>>({});
  const materialList = ref<Record<string, string | number>[]>([]);
  const shareLoading = ref(false);
  const shareResult = ref<Record<string, string>>({});
  const uploading = ref(false);
  const uploadCount = ref(0);
  const previewVisible = ref(false);
  const previewUrl = ref('');
  const mode = ref<'preview' | 'edit'>('preview');
  const draftNoteContent = ref('');
  const saving = ref(false);

  const keywordList = computed(() => {
    const kw = noteData.value.keywords;
    if (!kw || typeof kw !== 'string') return [];
    return kw
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean);
  });

  const sanitizedContent = computed(() => {
    const content = noteData.value.noteContent;
    if (!content || typeof content !== 'string') return '';
    return xss(content);
  });

  const materialColumns = [
    { title: '文件名', dataIndex: 'fileName', key: 'fileName' },
    { title: '文件类型', dataIndex: 'fileType', key: 'fileType', width: 100 },
    { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 120 },
    { title: '处理状态', dataIndex: 'processStatus', key: 'processStatus', width: 100 },
    { title: '操作', key: 'action', width: 150 },
  ];

  const AUDIO_EXTS = ['mp3', 'wav', 'm4a'];
  const IMAGE_EXTS = ['jpg', 'jpeg', 'png'];
  const VIDEO_EXTS = ['mp4', 'avi', 'mov', 'mkv', 'webm', 'm4v'];

  function isAudio(type: string | number | undefined) {
    return String(type || '').toLowerCase() === 'audio';
  }

  function isImage(type: string | number | undefined) {
    return String(type || '').toLowerCase() === 'image';
  }

  function isVideo(type: string | number | undefined) {
    return String(type || '').toLowerCase() === 'video';
  }

  function beforeUpload(file: File): boolean {
    const name = file.name.toLowerCase();
    const size = file.size;
    const ALLOWED_EXTS = [...AUDIO_EXTS, 'pdf', 'docx', 'pptx', 'xlsx', 'md', 'txt', ...IMAGE_EXTS, ...VIDEO_EXTS];
    const ext = name.split('.').pop() || '';
    if (!ALLOWED_EXTS.includes(ext)) {
      createMessage.error(`不支持的文件类型: .${ext}`);
      return false;
    }
    const isAudioFile = AUDIO_EXTS.includes(ext);
    const isImageFile = IMAGE_EXTS.includes(ext);
    const isVideoFile = VIDEO_EXTS.includes(ext);

    if (isAudioFile && size > 150 * 1024 * 1024) {
      createMessage.error('音频文件最大限制 150MB');
      return false;
    }
    if (isImageFile && size > 10 * 1024 * 1024) {
      createMessage.error('图片文件最大限制 10MB');
      return false;
    }
    if (isVideoFile && size > 500 * 1024 * 1024) {
      createMessage.error('视频文件最大限制 500MB');
      return false;
    }
    if (!isAudioFile && !isImageFile && !isVideoFile && size > 50 * 1024 * 1024) {
      createMessage.error('文档最大限制 50MB');
      return false;
    }
    return true;
  }

  async function handleUpload({ file, onSuccess, onError }: { file: File; onSuccess?: Function; onError?: Function }) {
    try {
      uploadCount.value++;
      uploading.value = true;
      const formData = new FormData();
      formData.append('file', file);
      formData.append('noteId', noteId.value);
      await uploadMaterial(formData);
      onSuccess?.({});
      createMessage.success('素材上传成功');
      await loadMaterials();
    } catch (e) {
      onError?.(e);
      createMessage.error('素材上传失败');
    } finally {
      uploadCount.value--;
      if (uploadCount.value === 0) uploading.value = false;
    }
  }

  async function handlePreview(record: Record<string, string | number>) {
    try {
      const url = await getPresignedUrl(record.id as string);
      previewUrl.value = url;
      previewVisible.value = true;
    } catch {
      createMessage.error('获取预览链接失败');
    }
  }

  async function handleDownload(record: Record<string, string | number>) {
    try {
      const url = await getPresignedUrl(record.id as string);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch {
      createMessage.error('获取下载链接失败');
    }
  }

  async function handleDeleteMaterial(record: Record<string, string | number>) {
    try {
      await deleteMaterial({ id: record.id as string }, () => {});
      createMessage.success('删除成功');
      await loadMaterials();
    } catch {
      createMessage.error('删除失败');
    }
  }

  async function loadNoteData() {
    try {
      const res = await getNoteById({ id: noteId.value });
      noteData.value = res || {};
    } catch (e) {
      createMessage.error('加载笔记详情失败');
    }
  }

  async function loadMaterials() {
    try {
      const res = await getMaterialList({ noteId: noteId.value });
      materialList.value = res?.records || res || [];
    } catch (e) {
      createMessage.error('加载素材列表失败');
    }
  }

  async function handleShare() {
    try {
      shareLoading.value = true;
      const res = await shareNote({ noteId: noteId.value });
      shareResult.value = res || {};
      createMessage.success('分享创建成功');
    } catch (e) {
      createMessage.error('创建分享失败');
    } finally {
      shareLoading.value = false;
    }
  }

  function handleEdit() {
    draftNoteContent.value = String(noteData.value.noteContent || '');
    mode.value = 'edit';
  }

  function handleCancel() {
    mode.value = 'preview';
    draftNoteContent.value = '';
  }

  async function handleSave() {
    try {
      saving.value = true;
      await editNote({ id: noteId.value, noteContent: draftNoteContent.value });
      createMessage.success('保存成功');
      await loadNoteData();
      mode.value = 'preview';
    } catch (e) {
      createMessage.error('保存失败');
    } finally {
      saving.value = false;
    }
  }

  onMounted(() => {
    if (!noteId.value) {
      createMessage.error('缺少笔记 ID');
      router.back();
      return;
    }
    loadNoteData();
    loadMaterials();
  });
</script>
