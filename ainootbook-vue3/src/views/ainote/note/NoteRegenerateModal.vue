<template>
  <BasicModal
    v-bind="$attrs"
    title="AI 重新生成"
    :width="600"
    @register="registerModal"
    @ok="handleSubmit"
    :confirmLoading="submitting"
  >
    <div class="regenerate-form">
      <a-form :model="formData" layout="vertical">
        <a-form-item label="基准版本（可选）">
          <a-select
            v-model:value="formData.baseVersion"
            placeholder="默认使用当前版本"
            allowClear
          >
            <a-select-option
              v-for="v in versionOptions"
              :key="v.version"
              :value="v.version"
            >
              v{{ v.version }} - {{ v.createTime }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="补充说明（可选）">
          <a-textarea
            v-model:value="formData.additionalContent"
            :rows="4"
            placeholder="例如：请重点关注第三章的内容，提取更多技术关键词"
          />
        </a-form-item>
      </a-form>
    </div>
  </BasicModal>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { BasicModal, useModalInner } from '/@/components/Modal';
import { useMessage } from '/@/hooks/web/useMessage';
import { regenerateNote, getNoteVersions } from '/@/api/ainote/note.api';
import type { NoteVersionRecord, RegenerateParams } from '/@/api/ainote/note.api';

const emit = defineEmits(['register', 'success']);
const { createMessage } = useMessage();

const noteId = ref<string>('');
const submitting = ref(false);
const versionOptions = ref<NoteVersionRecord[]>([]);

const formData = ref<RegenerateParams>({
  noteId: '',
  baseVersion: undefined,
  additionalContent: '',
});

const [registerModal, { closeModal }] = useModalInner(async (data) => {
  noteId.value = data.noteId;
  formData.value.noteId = data.noteId;
  formData.value.baseVersion = undefined;
  formData.value.additionalContent = '';
  await loadVersionOptions();
});

async function loadVersionOptions() {
  try {
    const res = await getNoteVersions({ noteId: noteId.value, pageNo: 1, pageSize: 10 });
    versionOptions.value = res.records;
  } catch (error) {
    console.error('加载版本列表失败', error);
  }
}

async function handleSubmit() {
  submitting.value = true;
  try {
    await regenerateNote(formData.value);
    closeModal();
    emit('success');
  } catch (error) {
    createMessage.error('触发生成失败');
    console.error(error);
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="less" scoped>
.regenerate-form {
  padding: 16px 0;
}
</style>