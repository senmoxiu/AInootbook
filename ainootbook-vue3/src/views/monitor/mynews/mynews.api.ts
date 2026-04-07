import { defHttp } from '/@/utils/http/axios';

// stub: mynews API 已随监控模块一同移除
export const readAllMsg = () => Promise.resolve();
export const getMyMessageList = (params?: Record<string, unknown>) =>
  defHttp.get({ url: '/sys/annountCement/listByUser', params });
