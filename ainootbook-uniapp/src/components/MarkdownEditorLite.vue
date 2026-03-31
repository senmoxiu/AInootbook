<template>
  <view class="markdown-editor-lite">
    <!-- 工具栏 -->
    <view class="toolbar">
      <wd-button size="small" @click="insertMarkdown('# ')">H1</wd-button>
      <wd-button size="small" @click="insertMarkdown('## ')">H2</wd-button>
      <wd-button size="small" @click="insertMarkdown('**', '**')">粗体</wd-button>
      <wd-button size="small" @click="insertMarkdown('*', '*')">斜体</wd-button>
      <wd-button size="small" @click="insertMarkdown('- ')">列表</wd-button>
      <wd-button size="small" @click="insertMarkdown('> ')">引用</wd-button>
      <wd-button size="small" @click="insertMarkdown('```\n', '\n```')">代码</wd-button>
      <wd-button size="small" @click="insertMarkdown('[', '](url)')">链接</wd-button>
    </view>

    <!-- 编辑/预览切换 -->
    <wd-tabs v-model="activeTab">
      <wd-tab title="编辑" name="edit">
        <wd-textarea
          v-model="syncedContent"
          placeholder="请输入 Markdown 内容..."
          :maxlength="50000"
          auto-height
        />
      </wd-tab>
      <wd-tab title="预览" name="preview">
        <MarkdownViewer :rawContent="syncedContent" />
      </wd-tab>
    </wd-tabs>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import MarkdownViewer from './MarkdownViewer.vue'

interface Props {
  modelValue: string
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue'])

const activeTab = ref('edit')

const syncedContent = computed({
  get: () => props.modelValue || '',
  set: (val) => emit('update:modelValue', val)
})

function insertMarkdown(prefix: string, suffix = '') {
  const textarea = syncedContent.value
  syncedContent.value = textarea + prefix + suffix
}
</script>

<style lang="scss" scoped>
.markdown-editor-lite {
  .toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 8rpx;
    padding: 16rpx;
    background: #f7f8fa;
    border-bottom: 1px solid #ebedf0;
  }
}
</style>
