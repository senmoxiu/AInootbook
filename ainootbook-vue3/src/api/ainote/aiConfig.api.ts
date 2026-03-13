import { defHttp } from '/@/utils/http/axios';

enum Api {
  config = '/ainote/aiConfig',
  refresh = '/ainote/aiConfig/refresh',
}

/** 失败处理策略 */
export type FailureMode = 'skip' | 'retry' | 'fail_all';

/** AI 配置记录 */
export interface AiConfigRecord {
  id?: string;
  tenantId?: number;
  summaryModelId?: string;
  ocrModelId?: string;
  /** 语音识别（ASR）模型 ID */
  asrModelId?: string;
  /** 视频处理模型 ID */
  videoModelId?: string;
  /** 关键字提取模型 ID */
  keywordsModelId?: string;
  /** 内容整合模型 ID */
  integrateModelId?: string;
  knowledgeId?: string;
  summaryPromptKey?: string;
  maxSummaryLength?: number;
  maxKeywordsCount?: number;
  /** ASR 失败处理策略 */
  asrFailureMode?: FailureMode;
  /** ASR 重试上限 */
  asrRetryLimit?: number;
  /** OCR 失败处理策略 */
  ocrFailureMode?: FailureMode;
  /** OCR 重试上限 */
  ocrRetryLimit?: number;
  /** 视频处理失败策略 */
  videoFailureMode?: FailureMode;
  /** 视频重试上限 */
  videoRetryLimit?: number;
  /** 摘要处理失败策略 */
  summaryFailureMode?: FailureMode;
  /** 摘要重试上限 */
  summaryRetryLimit?: number;
  /** 整合处理失败策略 */
  integrateFailureMode?: FailureMode;
  /** 整合重试上限 */
  integrateRetryLimit?: number;
  /** @deprecated 历史保留字段：工作流 ID */
  summaryFlowId?: string;
  /** @deprecated 历史保留字段：0=关闭 1=开启 */
  summaryFlowEnabled?: number;
}

export const getAiConfig = () => {
  return defHttp.get<AiConfigRecord>({ url: Api.config });
};

export const updateAiConfig = (config: AiConfigRecord) => {
  // 过滤废弃的旧有字段
  const { summaryFlowId, summaryFlowEnabled, ...payload } = config;
  return defHttp.put<string>({ url: Api.config, params: payload });
};

export const refreshAiConfigCache = () => {
  return defHttp.post<AiConfigRecord>({ url: Api.refresh });
};
