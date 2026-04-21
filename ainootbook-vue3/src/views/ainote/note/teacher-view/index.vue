<template>
  <PageWrapper title="笔记统计分析">
    <!-- 筛选栏 -->
    <a-card :bordered="false" class="stat-filter-card">
      <a-form layout="inline" :model="filterForm">
        <a-form-item label="课程">
          <a-select
            v-model:value="filterForm.courseId"
            placeholder="请选择课程"
            allow-clear
            style="width: 200px"
            :options="courseOptions"
            @change="onCourseChange"
          />
        </a-form-item>
        <a-form-item label="章节">
          <a-select
            v-model:value="filterForm.chapterId"
            placeholder="请选择章节"
            allow-clear
            style="width: 200px"
            :options="chapterOptions"
            @change="onFilterChange"
          />
        </a-form-item>
        <a-form-item label="学期">
          <a-select
            v-model:value="filterForm.semester"
            placeholder="请选择学期"
            allow-clear
            style="width: 160px"
            :options="semesterOptions"
            @change="onFilterChange"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="onFilterChange">查询</a-button>
          <a-button style="margin-left: 8px" @click="onReset">重置</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 概览数字卡片 -->
    <a-row :gutter="16" class="stat-overview">
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card stat-card--blue">
          <a-statistic title="笔记总数" :value="overview.totalNotes" suffix="篇">
            <template #prefix>
              <FileTextOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card stat-card--green">
          <a-statistic title="AI处理完成" :value="overview.completedNotes" suffix="篇">
            <template #prefix>
              <CheckCircleOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card stat-card--orange">
          <a-statistic title="素材上传数" :value="overview.totalMaterials" suffix="个">
            <template #prefix>
              <UploadOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card stat-card--purple">
          <a-statistic title="参与学生数" :value="overview.studentCount" suffix="人">
            <template #prefix>
              <TeamOutlined />
            </template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="16" class="stat-charts">
      <!-- 各章节笔记上传数量柱状图 -->
      <a-col :xs="24" :lg="12">
        <a-card title="各章节笔记上传数量" :bordered="false" class="stat-chart-card">
          <div ref="uploadBarRef" class="chart-container" />
        </a-card>
      </a-col>

      <!-- AI处理完成率折线图 -->
      <a-col :xs="24" :lg="12">
        <a-card title="AI处理完成率（按章节）" :bordered="false" class="stat-chart-card">
          <div ref="completionLineRef" class="chart-container" />
        </a-card>
      </a-col>

      <!-- 素材类型分布饼图 -->
      <a-col :xs="24" :lg="12">
        <a-card title="素材类型分布" :bordered="false" class="stat-chart-card">
          <div ref="materialPieRef" class="chart-container" />
        </a-card>
      </a-col>

      <!-- 高频关键词词云/排行 -->
      <a-col :xs="24" :lg="12">
        <a-card title="高频关键词 TOP 20" :bordered="false" class="stat-chart-card">
          <div ref="keywordBarRef" class="chart-container" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 章节明细表格 -->
    <a-card title="章节笔记明细" :bordered="false" class="stat-table-card">
      <a-table
        :columns="tableColumns"
        :data-source="tableData"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :loading="tableLoading"
        row-key="chapterId"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'completionRate'">
            <a-progress
              :percent="record.completionRate"
              :stroke-color="record.completionRate >= 80 ? '#52c41a' : record.completionRate >= 50 ? '#faad14' : '#ff4d4f'"
              size="small"
            />
          </template>
          <template v-if="column.key === 'noteStatus'">
            <a-tag :color="record.pendingCount > 0 ? 'orange' : 'green'">
              {{ record.pendingCount > 0 ? `${record.pendingCount}篇待处理` : '全部完成' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </PageWrapper>
</template>

<script lang="ts" setup>
  import { ref, reactive, onMounted, onUnmounted } from 'vue';
  import { Statistic as AStatistic } from 'ant-design-vue';
  import { PageWrapper } from '/@/components/Page';
  import {
    FileTextOutlined,
    CheckCircleOutlined,
    UploadOutlined,
    TeamOutlined,
  } from '@ant-design/icons-vue';
  import * as echarts from 'echarts';
  import { getChapterTreeList } from '/@/api/ainote/chapter.api';
  import { getCourseStatistics, getTopKeywords, getMaterialTypeStats } from '/@/api/ainote/statistics.api';
  import { getTeachingList } from '/@/api/ainote/teaching.api';
  import { useMessage } from '/@/hooks/web/useMessage';

  const { createMessage } = useMessage();

  // ─── 筛选表单 ───────────────────────────────────────────────
  const filterForm = reactive({
    courseId: undefined as string | undefined,
    chapterId: undefined as string | undefined,
    semester: undefined as string | undefined,
  });

  // 动态选项
  const courseOptions = ref<{ label: string; value: string }[]>([]);
  const chapterOptions = ref<{ label: string; value: string }[]>([]);
  const semesterOptions = ref([
    { label: '2025-2026 第一学期', value: '2025-1' },
    { label: '2025-2026 第二学期', value: '2025-2' },
    { label: '2024-2025 第一学期', value: '2024-1' },
    { label: '2024-2025 第二学期', value: '2024-2' },
  ]);

  // ─── 概览数据 ────────────────────────────────────────────────
  const overview = reactive({
    totalNotes: 0,
    completedNotes: 0,
    totalMaterials: 0,
    studentCount: 0,
  });

  // ─── 表格 ────────────────────────────────────────────────────
  const tableLoading = ref(false);
  const tableColumns = [
    { title: '章节名称', dataIndex: 'chapterName', key: 'chapterName', width: 200 },
    { title: '笔记上传数', dataIndex: 'uploadCount', key: 'uploadCount', align: 'center' as const },
    { title: 'AI处理完成数', dataIndex: 'completedCount', key: 'completedCount', align: 'center' as const },
    { title: '完成率', dataIndex: 'completionRate', key: 'completionRate', width: 180 },
    { title: '状态', dataIndex: 'noteStatus', key: 'noteStatus', align: 'center' as const },
    { title: '高频关键词', dataIndex: 'topKeywords', key: 'topKeywords' },
  ];
  const tableData = ref<any[]>([]);

  // ─── ECharts 实例 ────────────────────────────────────────────
  const uploadBarRef = ref<HTMLDivElement>();
  const completionLineRef = ref<HTMLDivElement>();
  const materialPieRef = ref<HTMLDivElement>();
  const keywordBarRef = ref<HTMLDivElement>();

  let uploadBarChart: echarts.ECharts | null = null;
  let completionLineChart: echarts.ECharts | null = null;
  let materialPieChart: echarts.ECharts | null = null;
  let keywordBarChart: echarts.ECharts | null = null;

  // ─── 数据加载 ────────────────────────────────────────────────
  async function loadCourses() {
    try {
      // 从教学任务接口取，后端 applyDataPermission 自动过滤当前教师的课程
      const res = await getTeachingList({ pageSize: 200, status: 1 });
      const records = res?.records || [];
      // 按 courseId 去重，提取课程选项
      const seen = new Set<string>();
      courseOptions.value = records
        .filter((t: any) => t.courseId && t.courseName && !seen.has(t.courseId) && seen.add(t.courseId))
        .map((t: any) => ({ label: t.courseName, value: t.courseId }));
    } catch (e) {
      console.error('加载课程列表失败', e);
    }
  }

  async function loadChapters(courseId: string) {
    if (!courseId) {
      chapterOptions.value = [];
      return;
    }
    try {
      const res = await getChapterTreeList({ courseId });
      // 扁平化章节树（只取一级，如需多级可递归）
      const flattenChapters = (nodes: any[]): any[] => {
        const result: any[] = [];
        nodes.forEach((node) => {
          result.push({ label: node.chapterName, value: node.id });
          if (node.children && node.children.length > 0) {
            result.push(...flattenChapters(node.children));
          }
        });
        return result;
      };
      chapterOptions.value = flattenChapters(res || []);
    } catch (e) {
      console.error('加载章节列表失败', e);
      chapterOptions.value = [];
    }
  }

  async function fetchOverview() {
    if (!filterForm.courseId) return;
    try {
      const res = await getCourseStatistics({
        courseId: filterForm.courseId,
        semester: filterForm.semester,
        chapterId: filterForm.chapterId,
      });
      overview.totalNotes = res.totalNotes || 0;
      overview.completedNotes = res.completedNotes || 0;
      overview.totalMaterials = res.totalMaterials || 0;
      overview.studentCount = res.studentCount || 0;
      tableData.value = res.chapterStats || [];

      // 更新图表
      updateCharts(res.chapterStats || []);
    } catch (e) {
      console.error('加载统计数据失败', e);
      createMessage.error('加载统计数据失败');
    }
  }

  async function fetchKeywords() {
    if (!filterForm.courseId) return [];
    try {
      const res = await getTopKeywords({
        courseId: filterForm.courseId,
        topN: 20,
        semester: filterForm.semester,
      });
      return res || [];
    } catch (e) {
      console.error('加载关键词失败', e);
      return [];
    }
  }

  async function fetchMaterials() {
    if (!filterForm.courseId) return [];
    try {
      const res = await getMaterialTypeStats({
        courseId: filterForm.courseId,
        semester: filterForm.semester,
      });
      return res || [];
    } catch (e) {
      console.error('加载素材统计失败', e);
      return [];
    }
  }

  function updateCharts(chapterStats: any[]) {
    const chapterNames = chapterStats.map((c) => c.chapterName || '未命名章节');
    const uploadCounts = chapterStats.map((c) => c.uploadCount || 0);
    const completedCounts = chapterStats.map((c) => c.completedCount || 0);
    const completionRates = chapterStats.map((c) => c.completionRate || 0);

    // 更新上传数柱状图
    if (uploadBarChart) {
      uploadBarChart.setOption({
        xAxis: { data: chapterNames },
        series: [
          { data: uploadCounts },
          { data: completedCounts },
        ],
      });
    }

    // 更新完成率折线图
    if (completionLineChart) {
      completionLineChart.setOption({
        xAxis: { data: chapterNames },
        series: [{ data: completionRates }],
      });
    }
  }

  async function updateKeywordChart() {
    const keywords = await fetchKeywords();
    if (keywordBarChart && keywords.length > 0) {
      const keywordNames = keywords.map((k: any) => k.keyword);
      const keywordCounts = keywords.map((k: any) => k.frequency);
      keywordBarChart.setOption({
        yAxis: { data: keywordNames.slice().reverse() },
        series: [{ data: keywordCounts.slice().reverse() }],
      });
    }
  }

  async function updateMaterialChart() {
    const materials = await fetchMaterials();
    if (materialPieChart && materials.length > 0) {
      const pieData = materials.map((m: any) => ({
        name: m.materialType || '未知类型',
        value: m.count || 0,
      }));
      materialPieChart.setOption({
        series: [{ data: pieData }],
      });
    }
  }

  // ─── 图表初始化 ──────────────────────────────────────────────
  function initUploadBar() {
    if (!uploadBarRef.value) return;
    uploadBarChart = echarts.init(uploadBarRef.value);
    uploadBarChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['上传数', 'AI完成数'] },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: [], axisLabel: { interval: 0, rotate: 15 } },
      yAxis: { type: 'value', name: '篇数' },
      series: [
        { name: '上传数', type: 'bar', data: [], itemStyle: { color: '#1677ff' }, barMaxWidth: 40 },
        { name: 'AI完成数', type: 'bar', data: [], itemStyle: { color: '#52c41a' }, barMaxWidth: 40 },
      ],
    });
  }

  function initCompletionLine() {
    if (!completionLineRef.value) return;
    completionLineChart = echarts.init(completionLineRef.value);
    completionLineChart.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}<br/>完成率: {c}%' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: [], axisLabel: { interval: 0, rotate: 15 } },
      yAxis: { type: 'value', name: '完成率(%)', min: 0, max: 100 },
      series: [
        {
          name: '完成率',
          type: 'line',
          data: [],
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          itemStyle: { color: '#722ed1' },
          areaStyle: { color: 'rgba(114,46,209,0.1)' },
          markLine: { data: [{ type: 'average', name: '平均值' }] },
        },
      ],
    });
  }

  function initMaterialPie() {
    if (!materialPieRef.value) return;
    materialPieChart = echarts.init(materialPieRef.value);
    materialPieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c}个 ({d}%)' },
      legend: { orient: 'vertical', left: 'left' },
      series: [
        {
          name: '素材类型',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
          labelLine: { show: false },
          data: [],
        },
      ],
    });
  }

  function initKeywordBar() {
    if (!keywordBarRef.value) return;
    keywordBarChart = echarts.init(keywordBarRef.value);
    keywordBarChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '15%', right: '4%', bottom: '3%', top: '3%', containLabel: true },
      xAxis: { type: 'value', name: '出现次数' },
      yAxis: { type: 'category', data: [], axisLabel: { fontSize: 12 } },
      series: [
        {
          type: 'bar',
          data: [],
          barMaxWidth: 20,
          itemStyle: {
            color: (params: { dataIndex: number }) => {
              const colors = ['#1677ff', '#36cfc9', '#73d13d', '#ffc53d', '#ff7a45'];
              return colors[params.dataIndex % colors.length];
            },
          },
          label: { show: true, position: 'right', fontSize: 11 },
        },
      ],
    });
  }

  async function onFilterChange() {
    if (!filterForm.courseId) {
      createMessage.warning('请先选择课程');
      return;
    }

    uploadBarChart?.showLoading();
    completionLineChart?.showLoading();
    materialPieChart?.showLoading();
    keywordBarChart?.showLoading();
    tableLoading.value = true;

    try {
      await Promise.all([
        fetchOverview(),
        updateKeywordChart(),
        updateMaterialChart(),
      ]);
    } finally {
      uploadBarChart?.hideLoading();
      completionLineChart?.hideLoading();
      materialPieChart?.hideLoading();
      keywordBarChart?.hideLoading();
      tableLoading.value = false;
    }
  }

  async function onCourseChange(courseId: string) {
    filterForm.chapterId = undefined;
    chapterOptions.value = [];
    if (courseId) {
      await loadChapters(courseId);
    }
  }

  function onReset() {
    filterForm.courseId = undefined;
    filterForm.chapterId = undefined;
    filterForm.semester = undefined;
    chapterOptions.value = [];
    overview.totalNotes = 0;
    overview.completedNotes = 0;
    overview.totalMaterials = 0;
    overview.studentCount = 0;
    tableData.value = [];

    // 清空图表
    uploadBarChart?.setOption({ xAxis: { data: [] }, series: [{ data: [] }, { data: [] }] });
    completionLineChart?.setOption({ xAxis: { data: [] }, series: [{ data: [] }] });
    materialPieChart?.setOption({ series: [{ data: [] }] });
    keywordBarChart?.setOption({ yAxis: { data: [] }, series: [{ data: [] }] });
  }

  function handleResize() {
    uploadBarChart?.resize();
    completionLineChart?.resize();
    materialPieChart?.resize();
    keywordBarChart?.resize();
  }

  onMounted(() => {
    loadCourses();
    initUploadBar();
    initCompletionLine();
    initMaterialPie();
    initKeywordBar();
    window.addEventListener('resize', handleResize);
  });

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize);
    uploadBarChart?.dispose();
    completionLineChart?.dispose();
    materialPieChart?.dispose();
    keywordBarChart?.dispose();
  });
</script>

<style lang="less" scoped>
  .stat-filter-card {
    margin-bottom: 16px;
  }

  .stat-overview {
    margin-bottom: 16px;

    .stat-card {
      border-radius: 8px;
      overflow: hidden;

      &--blue { border-top: 3px solid #1677ff; }
      &--green { border-top: 3px solid #52c41a; }
      &--orange { border-top: 3px solid #faad14; }
      &--purple { border-top: 3px solid #722ed1; }
    }
  }

  .stat-charts {
    margin-bottom: 16px;

    .stat-chart-card {
      .chart-container {
        height: 300px;
        width: 100%;
      }
    }
  }

  .stat-table-card {
    margin-bottom: 16px;
  }
</style>
