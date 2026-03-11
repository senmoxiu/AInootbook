import { defineStore } from 'pinia';
import { store } from '/@/store';
import { getMaterialList, uploadMaterial, deleteMaterial, getPresignedUrl } from '/@/api/ainote/material.api';

interface MaterialItem {
  id: string;
  noteId: string;
  fileType: string;
  filePath: string;
  fileName: string;
  fileExt: string;
  fileSize: number;
  processStatus: number;
  tenantId?: number;
  createBy?: string;
  createTime?: string;
}

interface MaterialState {
  materials: MaterialItem[];
  uploading: boolean;
  loading: boolean;
  currentNoteId: string | null;
}

export const useMaterialStore = defineStore({
  id: 'ainote-material',
  state: (): MaterialState => ({
    materials: [],
    uploading: false,
    loading: false,
    currentNoteId: null,
  }),
  getters: {
    getMaterialsByNoteId:
      (state) =>
      (noteId: string): MaterialItem[] =>
        state.materials.filter((m) => m.noteId === noteId),
  },
  actions: {
    async fetchMaterials(noteId: string) {
      this.loading = true;
      this.currentNoteId = noteId;
      try {
        const res = await getMaterialList({ noteId });
        this.materials = res?.records ?? res ?? [];
      } finally {
        this.loading = false;
      }
    },
    async upload(formData: FormData) {
      this.uploading = true;
      try {
        const res = await uploadMaterial(formData);
        if (res && this.currentNoteId) {
          await this.fetchMaterials(this.currentNoteId);
        }
        return res;
      } finally {
        this.uploading = false;
      }
    },
    async remove(id: string) {
      await deleteMaterial({ id }, () => {});
      this.materials = this.materials.filter((m) => m.id !== id);
    },
    async getUrl(id: string): Promise<string> {
      const res = await getPresignedUrl(id);
      return res?.url ?? res ?? '';
    },
    clearMaterials() {
      this.materials = [];
      this.currentNoteId = null;
    },
  },
});

export function useMaterialStoreWithOut() {
  return useMaterialStore(store);
}
