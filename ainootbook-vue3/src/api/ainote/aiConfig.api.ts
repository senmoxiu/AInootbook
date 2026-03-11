import { defHttp } from '/@/utils/http/axios';

enum Api {
  config = '/ainote/aiConfig',
  refresh = '/ainote/aiConfig/refresh',
}

/** AI 配置记录 */
export interface AiConfigRecord {
  id?: string;
  tenantId?: number;
  summaryModelId?: string;
  ocrModelId?: string;
  knowledgeId?: string;
  summaryPromptKey?: string;
  maxSummaryLength?: number;
  maxKeywordsCount?: number;
  summaryFlowId?: string;
  /** 0=关闭 1=开启，整数类型 */
  summaryFlowEnabled?: number;
}

export const getAiConfig = () => {
  return defHttp.get<AiConfigRecord>({ url: Api.config });
};

export const updateAiConfig = (config: AiConfigRecord) => {
  return defHttp.put<string>({ url: Api.config, params: config });
};

export const refreshAiConfigCache = () => {
  return defHttp.post<AiConfigRecord>({ url: Api.refresh });
};
