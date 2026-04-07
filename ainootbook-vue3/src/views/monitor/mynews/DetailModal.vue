<template>
  <BasicModal v-bind="$attrs" @register="registerModal" :footer="null" :closable="true">
    <div class="p-4 text-center text-gray-400">消息详情功能暂不可用</div>
  </BasicModal>
</template>
<script lang="ts" setup>
import { BasicModal, useModalInner } from '/@/components/Modal';
const [registerModal] = useModalInner();
</script>
