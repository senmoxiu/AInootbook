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
            @change="onFilterChange"
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
  import { PageWrapper } from '/@/components/Page';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getCourseList } from '/@/api/ainote/course.api';
  import { getCourseStatistics, getTopKeywords, getMaterialTypeStats } from '/@/api/ainote/statistics.api';
  import {
    FileTextOutlined,
    CheckCircleOutlined,
    UploadOutlined,
    TeamOutlined,
  } from '@ant-design/icons-vue';
  import * as echarts from 'echarts';

  const { createMessage } = useMessage();

  // ─── 筛选表单 ───────────────────────────────────────────────
  const filterForm = reactive({
    courseId: undefined as string | undefined,
    chapterId: undefined as string | undefined,
    semester: undefined as string | undefined,
  });

  // 课程选项（从接口加载）
  const courseOptions = ref<{ label: string; value: string }[]>([]);
  const chapterOptions = ref([
    { label: '第一章 绪论', value: 'ch001' },
    { label: '第二章 监督学习', value: 'ch002' },
    { label: '第三章 神经网络', value: 'ch003' },
    { label: '第四章 卷积网络', value: 'ch004' },
  ]);
  const semesterOptions = ref([
    { label: '2025-2026 第一学期', value: '2025-1' },
    { label: '2025-2026 第二学期', value: '2025-2' },
    { label: '2024-2025 第一学期', value: '2024-1' },
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

  async function loadCourses() {
    try {
      const res = await getCourseList({});
      courseOptions.value = (res?.records || []).map((c: any) => ({
        label: c.courseName,
        value: c.id,
      }));
    } catch (e) {
      console.error('加载课程列表失败', e);
    }
  }

  async function fetchOverview() {
    if (!filterForm.courseId) return;
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
  }

  async function fetchKeywords() {
    if (!filterForm.courseId) return;
    const res = await getTopKeywords({ courseId: filterForm.courseId, topN: 20, semester: filterForm.semester });
    return res || [];
  }

  async function fetchMaterials() {
    if (!filterForm.courseId) return;
    const res = await getMaterialTypeStats({ courseId: filterForm.courseId, semester: filterForm.semester });
    return res || [];
  }

  // ─── ECharts 实例 ────────────────────────────────────────────
  const uploadBarRef = ref<HTMLDivElement>();
  const completionLineRef = ref<HTMLDivElement>();
  const materialPieRef = ref<HTMLDivElement>();
  const keywordBarRef = ref<HTMLDivElement>();

  let uploadBarChart: echarts.ECharts | null = null;
  let completionLineChart: echarts.ECharts | null = null;
  let materialPieChart: echarts.ECharts | null = null;
  let keywordBarChart: echarts.ECharts | null = null;

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
    if (!filterForm.courseId) return;
    uploadBarChart?.showLoading();
    completionLineChart?.showLoading();
    materialPieChart?.showLoading();
    keywordBarChart?.showLoading();
    tableLoading.value = true;

    try {
      const [overviewRes, keywordRes, materialRes] = await Promise.allSettled([
        fetchOverview(),
        fetchKeywords(),
        fetchMaterials(),
      ]);

      if (overviewRes.status === 'rejected') {
        createMessage.error('统计数据加载失败，请重试');
      }

      if (overviewRes.status === 'fulfilled') {
        const uploadData = tableData.value.map((ch: any) => ch.uploadCount || 0);
        const completedData = tableData.value.map((ch: any) => ch.completedCount || 0);
        const completionData = tableData.value.map((ch: any) => ch.completionRate || 0);
        const chapterNames = tableData.value.map((ch: any) => ch.chapterName || '');

        uploadBarChart?.setOption({
          xAxis: { data: chapterNames },
          series: [
            { data: uploadData },
            { data: completedData },
          ],
        });

        completionLineChart?.setOption({
          xAxis: { data: chapterNames },
          series: [{ data: completionData }],
        });
      }

      if (materialRes.status === 'fulfilled') {
        const data = materialRes.value || [];
        const pieData = data.map((m: any) => ({ name: m.materialType, value: m.count }));
        materialPieChart?.setOption({
          series: [{ data: pieData }],
        });
      }

      if (keywordRes.status === 'fulfilled') {
        const data = keywordRes.value || [];
        const keywords = data.map((k: any) => k.keyword);
        const counts = data.map((k: any) => k.frequency);
        keywordBarChart?.setOption({
          yAxis: { data: keywords.slice().reverse() },
          series: [{ data: counts.slice().reverse() }],
        });
      }
    } finally {
      uploadBarChart?.hideLoading();
      completionLineChart?.hideLoading();
      materialPieChart?.hideLoading();
      keywordBarChart?.hideLoading();
      tableLoading.value = false;
    }
  }

  function onReset() {
    filterForm.courseId = undefined;
    filterForm.chapterId = undefined;
    filterForm.semester = undefined;
  }

  function handleResize() {
    uploadBarChart?.resize();
    completionLineChart?.resize();
    materialPieChart?.resize();
    keywordBarChart?.resize();
  }

  onMounted(() => {
    initUploadBar();
    initCompletionLine();
    initMaterialPie();
    initKeywordBar();
    window.addEventListener('resize', handleResize);

    loadCourses().then(() => {
      if (courseOptions.value.length > 0) {
        filterForm.courseId = courseOptions.value[0].value;
        onFilterChange();
      }
    });
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
