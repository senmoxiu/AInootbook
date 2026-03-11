import type { AppRouteRecordRaw } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

export const AI_ROUTE: AppRouteRecordRaw = {
  path: '',
  name: 'ai-parent',
  component: LAYOUT,
  meta: {
    title: 'ai',
  },
  children: [
    {
      path: '/ai',
      name: 'ai',
      component: () => import('/@/views/dashboard/ai/index.vue'),
      meta: {
        title: 'AI助手',
      },
    },
  ],
};

/**
 * AI笔记详情路由（非菜单，带动态参数，必须注册为静态路由）
 * 菜单由 sys_permission 数据库控制；此处仅注册详情页 /ainote/note/view/:id
 */
export const AINOTE_ROUTE: AppRouteRecordRaw = {
  path: '/ainote',
  name: 'AinoteStatic',
  component: LAYOUT,
  meta: {
    title: 'AI笔记',
    hideMenu: true,
  },
  children: [
    {
      path: 'note/view/:id',
      name: 'AinoteNoteView',
      component: () => import('/@/views/ainote/note/detail.vue'),
      meta: {
        title: '笔记详情',
        hideMenu: true,
        currentActiveMenu: '/ainote/note',
      },
    },
  ],
};

export const staticRoutesList = [AI_ROUTE, AINOTE_ROUTE];
