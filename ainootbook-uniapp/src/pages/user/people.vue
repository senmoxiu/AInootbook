<!-- @ts-nocheck -->
<route lang="json5" type="page">
{
  layout: 'default',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '个人',
    disableScroll: true,
    'app-plus': {
      bounce: 'none',
    },
  },
}
</route>

<template>
  <PageLayout :navbarShow="false">
    <view class="avatar-area">
      <!-- prettier-ignore -->
      <wd-img width="100" height="100" :round="true" :radius="50" :src="avatarUrl" @click="ChooseImage"></wd-img>
      <view class="name-row">
        <text class="realname">{{ personalList.realname || userStore.userInfo.realname }}</text>
      </view>
      <view class="role-tag">
        <text>{{ roleLabel }}</text>
      </view>
    </view>
    <scroll-view scroll-y>
      <wd-cell-group custom-class="shadow-warp" border clickable>
        <wd-cell title="个人资料" is-link @click="goUserDetail">
          <template #icon>
            <view class="cuIcon-people text-blue mr-2"></view>
          </template>
        </wd-cell>
        <wd-cell title="退出登录" @click="exit">
          <template #icon>
            <view class="cuIcon-exit text-yellow mr-2"></view>
          </template>
        </wd-cell>
      </wd-cell-group>
    </scroll-view>
  </PageLayout>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { getFileAccessHttpUrl } from '@/common/uitls'
import { onLoad } from '@dcloudio/uni-app'
import { useToast, useMessage } from 'wot-design-uni'
import { useRouter } from '@/plugin/uni-mini-router'
import { http } from '@/utils/http'
import { useUserStore } from '@/store/user'
import useUpload from '@/hooks/useUpload'
import { getEnvBaseUrl } from '@/utils/index'

defineOptions({
  name: 'people',
  options: {
    styleIsolation: 'shared',
  },
})

const userStore = useUserStore()
const toast = useToast()
const router = useRouter()
const message = useMessage()
const defAvatar = '/static/logo.png'

const personalList = reactive({
  avatar: '',
  realname: '',
  username: '',
  roles: [] as string[],
})

const userId = ref(userStore.userInfo.userid)
let stopWatch: any = null

const api = {
  userUrl: '/sys/user/queryById',
  uploadUrl: `${getEnvBaseUrl()}/sys/common/upload`,
}

const avatarUrl = computed(() => {
  return personalList.avatar || getFileAccessHttpUrl(userStore.userInfo.avatar) || defAvatar
})

const roleLabel = computed(() => {
  const roles = personalList.roles
  if (roles.includes('teacher')) return '教师'
  if (roles.includes('student')) return '学生'
  if (roles.includes('admin')) return '管理员'
  return '用户'
})

const load = () => {
  if (!userId.value) return
  http
    .get(api.userUrl, { id: userId.value })
    .then((res: any) => {
      if (res.success) {
        const perArr = res.result
        personalList.avatar = perArr.avatar ? getFileAccessHttpUrl(perArr.avatar) : ''
        personalList.realname = perArr.realname
        personalList.username = perArr.username
        personalList.roles = (perArr.roles || []).map((r: any) => r.roleCode ?? r)
      }
    })
    .catch(() => {})
}

const ChooseImage = () => {
  const { loading, data, error, run } = useUpload({ name: 'file' }, { url: api.uploadUrl })
  if (stopWatch) stopWatch()
  run()
  stopWatch = watch(
    () => [loading.value, error.value, data.value],
    ([loading, err, data]) => {
      if (!loading) {
        if (!err && data) {
          editAvatar((data as any).message)
        } else if (err) {
          uni.hideLoading()
        }
      }
    },
  )
}

const editAvatar = (avatar: string) => {
  http
    .put('/sys/user/appEdit', { id: userId.value, avatar })
    .then((res: any) => {
      if (res.success) {
        toast.success('修改成功~')
        userStore.editUserInfo({ avatar: getFileAccessHttpUrl(avatar) })
        personalList.avatar = getFileAccessHttpUrl(avatar)
      } else {
        toast.warning(res.message)
      }
      uni.hideLoading()
    })
    .catch(() => {
      uni.hideLoading()
      toast.warning('修改失败')
    })
}

const goUserDetail = () => {
  router.push({ name: 'user-userDetail-userDetail' })
}

const exit = () => {
  message
    .confirm({ title: '提示', msg: '确定退出吗？' })
    .then(() => {
      userStore.clearUserInfo()
      router.replaceAll({ name: 'login' })
    })
}

onBeforeUnmount(() => {
  stopWatch?.()
})

onLoad(() => {
  load()
})
</script>

<style lang="scss" scoped>
.avatar-area {
  /* #ifdef MP-WEIXIN */
  background-image: url('https://static.jeecg.com/upload/test/blue_1595818030310.png');
  /* #endif */
  /* #ifndef MP-WEIXIN */
  background-image: url('@/static/blue.png');
  /* #endif */
  background-size: cover;
  height: 400upx;
  display: flex;
  justify-content: center;
  padding-top: 40upx;
  overflow: hidden;
  position: relative;
  flex-direction: column;
  align-items: center;
  color: #fff;
  font-weight: 300;
  text-shadow: 0 0 3px rgba(0, 0, 0, 0.3);

  .name-row {
    margin-top: 16upx;
    .realname {
      font-size: 34upx;
      font-weight: 600;
      color: #fff;
    }
  }

  .role-tag {
    margin-top: 8upx;
    background: rgba(255, 255, 255, 0.25);
    border-radius: 20upx;
    padding: 4upx 20upx;
    font-size: 24upx;
    color: rgba(255, 255, 255, 0.9);
  }
}

:deep(.wd-cell-group) {
  margin: 24upx 26upx;
  border-radius: 18upx;
  overflow: hidden;
  --wot-cell-line-height: 32px;
  .wd-cell {
    --wot-cell-title-fs: 15px;
    --wot-cell-title-color: var(--color-grey);
    .wd-cell__left {
      font-size: 15px;
    }
  }
}
</style>
