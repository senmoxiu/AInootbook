<template>
  <PageWrapper title="公开笔记广场">
    <div class="public-notes">
      <a-input-search v-model:value="keyword" placeholder="搜索公开笔记" enter-button allow-clear class="public-notes__search" @search="onSearch" />

      <a-spin :spinning="loading">
        <template v-if="noteList.length > 0">
          <a-row :gutter="[16, 16]">
            <a-col v-for="note in noteList" :key="note.id" :xs="24" :sm="12" :md="8" :lg="6">
              <a-card hoverable class="public-notes__card" @click="goToDetail(note.id)">
                <template #title>
                  <div class="public-notes__card-title" :title="note.noteTitle">
                    {{ note.noteTitle || '无标题' }}
                  </div>
                </template>

                <div class="public-notes__card-body">
                  {{ truncate(note.aiSummary, 100) || '暂无摘要' }}
                </div>

                <div class="public-notes__card-footer">
                  <div v-if="note.keywords" class="public-notes__keywords">
                    <a-tag v-for="(kw, idx) in splitKeywords(note.keywords)" :key="`${note.id}-kw-${idx}`" color="blue">{{ kw }}</a-tag>
                  </div>
                  <div class="public-notes__meta">
                    <div class="public-notes__meta-info">
                      <span>{{ note.createBy_dictText || note.createBy || '未知作者' }}</span>
                      <span>{{ note.createTime }}</span>
                    </div>
                    <span
                      class="public-notes__like-btn"
                      role="button"
                      tabindex="0"
                      :aria-label="note.isLiked ? '取消点赞' : '点赞'"
                      :aria-pressed="!!note.isLiked"
                      @click.stop="handleLike(note)"
                      @keydown.enter.stop="handleLike(note)"
                      @keydown.space.prevent.stop="handleLike(note)"
                    >
                      <LikeFilled v-if="note.isLiked" style="color: #1890ff" />
                      <LikeOutlined v-else />
                      {{ note.likeCount || 0 }}
                    </span>
                  </div>
                </div>
              </a-card>
            </a-col>
          </a-row>

          <div class="public-notes__pagination">
            <a-pagination v-model:current="pageNo" :total="total" :page-size="PAGE_SIZE" show-less-items @change="onPageChange" />
          </div>
        </template>

        <a-empty v-else-if="!loading" description="暂无公开笔记" />
      </a-spin>
    </div>
  </PageWrapper>
</template>

<script lang="ts" setup>
  import { ref, onMounted } from 'vue';
  import { Pagination as APagination } from 'ant-design-vue';
  import { useRouter } from 'vue-router';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { PageWrapper } from '/@/components/Page';
  import { getPublicNotes, likeNote, unlikeNote, type NoteRecord } from '/@/api/ainote/note.api';
  import { LikeOutlined, LikeFilled } from '@ant-design/icons-vue';

  const PAGE_SIZE = 12;

  const router = useRouter();
  const { createMessage } = useMessage();

  const keyword = ref('');
  const loading = ref(false);
  const noteList = ref<NoteRecord[]>([]);
  const pageNo = ref(1);
  const total = ref(0);

  async function fetchNotes() {
    loading.value = true;
    try {
      const res = await getPublicNotes({
        keyword: keyword.value,
        pageNo: pageNo.value,
        pageSize: PAGE_SIZE,
      });
      noteList.value = res?.records || [];
      total.value = res?.total || 0;
    } catch {
      createMessage.error('获取公开笔记失败');
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    pageNo.value = 1;
    fetchNotes();
  }

  function onPageChange(page: number) {
    pageNo.value = page;
    fetchNotes();
  }

  function goToDetail(id: string) {
    router.push(`/ainote/note/view/${id}`);
  }

  async function handleLike(note: NoteRecord) {
    const wasLiked = note.isLiked;
    note.isLiked = !wasLiked;
    note.likeCount = Math.max(0, (note.likeCount || 0) + (wasLiked ? -1 : 1));
    try {
      if (wasLiked) {
        await unlikeNote({ noteId: note.id });
      } else {
        await likeNote({ noteId: note.id });
      }
    } catch {
      note.isLiked = wasLiked;
      note.likeCount = Math.max(0, (note.likeCount || 0) + (wasLiked ? 1 : -1));
      createMessage.error('操作失败，请重试');
    }
  }

  function truncate(text: string | undefined, max: number): string {
    if (!text) return '';
    // 清除 Markdown 加粗符号
    const cleaned = String(text).replace(/\*\*/g, '');
    return cleaned.length > max ? cleaned.slice(0, max) + '...' : cleaned;
  }

  function splitKeywords(keywords: string | undefined): string[] {
    if (!keywords) return [];
    // 清除 JSON 数组符号和引号
    const cleaned = String(keywords).replace(/[\[\]"']/g, '');
    return cleaned
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }

  onMounted(fetchNotes);
</script>

<style lang="less" scoped>
  .public-notes {
    &__search {
      max-width: 400px;
      margin-bottom: 16px;
    }

    &__card {
      height: 100%;
      display: flex;
      flex-direction: column;

      :deep(.ant-card-body) {
        flex: 1;
        display: flex;
        flex-direction: column;
      }
    }

    &__card-title {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    &__card-body {
      flex: 1;
      color: #666;
      font-size: 14px;
      line-height: 1.6;
      margin-bottom: 12px;
      overflow-wrap: break-word;
    }

    &__card-footer {
      margin-top: auto;
      border-top: 1px solid #f0f0f0;
      padding-top: 10px;
    }

    &__keywords {
      margin-bottom: 8px;
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    &__meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 12px;
      color: #999;
    }

    &__meta-info {
      display: flex;
      gap: 12px;
    }

    &__like-btn {
      display: flex;
      align-items: center;
      gap: 4px;
      cursor: pointer;
      user-select: none;
      transition: color 0.2s;

      &:hover {
        color: #1890ff;
      }
    }

    &__pagination {
      margin-top: 24px;
      text-align: right;
    }
  }
</style>
