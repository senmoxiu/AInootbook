import { mount } from '@vue/test-utils';
import ConfigPage from '../index.vue';
import { defHttp } from '/@/utils/http/axios';
import { updateAiConfig } from '/@/api/ainote/aiConfig.api';

// Mocks
jest.mock('/@/utils/http/axios', () => ({
  defHttp: {
    get: jest.fn(),
    put: jest.fn(),
    post: jest.fn()
  }
}));

jest.mock('/@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    notification: { success: jest.fn(), error: jest.fn() },
    createMessage: { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
  })
}));

let formConfigs: any[] = [];
let mockValidateService = jest.fn().mockResolvedValue({});
let mockValidatePrompt = jest.fn().mockResolvedValue({});
let mockValidateFlow = jest.fn().mockResolvedValue({});

jest.mock('/@/components/Form/index', () => {
  return {
    BasicForm: {
      name: 'BasicForm',
      template: '<div class="mock-basic-form"></div>'
    },
    useForm: jest.fn((config: any) => {
      formConfigs.push(config);
      let valFn = jest.fn().mockResolvedValue({});
      if (config.schemas.some((s: any) => s.field === 'asrModelId')) {
        valFn = () => mockValidateService();
      } else if (config.schemas.some((s: any) => s.field === 'integratePromptKey')) {
        valFn = () => mockValidatePrompt();
      } else {
        valFn = () => mockValidateFlow();
      }
      return [
        jest.fn(),
        {
          setFieldsValue: jest.fn(),
          validate: valFn
        }
      ];
    })
  };
});

describe('ConfigPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    formConfigs = [];
    mockValidateService.mockReset().mockResolvedValue({ asrModelId: 'asr-1' });
    mockValidatePrompt.mockReset().mockResolvedValue({ maxSummaryLength: 100 });
    mockValidateFlow.mockReset().mockResolvedValue({ asrFailureMode: 'retry' });
    
    // Default GET response
    (defHttp.get as any).mockResolvedValue({
      id: '1',
      tenantId: 1000,
      asrModelId: 'asr-1',
      summaryFlowEnabled: 1
    });
  });

  const mountPage = () => {
    return mount(ConfigPage, {
      global: {
        stubs: {
          PageWrapper: { template: '<div><slot></slot></div>' },
          'a-card': { template: '<div><slot></slot></div>' },
          'a-tabs': { template: '<div><slot></slot></div>', props: ['activeKey'] },
          'a-tab-pane': { template: '<div><slot name="tab"></slot><slot></slot></div>', props: ['key'] },
          'a-badge': { template: '<span class="mock-badge" :data-dot="dot"><slot></slot></span>', props: ['dot'] },
          'a-button': { template: '<button class="mock-btn" @click="$emit(\'click\')"><slot></slot></button>' },
          BasicForm: { template: '<div class="basic-form"></div>' }
        }
      }
    });
  };

  // 10.1 测试 config/index.vue（3 Tab 渲染）
  it('renders 3 tabs correctly', () => {
    const wrapper = mountPage();
    const tabPanes = wrapper.findAllComponents({ name: 'a-tab-pane' });
    expect(tabPanes.length).toBe(3);
    
    const badges = wrapper.findAll('.mock-badge');
    expect(badges[0].text()).toBe('服务配置');
    expect(badges[1].text()).toBe('提示词配置');
    expect(badges[2].text()).toBe('处理流程配置');
  });

  // 10.2 测试模型下拉（动态获取 + 加载/错误状态）
  // NOTE: Current test only verifies API call trigger. Loading/error states and
  // other model dropdowns (OCR/Video/Summary/Keywords/Integrate) are not covered.
  // TODO: Add tests for loading indicator, error handling, and all 6 model dropdowns.
  it('configures ApiSelect for model selections correctly', () => {
    mountPage();
    const serviceSchema = formConfigs.find((c: any) => c.schemas.some((s: any) => s.field === 'asrModelId'))?.schemas;
    expect(serviceSchema).toBeDefined();

    const asrField = serviceSchema.find((s: any) => s.field === 'asrModelId');
    expect(asrField.component).toBe('ApiSelect');
    expect(asrField.componentProps.api).toBeDefined();

    // Trigger api
    asrField.componentProps.api();
    expect(defHttp.get).toHaveBeenCalledWith({
      url: '/airag/airagModel/list',
      params: { modelType: 'ASR', activateFlag: 1, pageSize: 50 }
    });
  });

  // 10.3 测试失败策略控件（下拉 + 步进器联动）
  it('links failure mode with retry limit dynamically', () => {
    mountPage();
    const flowSchema = formConfigs.find((c: any) => c.schemas.some((s: any) => s.field === 'asrFailureMode'))?.schemas;
    
    const asrFailureMode = flowSchema.find((s: any) => s.field === 'asrFailureMode');
    expect(asrFailureMode.component).toBe('Select');
    expect(asrFailureMode.defaultValue).toBe('skip');

    const asrRetryLimit = flowSchema.find((s: any) => s.field === 'asrRetryLimit');
    expect(asrRetryLimit.component).toBe('InputNumber');
    
    // Evaluate dynamic componentProps
    const propsFn = asrRetryLimit.componentProps;
    
    let dynamicProps = propsFn({ formModel: { asrFailureMode: 'skip' } });
    expect(dynamicProps.disabled).toBe(true);

    dynamicProps = propsFn({ formModel: { asrFailureMode: 'retry' } });
    expect(dynamicProps.disabled).toBe(false);
    expect(dynamicProps.min).toBe(0);
    expect(dynamicProps.max).toBe(10);
  });

  // 10.4 测试跨 Tab 全局验证 & 10.5 测试 Tab 错误徽章
  it('validates all tabs and shows error badge on failure', async () => {
    // Make prompt tab validation fail
    mockValidatePrompt.mockRejectedValue(new Error('Validation Failed'));

    const wrapper = mountPage();
    const saveBtn = wrapper.findAll('.mock-btn').find(btn => btn.text() === '保存流水线配置');

    await saveBtn?.trigger('click');

    // Flush promises
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(mockValidateService).toHaveBeenCalled();
    expect(mockValidatePrompt).toHaveBeenCalled();
    expect(mockValidateFlow).toHaveBeenCalled();

    // Critical: Verify PUT was NOT called when validation fails
    expect(defHttp.put).not.toHaveBeenCalled();

    const badges = wrapper.findAll('.mock-badge');
    expect(badges[0].attributes('data-dot')).toBe('false');
    expect(badges[1].attributes('data-dot')).toBe('true'); // Error in prompt tab
    expect(badges[2].attributes('data-dot')).toBe('false');
  });

  // 10.6 测试单 PUT 请求提交
  it('merges tab values and submits a single PUT request', async () => {
    const wrapper = mountPage();

    // Wait for initial data loading (onMounted -> getAiConfig)
    await new Promise(resolve => setTimeout(resolve, 0));

    const saveBtn = wrapper.findAll('.mock-btn').find(btn => btn.text() === '保存流水线配置');

    await saveBtn?.trigger('click');

    // Flush promises for save operation
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(defHttp.put).toHaveBeenCalledTimes(1);

    // It should include original fields + merged form fields
    const putCall = (defHttp.put as any).mock.calls[0][0];
    expect(putCall.url).toBe('/ainote/aiConfig');
    expect(putCall.params).toMatchObject({
      id: '1',
      tenantId: 1000,
      asrModelId: 'asr-1',
      maxSummaryLength: 100,
      asrFailureMode: 'retry'
    });
  });

  // 10.7 测试 legacy 字段过滤
  it('filters legacy fields on updateAiConfig API call', () => {
    updateAiConfig({
      id: '1',
      asrModelId: 'asr-2',
      summaryFlowId: 'old-flow',
      summaryFlowEnabled: 1
    } as any);

    const putCall = (defHttp.put as any).mock.calls[0][0];
    expect(putCall.params).not.toHaveProperty('summaryFlowId');
    expect(putCall.params).not.toHaveProperty('summaryFlowEnabled');
    expect(putCall.params.id).toBe('1');
    expect(putCall.params.asrModelId).toBe('asr-2');
  });
});
