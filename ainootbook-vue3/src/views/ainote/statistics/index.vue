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
  import {
    FileTextOutlined,
    CheckCircleOutlined,
    UploadOutlined,
    TeamOutlined,
  } from '@ant-design/icons-vue';
  import * as echarts from 'echarts';

  // ─── 筛选表单 ───────────────────────────────────────────────
  const filterForm = reactive({
    courseId: undefined as string | undefined,
    chapterId: undefined as string | undefined,
    semester: undefined as string | undefined,
  });

  // 静态占位选项（待对接接口后替换）
  const courseOptions = ref([
    { label: '人工智能导论', value: 'c001' },
    { label: '机器学习基础', value: 'c002' },
    { label: '深度学习实践', value: 'c003' },
  ]);
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

  // ─── 概览数据（静态占位）────────────────────────────────────
  const overview = reactive({
    totalNotes: 128,
    completedNotes: 112,
    totalMaterials: 256,
    studentCount: 43,
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
  const tableData = ref([
    { chapterId: 'ch001', chapterName: '第一章 绪论', uploadCount: 38, completedCount: 36, completionRate: 95, pendingCount: 2, topKeywords: '人工智能, 机器学习, 深度学习' },
    { chapterId: 'ch002', chapterName: '第二章 监督学习', uploadCount: 35, completedCount: 30, completionRate: 86, pendingCount: 5, topKeywords: '分类, 回归, 决策树' },
    { chapterId: 'ch003', chapterName: '第三章 神经网络', uploadCount: 32, completedCount: 28, completionRate: 88, pendingCount: 4, topKeywords: '激活函数, 反向传播, 梯度' },
    { chapterId: 'ch004', chapterName: '第四章 卷积网络', uploadCount: 23, completedCount: 18, completionRate: 78, pendingCount: 5, topKeywords: '卷积, 池化, 特征图' },
  ]);

  // ─── ECharts 实例 ────────────────────────────────────────────
  const uploadBarRef = ref<HTMLDivElement>();
  const completionLineRef = ref<HTMLDivElement>();
  const materialPieRef = ref<HTMLDivElement>();
  const keywordBarRef = ref<HTMLDivElement>();

  let uploadBarChart: echarts.ECharts | null = null;
  let completionLineChart: echarts.ECharts | null = null;
  let materialPieChart: echarts.ECharts | null = null;
  let keywordBarChart: echarts.ECharts | null = null;

  // 静态占位数据
  const chapterNames = ['第一章 绪论', '第二章 监督学习', '第三章 神经网络', '第四章 卷积网络'];
  const uploadCounts = [38, 35, 32, 23];
  const completedCounts = [36, 30, 28, 18];
  const completionRates = [95, 86, 88, 78];

  function initUploadBar() {
    if (!uploadBarRef.value) return;
    uploadBarChart = echarts.init(uploadBarRef.value);
    uploadBarChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['上传数', 'AI完成数'] },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: chapterNames, axisLabel: { interval: 0, rotate: 15 } },
      yAxis: { type: 'value', name: '篇数' },
      series: [
        { name: '上传数', type: 'bar', data: uploadCounts, itemStyle: { color: '#1677ff' }, barMaxWidth: 40 },
        { name: 'AI完成数', type: 'bar', data: completedCounts, itemStyle: { color: '#52c41a' }, barMaxWidth: 40 },
      ],
    });
  }

  function initCompletionLine() {
    if (!completionLineRef.value) return;
    completionLineChart = echarts.init(completionLineRef.value);
    completionLineChart.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}<br/>完成率: {c}%' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: chapterNames, axisLabel: { interval: 0, rotate: 15 } },
      yAxis: { type: 'value', name: '完成率(%)', min: 0, max: 100 },
      series: [
        {
          name: '完成率',
          type: 'line',
          data: completionRates,
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
          data: [
            { value: 98, name: '音频(MP3/WAV)', itemStyle: { color: '#1677ff' } },
            { value: 72, name: '文档(PDF/Word)', itemStyle: { color: '#52c41a' } },
            { value: 54, name: '图片(JPG/PNG)', itemStyle: { color: '#faad14' } },
            { value: 32, name: '视频(MP4)', itemStyle: { color: '#ff4d4f' } },
          ],
        },
      ],
    });
  }

  function initKeywordBar() {
    if (!keywordBarRef.value) return;
    keywordBarChart = echarts.init(keywordBarRef.value);
    const keywords = ['人工智能', '机器学习', '深度学习', '神经网络', '卷积', '激活函数', '反向传播', '梯度下降', '分类', '回归', '决策树', '随机森林', '特征提取', '过拟合', '正则化', '批归一化', '注意力机制', 'Transformer', '迁移学习', '强化学习'];
    const counts = [89, 76, 68, 62, 55, 51, 48, 45, 42, 39, 36, 33, 30, 28, 25, 23, 21, 19, 17, 15];
    keywordBarChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '15%', right: '4%', bottom: '3%', top: '3%', containLabel: true },
      xAxis: { type: 'value', name: '出现次数' },
      yAxis: { type: 'category', data: keywords.slice().reverse(), axisLabel: { fontSize: 12 } },
      series: [
        {
          type: 'bar',
          data: counts.slice().reverse(),
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

  function onFilterChange() {
    // TODO: 对接接口后在此处发起请求，当前使用静态数据
    console.log('筛选条件变更:', filterForm);
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
