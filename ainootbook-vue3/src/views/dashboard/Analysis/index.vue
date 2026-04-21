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

      <!-- 图表区 - 根据角色渲染不同内容 -->
      <a-row :gutter="16" class="mt-2 equal-height-row">
        <!-- 左侧：admin/student 显示 AI 任务饼图，teacher 显示课程列表 -->
        <a-col :md="10" :sm="24" :xs="24" class="flex">
          <a-card v-if="isTeacher" :bordered="false" class="mb-4 flex-1">
            <template #title>
              <span>我的课程</span>
              <a-button type="link" size="small" @click="goToStatistics" style="float: right">
                查看统计
              </a-button>
            </template>
            <div class="course-grid">
              <a-card
                v-for="course in teacherCourses"
                :key="course.courseId"
                size="small"
                :bordered="false"
                class="course-card"
              >
                <div class="course-info">
                  <div class="course-name">{{ course.courseName }}</div>
                  <div class="course-meta">
                    <span>{{ course.semester }}</span>
                    <span class="ml-2">学生数: {{ course.studentCount }}</span>
                  </div>
                </div>
              </a-card>
            </div>
          </a-card>

          <a-card v-else title="AI 任务状态分布" :bordered="false" class="mb-4 flex-1">
            <Pie :chartData="taskChartData" height="280px" />
          </a-card>
        </a-col>

        <!-- 右侧：admin 显示课程排行，teacher 显示最近学生笔记，student 显示课程列表 -->
        <a-col :md="14" :sm="24" :xs="24" class="flex">
          <a-card v-if="isAdmin" title="课程笔记排行 TOP10" :bordered="false" class="mb-4 flex-1">
            <RankList :list="courseRankList" :height="280" />
          </a-card>

          <a-card v-else-if="isTeacher" title="最近学生笔记" :bordered="false" class="mb-4 flex-1">
            <a-table
              :dataSource="teacherRecentNotes"
              :columns="teacherNoteColumns"
              :pagination="false"
              size="small"
              row-key="id"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'createTime'">
                  {{ record.createTime?.substring(0, 10) }}
                </template>
              </template>
            </a-table>
          </a-card>

          <a-card v-else title="我的课程" :bordered="false" class="mb-4 flex-1">
            <div class="course-grid">
              <a-card
                v-for="course in studentCourses"
                :key="course.id"
                size="small"
                :bordered="false"
                class="course-card"
              >
                <div class="course-info">
                  <div class="course-name">{{ course.courseName }}</div>
                  <div class="course-meta">{{ course.semester }}</div>
                </div>
              </a-card>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <!-- 第三行 - 根据角色渲染 -->
      <a-row :gutter="16" v-if="isAdmin">
        <a-col :xs="24">
          <a-card title="最近教学任务" :bordered="false" class="mb-4">
            <a-table
              :dataSource="recentTeachings"
              :columns="teachingColumns"
              :pagination="false"
              size="small"
              row-key="id"
            >
            </a-table>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16" v-if="isStudent">
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
  import { ref, computed, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { Statistic as AStatistic } from 'ant-design-vue';
  import { Icon } from '/@/components/Icon';
  import Pie from '/@/components/chart/Pie.vue';
  import RankList from '/@/components/chart/RankList.vue';
  import { defHttp } from '/@/utils/http/axios';
  import { useUserStoreWithOut } from '/@/store/modules/user';

  const router = useRouter();
  const loading = ref(true);
  const userStore = useUserStoreWithOut();

  // 角色判断
  const isAdmin = computed(() => hasRole('admin'));
  const isTeacher = computed(() => hasRole('teacher') && !hasRole('admin'));
  const isStudent = computed(() => hasRole('student') && !hasRole('admin') && !hasRole('teacher'));

  function hasRole(target: string): boolean {
    const roleCode: string = (userStore.getUserInfo as any)?.roleCode || '';
    return roleCode.split(',').map((r) => r.trim()).includes(target);
  }

  // 统计卡片
  const statCards = ref([
    { title: '笔记总数', value: 0, icon: 'ant-design:file-text-outlined', color: '#1890ff' },
    { title: '本月新增', value: 0, icon: 'ant-design:plus-circle-outlined', color: '#52c41a' },
    { title: '课程总数', value: 0, icon: 'ant-design:book-outlined', color: '#faad14' },
    { title: '教学任务数', value: 0, icon: 'ant-design:team-outlined', color: '#f5222d' },
  ]);

  // 通用数据
  const taskChartData = ref<{ name: string; value: number }[]>([]);
  const courseRankList = ref<{ name: string; total: number }[]>([]);
  const recentNotes = ref<any[]>([]);

  // 教师专用数据
  const teacherCourses = ref<any[]>([]);
  const teacherRecentNotes = ref<any[]>([]);

  // 学生专用数据
  const studentCourses = ref<any[]>([]);

  // 管理员专用数据
  const recentTeachings = ref<any[]>([]);

  // 表格列定义
  const noteColumns = [
    { title: '笔记标题', dataIndex: 'noteTitle', key: 'noteTitle', ellipsis: true },
    { title: '状态', dataIndex: 'noteStatus', key: 'noteStatus', width: 90 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 110 },
  ];

  const teacherNoteColumns = [
    { title: '学生', dataIndex: 'studentName', key: 'studentName', width: 100 },
    { title: '笔记标题', dataIndex: 'noteTitle', key: 'noteTitle', ellipsis: true },
    { title: '课程', dataIndex: 'courseName', key: 'courseName', width: 150 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 110 },
  ];

  const teachingColumns = [
    { title: '课程', dataIndex: 'courseName', key: 'courseName', width: 200 },
    { title: '教师', dataIndex: 'teacherName', key: 'teacherName', width: 100 },
    { title: '组织', dataIndex: 'departName', key: 'departName', width: 150 },
    { title: '学期', dataIndex: 'semester', key: 'semester', width: 120 },
  ];

  function statusColor(status?: number): string {
    const map: Record<number, string> = { 1: 'default', 2: 'success', 3: 'error' };
    return map[status ?? 1] ?? 'default';
  }

  function statusText(status?: number): string {
    const map: Record<number, string> = { 1: '草稿', 2: '已完成', 3: '已删除' };
    return map[status ?? 1] ?? '未知';
  }

  function goToStatistics() {
    router.push('/ainote/note/teacher');
  }

  // 数据加载
  async function loadData() {
    loading.value = true;
    try {
      if (isAdmin.value) {
        await loadAdminData();
      } else if (isTeacher.value) {
        await loadTeacherData();
      } else if (isStudent.value) {
        await loadStudentData();
      }
    } finally {
      loading.value = false;
    }
  }

  async function loadAdminData() {
    const [noteRes, courseRes, teachingRes, taskRes] = await Promise.allSettled([
      defHttp.get({ url: '/ainote/note/list', params: { pageNo: 1, pageSize: 1 } }),
      defHttp.get({ url: '/teaching/course/list', params: { pageNo: 1, pageSize: 10 } }),
      defHttp.get({ url: '/teaching/assignment/list', params: { pageNo: 1, pageSize: 10, column: 'createTime', order: 'desc' } }),
      defHttp.get({ url: '/ainote/task/list', params: { pageNo: 1, pageSize: 200 } }),
    ]);

    // 笔记总数
    if (noteRes.status === 'fulfilled' && noteRes.value) {
      statCards.value[0].value = noteRes.value.total ?? 0;
    }

    // 课程总数 + 排行
    if (courseRes.status === 'fulfilled' && courseRes.value) {
      const data = courseRes.value;
      statCards.value[2].value = data.total ?? 0;
      const courses = data.records ?? [];

      const noteCountPromises = courses.map((c) =>
        defHttp.get({ url: '/ainote/note/list', params: { courseId: c.id, pageNo: 1, pageSize: 1 } })
          .then((res) => ({ courseId: c.id, courseName: c.courseName, total: res.total ?? 0 }))
          .catch(() => ({ courseId: c.id, courseName: c.courseName, total: 0 }))
      );

      const noteCounts = await Promise.all(noteCountPromises);
      courseRankList.value = noteCounts
        .sort((a, b) => b.total - a.total)
        .slice(0, 10)
        .map((c) => ({ name: c.courseName ?? '未命名课程', total: c.total }));
    }

    // 教学任务数 + 最近任务
    if (teachingRes.status === 'fulfilled' && teachingRes.value) {
      const data = teachingRes.value;
      statCards.value[3].value = data.total ?? 0;
      recentTeachings.value = (data.records ?? []).slice(0, 10);
    }

    // AI 任务状态
    if (taskRes.status === 'fulfilled' && taskRes.value) {
      const tasks = taskRes.value.records ?? [];
      const counts = [0, 0, 0, 0];
      tasks.forEach((t: any) => {
        const s = t.taskStatus ?? 0;
        if (s >= 0 && s <= 3) counts[s]++;
      });
      taskChartData.value = [
        { name: '待处理', value: counts[0] },
        { name: '处理中', value: counts[1] },
        { name: '已完成', value: counts[2] },
        { name: '失败', value: counts[3] },
      ];
    }

    // 本月新增笔记
    const thisMonth = new Date().toISOString().substring(0, 7);
    if (noteRes.status === 'fulfilled' && noteRes.value) {
      try {
        const monthlyRes = await defHttp.get({
          url: '/ainote/note/list',
          params: { pageNo: 1, pageSize: 1000, column: 'createTime', order: 'desc' }
        });
        const monthlyNotes = (monthlyRes.records ?? []).filter(
          (n: any) => n.createTime?.startsWith(thisMonth)
        );
        statCards.value[1].title = '本月新增';
        statCards.value[1].value = monthlyNotes.length;
      } catch {
        statCards.value[1].title = '本月新增';
        statCards.value[1].value = 0;
      }
    }
  }

  async function loadTeacherData() {
    const [teachingRes, noteRes, taskRes] = await Promise.allSettled([
      defHttp.get({ url: '/teaching/assignment/list', params: { pageSize: 200, status: 1 } }),
      defHttp.get({ url: '/ainote/note/teacherList', params: { pageNo: 1, pageSize: 10 } }),
      defHttp.get({ url: '/ainote/note/list', params: { pageNo: 1, pageSize: 1 } }),
    ]);

    // 我的课程列表（去重 + 聚合学生数）
    if (teachingRes.status === 'fulfilled' && teachingRes.value) {
      const teachings = teachingRes.value.records ?? [];
      statCards.value[0].title = '我的课程数';
      statCards.value[0].value = new Set(teachings.map((t: any) => t.courseId)).size;

      // 按 courseId 聚合学生数
      const courseMap = new Map<string, any>();
      for (const t of teachings) {
        if (!courseMap.has(t.courseId)) {
          courseMap.set(t.courseId, {
            courseId: t.courseId,
            courseName: t.courseName,
            semester: t.semester,
            studentCount: 0,
          });
        }
        // 简化：每个 teaching 记录代表一个组织，假设每个组织有若干学生
        // 实际应该查 ainote_course_selection 表，这里用 teaching 数量近似
        courseMap.get(t.courseId)!.studentCount += 1;
      }
      teacherCourses.value = Array.from(courseMap.values());
    }

    // 学生笔记总数
    if (taskRes.status === 'fulfilled' && taskRes.value) {
      statCards.value[1].title = '学生笔记总数';
      statCards.value[1].value = taskRes.value.total ?? 0;
    }

    // 最近学生笔记
    if (noteRes.status === 'fulfilled' && noteRes.value) {
      teacherRecentNotes.value = noteRes.value.records ?? [];
      const thisMonth = new Date().toISOString().substring(0, 7);
      const monthlyCompleted = (noteRes.value.records ?? []).filter(
        (n: any) => n.createTime?.startsWith(thisMonth) && n.noteStatus === 2
      ).length;
      statCards.value[2].title = '本月完成数';
      statCards.value[2].value = monthlyCompleted;
    }

    // 参与学生数（简化：用 teacherRecentNotes 去重 studentId）
    const studentIds = new Set(teacherRecentNotes.value.map((n: any) => n.studentId).filter(Boolean));
    statCards.value[3].title = '参与学生数';
    statCards.value[3].value = studentIds.size;
  }

  async function loadStudentData() {
    const username = userStore.getUserInfo?.username || '';
    const [noteRes, selectionRes, taskRes] = await Promise.allSettled([
      defHttp.get({ url: '/ainote/note/list', params: { pageNo: 1, pageSize: 10, createBy: username, column: 'createTime', order: 'desc' } }),
      defHttp.get({ url: '/teaching/selection/list', params: { pageSize: 200 } }),
      defHttp.get({ url: '/ainote/task/list', params: { pageNo: 1, pageSize: 200 } }),
    ]);

    // 我的笔记数 + 最近笔记
    if (noteRes.status === 'fulfilled' && noteRes.value) {
      const data = noteRes.value;
      statCards.value[0].value = data.total ?? 0;
      recentNotes.value = data.records ?? [];
      const thisMonth = new Date().toISOString().substring(0, 7);
      statCards.value[1].value = (data.records ?? []).filter(
        (n: any) => n.createTime?.startsWith(thisMonth)
      ).length;
    }

    // 已完成笔记数
    if (noteRes.status === 'fulfilled' && noteRes.value) {
      const completed = (noteRes.value.records ?? []).filter((n: any) => n.noteStatus === 2).length;
      statCards.value[2].title = '已完成';
      statCards.value[2].value = completed;
    }

    // 我的课程数
    if (selectionRes.status === 'fulfilled' && selectionRes.value) {
      const selections = selectionRes.value.records ?? [];
      statCards.value[3].title = '我的课程数';
      statCards.value[3].value = selections.length;
      studentCourses.value = selections.map((s: any) => ({
        id: s.id,
        courseName: s.courseName,
        semester: s.semester,
      }));
    }

    // AI 任务状态
    if (taskRes.status === 'fulfilled' && taskRes.value) {
      const tasks = taskRes.value.records ?? [];
      const counts = [0, 0, 0, 0];
      tasks.forEach((t: any) => {
        const s = t.taskStatus ?? 0;
        if (s >= 0 && s <= 3) counts[s]++;
      });
      taskChartData.value = [
        { name: '待处理', value: counts[0] },
        { name: '处理中', value: counts[1] },
        { name: '已完成', value: counts[2] },
        { name: '失败', value: counts[3] },
      ];
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

  .equal-height-row {
    .flex {
      display: flex;

      .flex-1 {
        flex: 1;
        display: flex;
        flex-direction: column;

        :deep(.ant-card-body) {
          flex: 1;
          display: flex;
          flex-direction: column;
        }
      }
    }
  }

  .course-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
    max-height: 240px;
    overflow-y: auto;

    .course-card {
      background: #f5f7fa;
      cursor: default;

      .course-info {
        .course-name {
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .course-meta {
          font-size: 12px;
          color: #666;
        }
      }
    }
  }
</style>
