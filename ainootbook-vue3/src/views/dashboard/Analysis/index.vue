<template>
  <div class="p-4 education-dashboard">
    <a-spin :spinning="loading">
      <!-- 统计卡片 -->
      <a-row :gutter="16">
        <a-col :md="6" :sm="12" :xs="24" v-for="(item, index) in statCards" :key="index">
          <a-card :bordered="false" class="mb-4">
            <a-statistic :title="item.title" :value="item.value">
              <template #prefix>
                <Icon :icon="item.icon" :color="item.color" size="24" class="mr-2" />
              </template>
            </a-statistic>
          </a-card>
        </a-col>
      </a-row>

      <!-- 图表区 -->
      <a-row :gutter="16" class="mt-2">
        <!-- AI 任务状态分布 -->
        <a-col :md="10" :sm="24" :xs="24">
          <a-card title="AI 任务状态分布" :bordered="false" class="mb-4">
            <Pie :chartData="taskChartData" height="280px" />
          </a-card>
        </a-col>

        <!-- 热门课程排行 -->
        <a-col :md="14" :sm="24" :xs="24">
          <a-card title="课程列表" :bordered="false" class="mb-4">
            <RankList :list="courseRankList" :height="260" />
          </a-card>
        </a-col>
      </a-row>

      <!-- 最近笔记列表 -->
      <a-row :gutter="16">
        <a-col :xs="24">
          <a-card title="最近笔记" :bordered="false" class="mb-4">
            <a-table
              :dataSource="recentNotes"
              :columns="noteColumns"
              :pagination="false"
              size="small"
              row-key="id"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'noteStatus'">
                  <a-tag :color="statusColor(record.noteStatus)">{{ statusText(record.noteStatus) }}</a-tag>
                </template>
                <template v-if="column.key === 'createTime'">
                  {{ record.createTime?.substring(0, 10) }}
                </template>
              </template>
            </a-table>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script lang="ts" setup>
  import { ref, onMounted } from 'vue';
  import { Statistic as AStatistic } from 'ant-design-vue';
  import { Icon } from '/@/components/Icon';
  import Pie from '/@/components/chart/Pie.vue';
  import RankList from '/@/components/chart/RankList.vue';
  import { defHttp } from '/@/utils/http/axios';
  import { useUserStoreWithOut } from '/@/store/modules/user';

  // ── 类型
  interface NoteRecord {
    id: string;
    noteTitle?: string;
    noteStatus?: number;
    createTime?: string;
  }
  interface CourseRecord {
    id: string;
    courseName?: string;
  }
  interface TaskRecord {
    taskStatus?: number;
  }
  interface PageResult<T> {
    records: T[];
    total: number;
  }

  // ── 状态
  const loading = ref(true);
  const userStore = useUserStoreWithOut();

  const statCards = ref([
    { title: '笔记总数', value: 0, icon: 'ant-design:file-text-outlined', color: '#1890ff' },
    { title: '本月新增', value: 0, icon: 'ant-design:plus-circle-outlined', color: '#52c41a' },
    { title: '课程总数', value: 0, icon: 'ant-design:book-outlined', color: '#faad14' },
    { title: '教学任务数', value: 0, icon: 'ant-design:team-outlined', color: '#f5222d' },
  ]);

  const taskChartData = ref<{ name: string; value: number }[]>([]);
  const courseRankList = ref<{ name: string; total: number }[]>([]);
  const recentNotes = ref<NoteRecord[]>([]);

  const noteColumns = [
    { title: '笔记标题', dataIndex: 'noteTitle', key: 'noteTitle', ellipsis: true },
    { title: '状态', dataIndex: 'noteStatus', key: 'noteStatus', width: 90 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 110 },
  ];

  // ── 工具
  function hasRole(target: string): boolean {
    const roleCode: string = userStore.getUserInfo?.roleCode || '';
    return roleCode.split(',').map((r) => r.trim()).includes(target);
  }

  function statusColor(status?: number): string {
    const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'error' };
    return map[status ?? 0] ?? 'default';
  }

  function statusText(status?: number): string {
    const map: Record<number, string> = { 0: '草稿', 1: '生成中', 2: '已完成', 3: '失败' };
    return map[status ?? 0] ?? '未知';
  }

  // ── 数据加载
  async function loadData() {
    loading.value = true;
    try {
      const isStudent = hasRole('student');
      const username = userStore.getUserInfo?.username || '';

      const noteParams: Record<string, unknown> = {
        pageNo: 1, pageSize: 10, column: 'createTime', order: 'desc',
      };
      if (isStudent && username) noteParams.createBy = username;

      const [noteRes, courseRes, teachingRes, taskRes] = await Promise.allSettled([
        defHttp.get<PageResult<NoteRecord>>({ url: '/ainote/note/list', params: noteParams }),
        defHttp.get<PageResult<CourseRecord>>({ url: '/teaching/course/list', params: { pageNo: 1, pageSize: 10 } }),
        defHttp.get<PageResult<unknown>>({ url: '/teaching/assignment/list', params: { pageNo: 1, pageSize: 1 } }),
        defHttp.get<PageResult<TaskRecord>>({ url: '/ainote/task/list', params: { pageNo: 1, pageSize: 200 } }),
      ]);

      // 笔记统计
      if (noteRes.status === 'fulfilled' && noteRes.value) {
        const data = noteRes.value;
        statCards.value[0].value = data.total ?? 0;
        recentNotes.value = data.records ?? [];
        const thisMonth = new Date().toISOString().substring(0, 7);
        statCards.value[1].value = (data.records ?? []).filter(
          (n) => n.createTime?.startsWith(thisMonth),
        ).length;
      }

      // 课程统计 + 排行
      if (courseRes.status === 'fulfilled' && courseRes.value) {
        const data = courseRes.value;
        statCards.value[2].value = data.total ?? 0;
        courseRankList.value = (data.records ?? []).map((c) => ({
          name: c.courseName ?? '未命名课程',
          total: 0,
        }));
      }

      // 教学任务统计
      if (teachingRes.status === 'fulfilled' && teachingRes.value) {
        statCards.value[3].value = (teachingRes.value as PageResult<unknown>).total ?? 0;
      }

      // AI 任务状态
      if (taskRes.status === 'fulfilled' && taskRes.value) {
        const tasks: TaskRecord[] = (taskRes.value as PageResult<TaskRecord>).records ?? [];
        const counts = [0, 0, 0, 0];
        tasks.forEach((t) => {
          const s = t.taskStatus ?? 0;
          if (s >= 0 && s <= 3) counts[s]++;
        });
        taskChartData.value = [
          { name: '待处理', value: counts[0] },
          { name: '处理中', value: counts[1] },
          { name: '已完成', value: counts[2] },
          { name: '失败',   value: counts[3] },
        ];
      }
    } catch {
      // 静默降级，保持 0 值展示
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => { loadData(); });
</script>

<style lang="less" scoped>
  .education-dashboard {
    .ant-card {
      border-radius: 8px;
    }
  }
</style>
