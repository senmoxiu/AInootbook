<template>
  <BasicDrawer
    v-bind="$attrs"
    title="版本历史"
    :width="1200"
    @register="registerDrawer"
  >
    <div class="version-drawer-content">
      <div class="version-list-panel">
        <NoteVersionList
          :noteId="noteId"
          :selectedVersion="selectedVersion"
          @select="handleVersionSelect"
        />
      </div>
      <div class="version-detail-panel">
        <NoteVersionDetail
          v-if="selectedVersionRecord"
          :versionRecord="selectedVersionRecord"
          :noteId="noteId"
          :currentContent="currentContent"
          @rollback="handleRollback"
        />
        <a-empty v-else description="请选择一个版本查看详情" />
      </div>
    </div>
  </BasicDrawer>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
import { useMessage } from '/@/hooks/web/useMessage';
import NoteVersionList from './NoteVersionList.vue';
import NoteVersionDetail from './NoteVersionDetail.vue';
import type { NoteVersionRecord } from '/@/api/ainote/note.api';

const emit = defineEmits(['register', 'success']);
const { createMessage } = useMessage();

const noteId = ref<string>('');
const currentContent = ref<string>('');
const selectedVersion = ref<number | undefined>(undefined);
const selectedVersionRecord = ref<NoteVersionRecord | undefined>(undefined);

const [registerDrawer, { closeDrawer }] = useDrawerInner((data) => {
  noteId.value = data.noteId;
  currentContent.value = data.currentContent || '';
  selectedVersion.value = undefined;
  selectedVersionRecord.value = undefined;
});

function handleVersionSelect(version: number, record: NoteVersionRecord) {
  selectedVersion.value = version;
  selectedVersionRecord.value = record;
}

function handleRollback() {
  createMessage.success('版本回滚成功');
  closeDrawer();
  emit('success');
}
</script>

<style lang="less" scoped>
.version-drawer-content {
  display: flex;
  gap: 16px;
  height: 100%;
}

.version-list-panel {
  width: 40%;
  border-right: 1px solid #f0f0f0;
  padding-right: 16px;
  overflow-y: auto;
}

.version-detail-panel {
  flex: 1;
  overflow-y: auto;
}
</style>
