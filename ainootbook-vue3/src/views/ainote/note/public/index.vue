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
                    <span>{{ note.createBy || '未知作者' }}</span>
                    <span>{{ note.createTime }}</span>
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
  import { useRouter } from 'vue-router';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { PageWrapper } from '/@/components/Page';
  import { getPublicNotes, type NoteRecord } from '/@/api/ainote/note.api';

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

  function truncate(text: string | undefined, max: number): string {
    if (!text) return '';
    return text.length > max ? text.slice(0, max) + '...' : text;
  }

  function splitKeywords(keywords: string | undefined): string[] {
    if (!keywords) return [];
    return keywords
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
      font-size: 12px;
      color: #999;
    }

    &__pagination {
      margin-top: 24px;
      text-align: right;
    }
  }
</style>
