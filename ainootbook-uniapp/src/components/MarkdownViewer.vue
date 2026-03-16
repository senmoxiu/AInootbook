<template>
  <view class="markdown-viewer">
    <!-- mp-html 渲染预编译的 HTML -->
    <mp-html
      v-if="!renderError && htmlContent"
      :content="htmlContent"
      :tag-style="tagStyle"
      :copy-link="false"
      :preview-img="false"
      @linktap="handleLinkTap"
      @imgtap="handleImageTap"
      @error="handleMediaError"
      class="mp-html-container"
    />
    <!-- 渲染错误降级：显示原始 Markdown -->
    <view v-else class="markdown-fallback">
      <text class="fallback-hint">内容渲染失败，显示原始文本</text>
      <text class="fallback-text">{{ rawContent || htmlContent }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  /** 后端预编译的 HTML 内容 */
  htmlContent?: string
  /** 原始 Markdown 内容（降级用） */
  rawContent?: string
}

const props = withDefaults(defineProps<Props>(), {
  htmlContent: '',
  rawContent: ''
})

const renderError = ref(false)

const tagStyle = {
  table: 'box-sizing: border-box; border-top: 1px solid #dfe2e5; border-left: 1px solid #dfe2e5; overflow-x: auto; display: block; max-width: 100%; white-space: nowrap;',
  th: 'border-right: 1px solid #dfe2e5; border-bottom: 1px solid #dfe2e5; padding: 6px 13px; font-weight: bold; background-color: #f6f8fa;',
  td: 'border-right: 1px solid #dfe2e5; border-bottom: 1px solid #dfe2e5; padding: 6px 13px;',
  pre: 'overflow-x: auto; background-color: #f6f8fa; padding: 16px; border-radius: 6px; display: block; max-width: 100%; margin: 10px 0;',
  code: 'font-family: Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace; font-size: 14px; white-space: pre;',
  img: 'max-width: 100%; height: auto; display: block; margin: 10px auto;',
  p: 'font-size: 15px; line-height: 1.8; margin-bottom: 10px; word-wrap: break-word;',
  h1: 'font-size: 20px; font-weight: bold; margin: 15px 0;',
  h2: 'font-size: 18px; font-weight: bold; margin: 15px 0;',
  h3: 'font-size: 16px; font-weight: bold; margin: 15px 0;',
  h4: 'font-size: 15px; font-weight: bold; margin: 15px 0;',
  h5: 'font-size: 14px; font-weight: bold; margin: 15px 0;',
  h6: 'font-size: 14px; font-weight: bold; margin: 15px 0;',
  blockquote: 'border-left: 4px solid #dfe2e5; padding: 0 15px; color: #6a737d; margin: 0 0 16px 0; background-color: #f8f9fa;',
  ul: 'padding-left: 20px; margin-bottom: 10px;',
  ol: 'padding-left: 20px; margin-bottom: 10px;',
  li: 'margin-bottom: 5px; font-size: 15px; line-height: 1.8;'
};

const handleLinkTap = (e: any) => {
  const url = e.href
  if (!url) return

  // 协议白名单校验
  const allowedProtocols = ['http:', 'https:', 'mailto:', 'tel:']
  try {
    const urlObj = new URL(url, window.location.href)
    if (!allowedProtocols.includes(urlObj.protocol)) {
      uni.showToast({
        title: '不支持的链接协议',
        icon: 'none'
      })
      return
    }
  } catch (error) {
    console.error('Invalid URL:', url, error)
    return
  }

  // 锚点跳转（不弹窗）
  if (url.startsWith('#')) {
    // mp-html 会自动处理锚点，这里不需要额外处理
    return
  }

  // 站内路由（不弹窗）
  if (url.startsWith('/')) {
    uni.navigateTo({ url })
    return
  }

  // 外部链接（弹窗确认）
  uni.showModal({
    title: '温馨提示',
    content: '是否要在外部浏览器中打开此链接？',
    success: (res) => {
      if (res.confirm) {
        // #ifdef H5
        window.open(url, '_blank')
        // #endif

        // #ifndef H5
        if (typeof plus !== 'undefined' && plus.runtime) {
          plus.runtime.openURL(url)
        } else {
          uni.setClipboardData({
            data: url,
            success: () => {
              uni.showToast({
                title: '链接已复制，请在浏览器中打开',
                icon: 'none'
              })
            }
          })
        }
        // #endif
      }
    }
  })
}

// 媒体资源加载错误（不触发整体降级）
const handleMediaError = (e: any) => {
  console.warn('Media resource load error:', e)
  // 不设置 renderError，让其他内容正常渲染
}

// 图片点击预览
const handleImageTap = (e: any) => {
  const { src } = e
  if (!src) return

  uni.previewImage({
    current: src,
    urls: [src]
  })
}
</script>

<style lang="scss" scoped>
.markdown-viewer {
  font-size: 15px;
  line-height: 1.8;
  color: #333;
  word-wrap: break-word;
  padding: 10px;
  background-color: #ffffff;
  
  :deep(.mp-html-container) {
    /* Handle math formulas, inline code blocks etc. */
    .katex, .math {
      overflow-x: auto;
      overflow-y: hidden;
      display: inline-block;
      max-width: 100%;
    }
    
    .katex-display {
      display: block;
      margin: 10px 0;
      text-align: center;
    }
  }
}

.markdown-fallback {
  background-color: #f6f8fa;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;

  .fallback-hint {
    display: block;
    font-size: 12px;
    color: #f56c6c;
    margin-bottom: 10px;
  }

  .fallback-text {
    font-family: Consolas, Monaco, monospace;
    font-size: 14px;
    white-space: pre-wrap;
    word-break: break-all;
    color: #333;
    line-height: 1.6;
  }
}
</style>
